-- Stop retaining OAuth profile data immediately, while keeping the nullable
-- legacy columns for one deployment rollback window. The application no longer
-- reads or writes them, and a later contract migration can remove them after
-- the pre-V19 image is no longer a rollback target.
update public.oauth_accounts
set email = null,
    display_name = null
where email is not null
   or display_name is not null;

alter table public.profiles
  alter column display_name set default '';

update public.profiles
set display_name = ''
where display_name is distinct from '';

-- Generation idempotency is enforced by readings(user_id, request_id) and
-- readings.input_hash. The removed columns were never read or populated.
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
    reading_id, provider, model, prompt_version, status
  ) values (
    inserted_reading.id, requested_provider, requested_model,
    requested_prompt_version, 'pending'
  ) returning generation_records.id into inserted_generation_id;

  return query select inserted_reading.id, inserted_generation_id, true;
end;
$$;

alter table public.generation_records
  drop column idempotency_key,
  drop column input_tokens,
  drop column output_tokens;
