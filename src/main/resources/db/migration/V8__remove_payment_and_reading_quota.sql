-- The current product has one authenticated reading model. Payment, paid
-- tiers, purchase retention, and daily reading quotas are retired.

drop function if exists public.claim_payment_confirmation(
  text, text, uuid, uuid, integer
);
drop function if exists public.complete_payment_confirmation(
  text, text, timestamptz
);
drop function if exists public.release_payment_confirmation(text, text);
drop function if exists public.record_payment_webhook(
  text, text, text, text, jsonb
);

drop function if exists public.reserve_free_reading_quota(
  uuid, text, uuid, uuid
);
drop function if exists public.create_pending_free_reading(
  uuid, text, uuid, text, public.reading_kind, text, integer, jsonb, text, text, text
);
drop function if exists public.complete_free_reading_generation(
  uuid, bigint, text, jsonb
);
drop function if exists public.fail_free_reading_generation(
  uuid, bigint, text
);

drop table public.payment_webhook_events;
drop table public.purchases;
drop table public.free_reading_quota_events;

alter table public.readings drop column tier;
drop type public.purchase_status;
drop type public.reading_tier;

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
  requested_prompt_version text
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
begin
  if requested_user_id is null then
    raise exception 'Authenticated user id is required';
  end if;

  if requested_request_id is null then
    raise exception 'Request id is required';
  end if;

  if length(btrim(coalesce(requested_input_hash, ''))) = 0 then
    raise exception 'Input hash is required';
  end if;

  if requested_schema_version is null or requested_schema_version <= 0 then
    raise exception 'Positive payload schema version is required';
  end if;

  if requested_kind = 'tarot' and (
    requested_spread_type is null
    or requested_spread_type not in (
      'daily_one_card',
      'mind_three_card',
      'relationship_three_card',
      'choice_five_card'
    )
  ) then
    raise exception 'Unsupported tarot spread type';
  end if;

  if requested_kind = 'saju' and requested_spread_type is not null then
    raise exception 'Saju reading must not have a spread type';
  end if;

  perform pg_advisory_xact_lock(
    hashtextextended(
      'reading:' || requested_user_id::text || ':' || requested_request_id::text,
      0
    )
  );

  select * into existing_reading
  from public.readings
  where readings.user_id = requested_user_id
    and readings.request_id = requested_request_id
  limit 1
  for update;

  if found then
    if existing_reading.deleted_at is not null then
      raise exception 'READING_DELETED'
        using errcode = 'RL105';
    end if;
    if existing_reading.input_hash <> requested_input_hash then
      raise exception 'IDEMPOTENCY_CONFLICT'
        using errcode = 'RL104';
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
      raise exception 'GENERATION_RECORD_MISSING'
        using errcode = 'RL106';
    end if;

    return query select existing_reading.id, existing_generation_id, false;
    return;
  end if;

  insert into public.readings (
    user_id,
    request_id,
    input_hash,
    kind,
    spread_type,
    schema_version,
    status,
    title,
    input_payload
  ) values (
    requested_user_id,
    requested_request_id,
    requested_input_hash,
    requested_kind,
    requested_spread_type,
    requested_schema_version,
    'generating',
    'Generating...',
    requested_input_payload
  )
  returning * into inserted_reading;

  insert into public.generation_records (
    reading_id,
    provider,
    model,
    prompt_version,
    idempotency_key,
    status
  ) values (
    inserted_reading.id,
    requested_provider,
    requested_model,
    requested_prompt_version,
    'reading:' || inserted_reading.id::text,
    'pending'
  )
  returning generation_records.id into inserted_generation_id;

  return query select inserted_reading.id, inserted_generation_id, true;
end;
$$;

create or replace function public.complete_reading_generation(
  requested_reading_id uuid,
  requested_generation_id bigint,
  requested_title text,
  requested_result jsonb
)
returns void
language plpgsql
security definer
set search_path = pg_catalog
as $$
declare
  changed_rows integer;
begin
  update public.generation_records
  set status = 'completed',
    error_code = null
  where generation_records.id = requested_generation_id
    and generation_records.reading_id = requested_reading_id
    and generation_records.status = 'pending';
  get diagnostics changed_rows = row_count;
  if changed_rows <> 1 then
    raise exception 'GENERATION_STATE_CONFLICT'
      using errcode = 'RL107';
  end if;

  update public.readings
  set status = 'completed',
    title = requested_title,
    result_payload = requested_result
  where readings.id = requested_reading_id
    and readings.status = 'generating'
    and readings.deleted_at is null;
  get diagnostics changed_rows = row_count;
  if changed_rows <> 1 then
    raise exception 'READING_STATE_CONFLICT'
      using errcode = 'RL108';
  end if;
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
  changed_rows integer;
begin
  update public.generation_records
  set status = 'failed',
    error_code = requested_error_code
  where generation_records.id = requested_generation_id
    and generation_records.reading_id = requested_reading_id
    and generation_records.status = 'pending';
  get diagnostics changed_rows = row_count;
  if changed_rows <> 1 then
    raise exception 'GENERATION_STATE_CONFLICT'
      using errcode = 'RL107';
  end if;

  update public.readings
  set status = 'failed'
  where readings.id = requested_reading_id
    and readings.status = 'generating'
    and readings.deleted_at is null;
  get diagnostics changed_rows = row_count;
  if changed_rows <> 1 then
    raise exception 'READING_STATE_CONFLICT'
      using errcode = 'RL108';
  end if;
end;
$$;

create or replace function public.start_failed_reading_retry(
  requested_user_id uuid,
  requested_reading_id uuid,
  requested_provider text,
  requested_model text,
  requested_prompt_version text
)
returns table (
  generation_id bigint
)
language plpgsql
security definer
set search_path = pg_catalog
as $$
declare
  owned_reading public.readings%rowtype;
  inserted_generation_id bigint;
begin
  select * into owned_reading
  from public.readings
  where readings.id = requested_reading_id
    and readings.user_id = requested_user_id
    and readings.schema_version > 0
    and readings.status = 'failed'
    and readings.deleted_at is null
  for update;

  if not found then
    raise exception 'READING_NOT_RETRYABLE'
      using errcode = 'RL109';
  end if;

  insert into public.generation_records (
    reading_id,
    provider,
    model,
    prompt_version,
    idempotency_key,
    status
  ) values (
    owned_reading.id,
    requested_provider,
    requested_model,
    requested_prompt_version,
    'reading-retry:' || owned_reading.id::text || ':' || gen_random_uuid()::text,
    'pending'
  )
  returning generation_records.id into inserted_generation_id;

  update public.readings
  set status = 'generating',
    title = 'Generating...',
    result_payload = null
  where readings.id = owned_reading.id;

  return query select inserted_generation_id;
end;
$$;

revoke all on function public.create_pending_reading(
  uuid, uuid, text, public.reading_kind, text, integer, jsonb, text, text, text
) from public;
revoke all on function public.complete_reading_generation(
  uuid, bigint, text, jsonb
) from public;
revoke all on function public.fail_reading_generation(
  uuid, bigint, text
) from public;

grant execute on function public.create_pending_reading(
  uuid, uuid, text, public.reading_kind, text, integer, jsonb, text, text, text
) to service_role;
grant execute on function public.complete_reading_generation(
  uuid, bigint, text, jsonb
) to service_role;
grant execute on function public.fail_reading_generation(
  uuid, bigint, text
) to service_role;
