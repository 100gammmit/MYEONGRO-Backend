-- MYEONGRO production schema baseline.
--
-- This service had no production database when the baseline was consolidated.
-- The final state of the pre-release V1-V19 chain was replayed on PostgreSQL 16
-- and reduced to this single clean migration. Once this migration has been
-- applied to production, never edit it; add V2 and later migrations instead.

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: generation_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.generation_status AS ENUM (
    'pending',
    'completed',
    'failed'
);


--
-- Name: reading_kind; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.reading_kind AS ENUM (
    'tarot',
    'saju'
);


--
-- Name: reading_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.reading_status AS ENUM (
    'draft',
    'generating',
    'completed',
    'failed'
);


--
-- Name: apply_daily_reading_credit_reset(uuid, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.apply_daily_reading_credit_reset(requested_user_id uuid, requested_daily_grant integer) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog'
    AS $$
declare
  today_in_korea date := (current_timestamp at time zone 'Asia/Seoul')::date;
  changed_rows integer;
begin
  if requested_user_id is null then
    raise exception 'Authenticated user id is required';
  end if;
  if requested_daily_grant is null or requested_daily_grant < 0 then
    raise exception 'Daily credit grant must be nonnegative';
  end if;

  perform pg_advisory_xact_lock(
    hashtextextended('reading-credit:' || requested_user_id::text, 0)
  );

  update public.profiles
  set free_credit_balance = requested_daily_grant,
    free_credit_reset_date = today_in_korea
  where profiles.id = requested_user_id
    and (
      profiles.free_credit_reset_date is null
      or profiles.free_credit_reset_date < today_in_korea
    );

  select count(*) into changed_rows
  from public.profiles
  where profiles.id = requested_user_id;
  if changed_rows <> 1 then
    raise exception 'PROFILE_NOT_FOUND';
  end if;
end;
$$;


--
-- Name: complete_reading_generation(uuid, bigint, text, jsonb, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.complete_reading_generation(requested_reading_id uuid, requested_generation_id bigint, requested_title text, requested_result jsonb, requested_daily_grant integer) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog'
    AS $$
declare
  owned_reading public.readings%rowtype;
  reading_user_id uuid;
  free_balance integer;
  paid_balance integer;
  free_debit integer;
  paid_debit integer;
  balance_mismatch boolean := false;
  changed_rows integer;
begin
  select readings.user_id into reading_user_id
  from public.readings
  where readings.id = requested_reading_id;
  if not found then
    raise exception 'READING_STATE_CONFLICT' using errcode = 'RL108';
  end if;

  perform pg_advisory_xact_lock(
    hashtextextended('reading-credit:' || reading_user_id::text, 0)
  );

  select * into owned_reading
  from public.readings
  where readings.id = requested_reading_id
  for update;
  if not found or owned_reading.status <> 'generating' then
    raise exception 'READING_STATE_CONFLICT' using errcode = 'RL108';
  end if;

  perform public.apply_daily_reading_credit_reset(
    owned_reading.user_id,
    requested_daily_grant
  );

  select profiles.free_credit_balance, profiles.paid_credit_balance
  into free_balance, paid_balance
  from public.profiles
  where profiles.id = owned_reading.user_id
  for update;

  free_debit := least(free_balance, owned_reading.credit_cost);
  paid_debit := least(
    paid_balance,
    greatest(owned_reading.credit_cost - free_debit, 0)
  );
  balance_mismatch := free_debit + paid_debit < owned_reading.credit_cost;

  update public.generation_records
  set status = 'completed', error_code = null
  where generation_records.id = requested_generation_id
    and generation_records.reading_id = requested_reading_id
    and generation_records.status = 'pending';
  get diagnostics changed_rows = row_count;
  if changed_rows <> 1 then
    raise exception 'GENERATION_STATE_CONFLICT' using errcode = 'RL107';
  end if;

  update public.profiles
  set free_credit_balance = greatest(free_credit_balance - free_debit, 0),
    paid_credit_balance = greatest(paid_credit_balance - paid_debit, 0)
  where profiles.id = owned_reading.user_id;

  update public.readings
  set status = 'completed', title = requested_title, result_payload = requested_result
  where readings.id = requested_reading_id;

  return balance_mismatch;
end;
$$;


--
-- Name: create_pending_reading(uuid, uuid, text, public.reading_kind, text, integer, jsonb, text, text, text, integer, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.create_pending_reading(requested_user_id uuid, requested_request_id uuid, requested_input_hash text, requested_kind public.reading_kind, requested_spread_type text, requested_schema_version integer, requested_input_payload jsonb, requested_provider text, requested_model text, requested_prompt_version text, requested_credit_cost integer, requested_daily_grant integer) RETURNS TABLE(reading_id uuid, generation_id bigint, created boolean)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog'
    AS $$
declare
  existing_reading public.readings%rowtype;
  inserted_reading public.readings%rowtype;
  existing_generation_id bigint;
  inserted_generation_id bigint;
  available_credits integer;
begin
  if requested_user_id is null or requested_request_id is null then
    raise exception 'User id and request id are required';
  end if;
  if length(btrim(coalesce(requested_input_hash, ''))) = 0 then
    raise exception 'Input hash is required';
  end if;
  if requested_schema_version is null or requested_schema_version <= 0 then
    raise exception 'Positive payload schema version is required';
  end if;
  if requested_credit_cost is null or requested_credit_cost <= 0 then
    raise exception 'Positive credit cost is required';
  end if;
  if requested_kind = 'tarot' and (
    requested_spread_type is null
    or requested_spread_type not in (
      'mind_three_card',
      'relationship_three_card', 'choice_five_card'
    )
  ) then
    raise exception 'Unsupported tarot spread type';
  end if;
  if requested_kind = 'saju' and requested_spread_type is not null then
    raise exception 'Saju reading must not have a spread type';
  end if;

  perform pg_advisory_xact_lock(
    hashtextextended('reading-credit:' || requested_user_id::text, 0)
  );

  select * into existing_reading
  from public.readings
  where readings.user_id = requested_user_id
    and readings.request_id = requested_request_id
  limit 1
  for update;

  if found then
    if existing_reading.input_hash <> requested_input_hash then
      raise exception 'IDEMPOTENCY_CONFLICT' using errcode = 'RL104';
    end if;
    if existing_reading.status <> 'completed' then
      raise exception 'READING_GENERATION_IN_PROGRESS_OR_RETRY_REQUIRED'
        using errcode = 'RL110';
    end if;

    select generation_records.id into existing_generation_id
    from public.generation_records
    where generation_records.reading_id = existing_reading.id
    order by generation_records.created_at desc
    limit 1;
    if existing_generation_id is null then
      raise exception 'GENERATION_RECORD_MISSING' using errcode = 'RL106';
    end if;
    return query select existing_reading.id, existing_generation_id, false;
    return;
  end if;

  perform public.apply_daily_reading_credit_reset(
    requested_user_id,
    requested_daily_grant
  );

  if exists (
    select 1 from public.readings
    where readings.user_id = requested_user_id
      and readings.status = 'generating'
  ) then
    raise exception 'READING_GENERATION_IN_PROGRESS' using errcode = 'RL111';
  end if;

  select profiles.free_credit_balance + profiles.paid_credit_balance
  into available_credits
  from public.profiles
  where profiles.id = requested_user_id
  for update;
  if available_credits < requested_credit_cost then
    raise exception 'INSUFFICIENT_READING_CREDITS' using errcode = 'RL112';
  end if;

  insert into public.readings (
    user_id, request_id, input_hash, kind, spread_type, schema_version,
    status, title, input_payload, credit_cost
  ) values (
    requested_user_id, requested_request_id, requested_input_hash,
    requested_kind, requested_spread_type, requested_schema_version,
    'generating', 'Generating...', requested_input_payload, requested_credit_cost
  ) returning * into inserted_reading;

  insert into public.generation_records (
    reading_id, provider, model, prompt_version, status
  ) values (
    inserted_reading.id, requested_provider, requested_model,
    requested_prompt_version, 'pending'
  ) returning generation_records.id into inserted_generation_id;

  return query select inserted_reading.id, inserted_generation_id, true;
end;
$$;


--
-- Name: fail_reading_generation(uuid, bigint, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fail_reading_generation(requested_reading_id uuid, requested_generation_id bigint, requested_error_code text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog'
    AS $$
declare
  reading_user_id uuid;
  changed_rows integer;
begin
  select readings.user_id into reading_user_id
  from public.readings
  where readings.id = requested_reading_id;
  if not found then
    raise exception 'READING_STATE_CONFLICT' using errcode = 'RL108';
  end if;

  perform pg_advisory_xact_lock(
    hashtextextended('reading-credit:' || reading_user_id::text, 0)
  );

  perform 1
  from public.readings
  where readings.id = requested_reading_id
    and readings.status = 'generating'
  for update;
  if not found then
    raise exception 'READING_STATE_CONFLICT' using errcode = 'RL108';
  end if;

  update public.generation_records
  set status = 'failed', error_code = requested_error_code
  where generation_records.id = requested_generation_id
    and generation_records.reading_id = requested_reading_id
    and generation_records.status = 'pending';
  get diagnostics changed_rows = row_count;
  if changed_rows <> 1 then
    raise exception 'GENERATION_STATE_CONFLICT' using errcode = 'RL107';
  end if;

  update public.readings
  set status = 'failed'
  where readings.id = requested_reading_id;
end;
$$;


--
-- Name: fail_stale_reading_generations(interval); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fail_stale_reading_generations(requested_stale_after interval) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog'
    AS $$
declare
  candidate record;
  locked_reading_status text;
  failed_count integer := 0;
begin
  if requested_stale_after is null or requested_stale_after <= interval '0 seconds' then
    raise exception 'Positive stale duration is required';
  end if;

  for candidate in
    select gr.id as generation_id, gr.reading_id, r.user_id
    from public.generation_records gr
    join public.readings r on r.id = gr.reading_id
    where gr.status = 'pending'
      and r.status = 'generating'
      and gr.created_at < current_timestamp - requested_stale_after
    order by gr.created_at
  loop
    if not pg_try_advisory_xact_lock(
      hashtextextended('reading-credit:' || candidate.user_id::text, 0)
    ) then
      continue;
    end if;

    select readings.status::text into locked_reading_status
    from public.readings
    where readings.id = candidate.reading_id
      and readings.status = 'generating'
    for update;
    if not found then
      continue;
    end if;

    update public.generation_records
    set status = 'failed', error_code = 'GENERATION_TIMEOUT'
    where generation_records.id = candidate.generation_id
      and generation_records.status = 'pending';
    if found then
      update public.readings
      set status = 'failed'
      where readings.id = candidate.reading_id
        and readings.status = 'generating';
      failed_count := failed_count + 1;
    end if;
  end loop;
  return failed_count;
end;
$$;


--
-- Name: get_reading_credit_status(uuid, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_reading_credit_status(requested_user_id uuid, requested_daily_grant integer) RETURNS TABLE(free_balance integer, paid_balance integer, generation_in_progress boolean)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog'
    AS $$
begin
  perform public.apply_daily_reading_credit_reset(
    requested_user_id,
    requested_daily_grant
  );

  return query
  select
    profiles.free_credit_balance,
    profiles.paid_credit_balance,
    exists (
      select 1
      from public.readings
      where readings.user_id = requested_user_id
        and readings.status = 'generating'
    )
  from public.profiles
  where profiles.id = requested_user_id;
end;
$$;


--
-- Name: is_valid_saju_v5_input(jsonb); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.is_valid_saju_v5_input(payload jsonb) RETURNS boolean
    LANGUAGE plpgsql IMMUTABLE STRICT
    SET search_path TO 'pg_catalog'
    AS $_$
declare
  snapshot jsonb;
  pillars jsonb;
  pillar_name text;
  pillar jsonb;
  relation jsonb;
  item jsonb;
  current_cycle jsonb;
  annual jsonb;
  uncertainty jsonb;
begin
  if jsonb_typeof(payload) <> 'object'
    or not (payload ?& array['targetYear', 'calculationSnapshot'])
    or payload - array['targetYear', 'calculationSnapshot'] <> '{}'::jsonb
    or jsonb_typeof(payload -> 'targetYear') <> 'number'
    or (payload ->> 'targetYear') !~ '^[0-9]{4}$' then
    return false;
  end if;

  snapshot := payload -> 'calculationSnapshot';
  if jsonb_typeof(snapshot) <> 'object'
    or not (snapshot ?& array[
      'calculationVersion', 'engine', 'engineVersion', 'cityCatalogVersion',
      'targetYear', 'pillars', 'fiveElements', 'relations',
      'limitations', 'uncertainty'
    ])
    or snapshot - array[
      'calculationVersion', 'engine', 'engineVersion', 'cityCatalogVersion',
      'targetYear', 'pillars', 'dayMaster', 'fiveElements', 'relations',
      'currentLuckCycle', 'annualFortune', 'limitations', 'uncertainty'
    ] <> '{}'::jsonb
    or jsonb_typeof(snapshot -> 'calculationVersion') <> 'string'
    or jsonb_typeof(snapshot -> 'engine') <> 'string'
    or jsonb_typeof(snapshot -> 'engineVersion') <> 'string'
    or jsonb_typeof(snapshot -> 'cityCatalogVersion') <> 'string'
    or jsonb_typeof(snapshot -> 'targetYear') <> 'number'
    or snapshot ->> 'targetYear' <> payload ->> 'targetYear'
    or (snapshot ? 'dayMaster' and jsonb_typeof(snapshot -> 'dayMaster') <> 'string')
    or jsonb_typeof(snapshot -> 'fiveElements') <> 'object'
    or (snapshot -> 'fiveElements') - array['wood', 'fire', 'earth', 'metal', 'water'] <> '{}'::jsonb
    or jsonb_typeof(snapshot -> 'relations') <> 'array'
    or jsonb_typeof(snapshot -> 'limitations') <> 'array' then
    return false;
  end if;

  pillars := snapshot -> 'pillars';
  if jsonb_typeof(pillars) <> 'object'
    or not (pillars ?& array['year', 'month', 'day', 'time'])
    or pillars - array['year', 'month', 'day', 'time'] <> '{}'::jsonb then
    return false;
  end if;

  foreach pillar_name in array array['year', 'month', 'day', 'time'] loop
    pillar := pillars -> pillar_name;
    if pillar <> 'null'::jsonb and (jsonb_typeof(pillar) <> 'object'
      or not (pillar ? 'ganZhi')
      or pillar - array['ganZhi', 'stemTenGod'] <> '{}'::jsonb
      or jsonb_typeof(pillar -> 'ganZhi') <> 'string'
      or (pillar ? 'stemTenGod' and jsonb_typeof(pillar -> 'stemTenGod') <> 'string')) then
      return false;
    end if;
  end loop;

  for item in select value from jsonb_each(snapshot -> 'fiveElements') loop
    if jsonb_typeof(item) <> 'number' then
      return false;
    end if;
  end loop;

  for relation in select value from jsonb_array_elements(snapshot -> 'relations') loop
    if jsonb_typeof(relation) <> 'object'
      or not (relation ?& array['type', 'members'])
      or relation - array['type', 'members'] <> '{}'::jsonb
      or jsonb_typeof(relation -> 'type') <> 'string'
      or jsonb_typeof(relation -> 'members') <> 'array' then
      return false;
    end if;
    for item in select value from jsonb_array_elements(relation -> 'members') loop
      if jsonb_typeof(item) <> 'string' then
        return false;
      end if;
    end loop;
  end loop;

  if snapshot ? 'currentLuckCycle' then
    current_cycle := snapshot -> 'currentLuckCycle';
    if current_cycle <> 'null'::jsonb and (
      jsonb_typeof(current_cycle) <> 'object'
      or not (current_cycle ?& array['startYear', 'endYear', 'ganZhi'])
      or current_cycle - array['startYear', 'endYear', 'ganZhi'] <> '{}'::jsonb
      or jsonb_typeof(current_cycle -> 'startYear') <> 'number'
      or jsonb_typeof(current_cycle -> 'endYear') <> 'number'
      or jsonb_typeof(current_cycle -> 'ganZhi') <> 'string'
    ) then
      return false;
    end if;
  end if;

  annual := snapshot -> 'annualFortune';
  if annual <> 'null'::jsonb and (
    jsonb_typeof(annual) <> 'object'
    or not (annual ?& array['year', 'ganZhi'])
    or annual - array['year', 'ganZhi', 'stemTenGod'] <> '{}'::jsonb
    or jsonb_typeof(annual -> 'year') <> 'number'
    or jsonb_typeof(annual -> 'ganZhi') <> 'string'
    or (annual ? 'stemTenGod' and jsonb_typeof(annual -> 'stemTenGod') <> 'string')
  ) then
    return false;
  end if;

  if jsonb_path_exists(
    snapshot -> 'limitations',
    '$[*] ? (@ == "BIRTH_TIME_UNKNOWN" || @ == "APPROXIMATE_BIRTH_TIME")'
  ) then
    return false;
  end if;
  for item in select value from jsonb_array_elements(snapshot -> 'limitations') loop
    if jsonb_typeof(item) <> 'string' then
      return false;
    end if;
  end loop;

  uncertainty := snapshot -> 'uncertainty';
  if jsonb_typeof(uncertainty) <> 'object'
    or not (uncertainty ? 'varyingFields')
    or uncertainty - 'varyingFields' <> '{}'::jsonb
    or jsonb_typeof(uncertainty -> 'varyingFields') <> 'array' then
    return false;
  end if;
  for item in select value from jsonb_array_elements(uncertainty -> 'varyingFields') loop
    if jsonb_typeof(item) <> 'string' then
      return false;
    end if;
  end loop;

  return true;
exception
  when others then
    return false;
end;
$_$;


--
-- Name: set_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
  new.updated_at = now();
  return new;
end;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: adult_eligibility_assertions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.adult_eligibility_assertions (
    id bigint NOT NULL,
    user_id uuid NOT NULL,
    policy_version text NOT NULL,
    confirmed_at timestamp with time zone NOT NULL,
    method character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    signup_generation_id character varying(64),
    CONSTRAINT adult_eligibility_assertions_method_check CHECK ((length(TRIM(BOTH FROM method)) > 0)),
    CONSTRAINT adult_eligibility_assertions_policy_version_check CHECK ((length(TRIM(BOTH FROM policy_version)) > 0))
);


--
-- Name: adult_eligibility_assertions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.adult_eligibility_assertions ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.adult_eligibility_assertions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: consent_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.consent_events (
    id bigint NOT NULL,
    user_id uuid NOT NULL,
    document_type character varying(64) NOT NULL,
    document_version text NOT NULL,
    action character varying(16) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    method character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT consent_events_action_check CHECK (((action)::text = ANY ((ARRAY['ACCEPTED'::character varying, 'WITHDRAWN'::character varying])::text[]))),
    CONSTRAINT consent_events_document_type_check CHECK (((document_type)::text = ANY ((ARRAY['TERMS'::character varying, 'AI_OVERSEAS_TRANSFER'::character varying])::text[]))),
    CONSTRAINT consent_events_document_version_check CHECK ((length(TRIM(BOTH FROM document_version)) > 0)),
    CONSTRAINT consent_events_method_check CHECK ((length(TRIM(BOTH FROM method)) > 0))
);


--
-- Name: consent_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.consent_events ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.consent_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: generation_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.generation_records (
    id bigint NOT NULL,
    reading_id uuid NOT NULL,
    provider text NOT NULL,
    model text NOT NULL,
    prompt_version text NOT NULL,
    status public.generation_status DEFAULT 'pending'::public.generation_status NOT NULL,
    error_code text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: generation_records_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.generation_records ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.generation_records_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: oauth_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.oauth_accounts (
    id bigint NOT NULL,
    profile_id uuid NOT NULL,
    provider text NOT NULL,
    provider_user_id text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT oauth_accounts_provider_check CHECK ((length(btrim(provider)) > 0)),
    CONSTRAINT oauth_accounts_provider_user_id_check CHECK ((length(btrim(provider_user_id)) > 0))
);


--
-- Name: oauth_accounts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.oauth_accounts ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.oauth_accounts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.profiles (
    id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    free_credit_balance integer DEFAULT 0 NOT NULL,
    paid_credit_balance integer DEFAULT 0 NOT NULL,
    free_credit_reset_date date,
    CONSTRAINT profiles_free_credit_balance_nonnegative CHECK ((free_credit_balance >= 0)),
    CONSTRAINT profiles_paid_credit_balance_nonnegative CHECK ((paid_credit_balance >= 0))
);


--
-- Name: readings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.readings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    kind public.reading_kind NOT NULL,
    status public.reading_status DEFAULT 'draft'::public.reading_status NOT NULL,
    title text NOT NULL,
    input_payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    result_payload jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    request_id uuid DEFAULT gen_random_uuid() NOT NULL,
    input_hash text NOT NULL,
    spread_type text,
    schema_version integer DEFAULT 1 NOT NULL,
    credit_cost integer DEFAULT 0 NOT NULL,
    CONSTRAINT readings_credit_cost_nonnegative CHECK ((credit_cost >= 0)),
    CONSTRAINT readings_input_hash_nonempty CHECK ((length(btrim(input_hash)) > 0)),
    CONSTRAINT readings_no_persisted_prompt_text_check CHECK (((NOT (input_payload ? 'question'::text)) AND (NOT (input_payload ? 'choiceOptions'::text)))),
    CONSTRAINT readings_payload_schema_check CHECK ((((schema_version = 0) AND (spread_type IS NULL)) OR ((schema_version > 0) AND (((kind = 'tarot'::public.reading_kind) AND (spread_type = ANY (ARRAY['mind_three_card'::text, 'relationship_three_card'::text, 'choice_five_card'::text]))) OR ((kind = 'saju'::public.reading_kind) AND (spread_type IS NULL)))))),
    CONSTRAINT readings_saju_v5_input_check CHECK (((kind <> 'saju'::public.reading_kind) OR ((schema_version = 5) AND public.is_valid_saju_v5_input(input_payload))))
);


--
-- Name: adult_eligibility_assertions adult_eligibility_assertions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adult_eligibility_assertions
    ADD CONSTRAINT adult_eligibility_assertions_pkey PRIMARY KEY (id);


--
-- Name: adult_eligibility_assertions adult_eligibility_assertions_user_id_policy_version_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adult_eligibility_assertions
    ADD CONSTRAINT adult_eligibility_assertions_user_id_policy_version_key UNIQUE (user_id, policy_version);


--
-- Name: consent_events consent_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.consent_events
    ADD CONSTRAINT consent_events_pkey PRIMARY KEY (id);


--
-- Name: generation_records generation_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generation_records
    ADD CONSTRAINT generation_records_pkey PRIMARY KEY (id);


--
-- Name: oauth_accounts oauth_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth_accounts
    ADD CONSTRAINT oauth_accounts_pkey PRIMARY KEY (id);


--
-- Name: oauth_accounts oauth_accounts_provider_provider_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth_accounts
    ADD CONSTRAINT oauth_accounts_provider_provider_user_id_key UNIQUE (provider, provider_user_id);


--
-- Name: profiles profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT profiles_pkey PRIMARY KEY (id);


--
-- Name: readings readings_id_user_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.readings
    ADD CONSTRAINT readings_id_user_key UNIQUE (id, user_id);


--
-- Name: readings readings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.readings
    ADD CONSTRAINT readings_pkey PRIMARY KEY (id);


--
-- Name: adult_eligibility_assertions_signup_generation_unique; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX adult_eligibility_assertions_signup_generation_unique ON public.adult_eligibility_assertions USING btree (signup_generation_id) WHERE (signup_generation_id IS NOT NULL);


--
-- Name: adult_eligibility_assertions_user_confirmed_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX adult_eligibility_assertions_user_confirmed_idx ON public.adult_eligibility_assertions USING btree (user_id, confirmed_at DESC, id DESC);


--
-- Name: consent_events_user_document_latest_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX consent_events_user_document_latest_idx ON public.consent_events USING btree (user_id, document_type, occurred_at DESC, id DESC);


--
-- Name: oauth_accounts_profile_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX oauth_accounts_profile_id_idx ON public.oauth_accounts USING btree (profile_id);


--
-- Name: readings_one_generating_per_user_uq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX readings_one_generating_per_user_uq ON public.readings USING btree (user_id) WHERE (status = 'generating'::public.reading_status);


--
-- Name: readings_tarot_spread_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX readings_tarot_spread_created_idx ON public.readings USING btree (spread_type, created_at DESC) WHERE (kind = 'tarot'::public.reading_kind);


--
-- Name: readings_user_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX readings_user_created_idx ON public.readings USING btree (user_id, created_at DESC);


--
-- Name: readings_user_request_id_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX readings_user_request_id_key ON public.readings USING btree (user_id, request_id) WHERE (user_id IS NOT NULL);


--
-- Name: generation_records generation_records_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER generation_records_set_updated_at BEFORE UPDATE ON public.generation_records FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: profiles profiles_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER profiles_set_updated_at BEFORE UPDATE ON public.profiles FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: readings readings_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER readings_set_updated_at BEFORE UPDATE ON public.readings FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: adult_eligibility_assertions adult_eligibility_assertions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adult_eligibility_assertions
    ADD CONSTRAINT adult_eligibility_assertions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: consent_events consent_events_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.consent_events
    ADD CONSTRAINT consent_events_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: generation_records generation_records_reading_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.generation_records
    ADD CONSTRAINT generation_records_reading_id_fkey FOREIGN KEY (reading_id) REFERENCES public.readings(id) ON DELETE CASCADE;


--
-- Name: oauth_accounts oauth_accounts_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth_accounts
    ADD CONSTRAINT oauth_accounts_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: readings readings_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.readings
    ADD CONSTRAINT readings_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


-- Security-definer functions mutate credit and generation state. PostgreSQL
-- grants function execution to PUBLIC by default, so keep these callable only
-- by their owner (the application/Flyway database role).
REVOKE ALL ON FUNCTION public.apply_daily_reading_credit_reset(uuid, integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.complete_reading_generation(uuid, bigint, text, jsonb, integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_pending_reading(uuid, uuid, text, public.reading_kind, text, integer, jsonb, text, text, text, integer, integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.fail_reading_generation(uuid, bigint, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.fail_stale_reading_generations(interval) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.get_reading_credit_status(uuid, integer) FROM PUBLIC;


--
-- PostgreSQL database dump complete
--
