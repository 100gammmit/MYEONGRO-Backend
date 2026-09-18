-- Retire reading soft deletion. Existing hidden rows are permanently removed
-- before the marker column is dropped so they cannot become visible again.
delete from public.readings where deleted_at is not null;

alter table public.readings
  drop column deleted_at;

create index readings_tarot_spread_created_idx
  on public.readings (spread_type, created_at desc)
  where kind = 'tarot';

create unique index readings_one_generating_per_user_uq
  on public.readings (user_id)
  where status = 'generating';

create or replace function public.get_reading_credit_status(
  requested_user_id uuid,
  requested_daily_grant integer
)
returns table (
  free_balance integer,
  paid_balance integer,
  generation_in_progress boolean
)
language plpgsql
security definer
set search_path = pg_catalog
as $$
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

create or replace function public.create_pending_reading(
  requested_user_id uuid,
  requested_request_id uuid,
  requested_input_hash text,
  requested_kind public.reading_kind,
  requested_spread_type text,
  requested_schema_version integer,
  requested_input_payload jsonb,
  requested_provider text,
  requested_model text,
  requested_prompt_version text,
  requested_credit_cost integer,
  requested_daily_grant integer
)
returns table (
  reading_id uuid,
  generation_id bigint,
  created boolean
)
language plpgsql
security definer
set search_path = pg_catalog
as $$
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
    reading_id, provider, model, prompt_version, idempotency_key, status
  ) values (
    inserted_reading.id, requested_provider, requested_model,
    requested_prompt_version, 'reading:' || inserted_reading.id::text, 'pending'
  ) returning generation_records.id into inserted_generation_id;

  return query select inserted_reading.id, inserted_generation_id, true;
end;
$$;

create or replace function public.complete_reading_generation(
  requested_reading_id uuid,
  requested_generation_id bigint,
  requested_title text,
  requested_result jsonb,
  requested_daily_grant integer
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog
as $$
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

create or replace function public.fail_reading_generation(
  requested_reading_id uuid,
  requested_generation_id bigint,
  requested_error_code text
)
returns void
language plpgsql
security definer
set search_path = pg_catalog
as $$
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

create or replace function public.fail_stale_reading_generations(
  requested_stale_after interval
)
returns integer
language plpgsql
security definer
set search_path = pg_catalog
as $$
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
