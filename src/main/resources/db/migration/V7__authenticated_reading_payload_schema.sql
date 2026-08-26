-- Guest AI readings are retired. New readings are authenticated-user owned,
-- while variable request/result bodies remain in versioned JSONB payloads.

drop function if exists public.create_pending_guest_reading_cache(
  uuid, text, uuid, text, public.reading_kind, jsonb
);
drop function if exists public.complete_guest_reading_cache(uuid, text, jsonb);
drop function if exists public.fail_guest_reading_cache(uuid, text);

drop function if exists public.create_pending_free_reading(
  uuid, uuid, text, uuid, text, public.reading_kind, jsonb, text, text, text
);
drop function if exists public.reserve_free_reading_quota(
  uuid, uuid, text, uuid, uuid
);

-- Guest-scoped data has no owner after guest AI reading support is removed.
-- User-owned readings are retained as schema version 0 instead of being
-- guessed into one of the new tarot spread types.
delete from public.consents where user_id is null;
delete from public.free_reading_quota_events where user_id is null;
delete from public.readings where user_id is null;

drop index if exists public.free_reading_quota_events_guest_cache_id_key;
drop index if exists public.free_reading_quota_events_guest_request_id_key;
alter table public.free_reading_quota_events
  drop constraint if exists free_reading_quota_events_exactly_one_subject;
alter table public.free_reading_quota_events
  drop column if exists guest_cache_id;
alter table public.free_reading_quota_events
  drop column if exists guest_session_id;
alter table public.free_reading_quota_events
  alter column user_id set not null;

drop index if exists public.consents_guest_key;
alter table public.consents
  drop constraint if exists consents_exactly_one_subject;
alter table public.consents
  drop column if exists guest_session_id;
alter table public.consents
  alter column user_id set not null;

drop index if exists public.readings_guest_created_idx;
alter table public.readings
  drop constraint if exists readings_user_owner_only;
alter table public.readings
  drop constraint if exists readings_exactly_one_owner;
alter table public.readings
  drop column if exists guest_session_id;
alter table public.readings
  alter column user_id set not null;

drop table if exists public.guest_reading_cache;
drop table if exists public.guest_ownership_transfers;

alter table public.readings rename column input to input_payload;
alter table public.readings rename column result to result_payload;
alter table public.readings add column spread_type text;
alter table public.readings add column schema_version integer;

update public.readings
set schema_version = 0
where schema_version is null;

alter table public.readings
  alter column schema_version set default 1;
alter table public.readings
  alter column schema_version set not null;
alter table public.readings
  add constraint readings_payload_schema_check check (
    (
      schema_version = 0
      and spread_type is null
    )
    or (
      schema_version > 0
      and (
        (
          kind = 'tarot'
          and spread_type in (
            'mind_three_card',
            'relationship_three_card',
            'choice_five_card'
          )
        )
        or (kind = 'saju' and spread_type is null)
      )
    )
  );

create index readings_tarot_spread_created_idx
  on public.readings(spread_type, created_at desc)
  where kind = 'tarot' and deleted_at is null;

create or replace function public.reserve_free_reading_quota(
  requested_user_id uuid,
  requested_ip_hash text,
  requested_reading_id uuid,
  requested_request_id uuid
)
returns table (
  quota_event_id bigint,
  user_id uuid,
  reading_id uuid,
  request_id uuid,
  already_reserved boolean
)
language plpgsql
security definer
set search_path = pg_catalog
as $$
declare
  normalized_ip_hash text := btrim(coalesce(requested_ip_hash, ''));
  normalized_request_id uuid;
  existing_event public.free_reading_quota_events%rowtype;
  inserted_event public.free_reading_quota_events%rowtype;
  user_daily_count integer := 0;
begin
  if requested_user_id is null then
    raise exception 'Authenticated user id is required';
  end if;

  if (requested_reading_id is not null)::integer
    + (requested_request_id is not null)::integer <> 1 then
    raise exception 'Exactly one reservation key is required';
  end if;

  if normalized_ip_hash = '' then
    raise exception 'IP hash is required';
  end if;

  if requested_reading_id is not null then
    select readings.request_id into normalized_request_id
    from public.readings
    where readings.id = requested_reading_id
      and readings.user_id = requested_user_id;

    if normalized_request_id is null then
      raise exception 'Reading not found for quota subject';
    end if;
  else
    normalized_request_id := requested_request_id;
  end if;

  perform pg_advisory_xact_lock(
    hashtextextended('user:' || requested_user_id::text, 0)
  );

  select * into existing_event
  from public.free_reading_quota_events
  where free_reading_quota_events.reading_id = requested_reading_id
    or (
      free_reading_quota_events.user_id = requested_user_id
      and free_reading_quota_events.request_id = normalized_request_id
    )
  order by free_reading_quota_events.created_at asc
  limit 1
  for update;

  if found then
    if requested_reading_id is not null and existing_event.reading_id is null then
      update public.free_reading_quota_events
      set reading_id = requested_reading_id
      where free_reading_quota_events.id = existing_event.id
      returning * into existing_event;
    end if;

    return query select
      existing_event.id,
      existing_event.user_id,
      existing_event.reading_id,
      existing_event.request_id,
      true;
    return;
  end if;

  select count(*)::integer into user_daily_count
  from public.free_reading_quota_events
  where free_reading_quota_events.user_id = requested_user_id
    and timezone('Asia/Seoul', created_at)::date
      = timezone('Asia/Seoul', statement_timestamp())::date;

  if user_daily_count >= 10 then
    raise exception 'FREE_READING_QUOTA_EXCEEDED'
      using errcode = 'RL103',
        detail = 'user_daily_limit_seoul',
        hint = 'Users can reserve at most 10 free readings per Asia/Seoul calendar day.';
  end if;

  insert into public.free_reading_quota_events (
    user_id,
    ip_hash,
    reading_id,
    request_id
  ) values (
    requested_user_id,
    normalized_ip_hash,
    requested_reading_id,
    normalized_request_id
  )
  returning * into inserted_event;

  return query select
    inserted_event.id,
    inserted_event.user_id,
    inserted_event.reading_id,
    inserted_event.request_id,
    false;
end;
$$;

create or replace function public.create_pending_free_reading(
  requested_user_id uuid,
  requested_ip_hash text,
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

  perform *
  from public.reserve_free_reading_quota(
    requested_user_id,
    requested_ip_hash,
    null,
    requested_request_id
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
    tier,
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
    'free',
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
    'free-reading:' || inserted_reading.id::text,
    'pending'
  )
  returning generation_records.id into inserted_generation_id;

  update public.free_reading_quota_events
  set reading_id = inserted_reading.id
  where free_reading_quota_events.user_id = requested_user_id
    and free_reading_quota_events.request_id = requested_request_id
    and free_reading_quota_events.reading_id is null;

  return query select inserted_reading.id, inserted_generation_id, true;
end;
$$;

create or replace function public.complete_free_reading_generation(
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

create or replace function public.fail_free_reading_generation(
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
    'free-reading-retry:' || owned_reading.id::text || ':' || gen_random_uuid()::text,
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

revoke all on function public.reserve_free_reading_quota(
  uuid, text, uuid, uuid
) from public;
revoke all on function public.create_pending_free_reading(
  uuid, text, uuid, text, public.reading_kind, text, integer, jsonb, text, text, text
) from public;
revoke all on function public.complete_free_reading_generation(
  uuid, bigint, text, jsonb
) from public;
revoke all on function public.fail_free_reading_generation(
  uuid, bigint, text
) from public;
revoke all on function public.start_failed_reading_retry(
  uuid, uuid, text, text, text
) from public;

grant execute on function public.reserve_free_reading_quota(
  uuid, text, uuid, uuid
) to service_role;
grant execute on function public.create_pending_free_reading(
  uuid, text, uuid, text, public.reading_kind, text, integer, jsonb, text, text, text
) to service_role;
grant execute on function public.complete_free_reading_generation(
  uuid, bigint, text, jsonb
) to service_role;
grant execute on function public.fail_free_reading_generation(
  uuid, bigint, text
) to service_role;
grant execute on function public.start_failed_reading_retry(
  uuid, uuid, text, text, text
) to service_role;
