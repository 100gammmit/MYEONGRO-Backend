create table if not exists public.guest_reading_cache (
  id uuid primary key default gen_random_uuid(),
  guest_session_id uuid not null,
  request_id uuid not null,
  input_hash text not null check (length(btrim(input_hash)) > 0),
  ip_hash text not null check (length(btrim(ip_hash)) > 0),
  kind public.reading_kind not null,
  status public.reading_status not null default 'generating',
  title text not null default 'Generating...',
  input jsonb not null default '{}'::jsonb,
  result jsonb,
  error_code text,
  expires_at timestamptz not null default (now() + interval '7 days'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists guest_reading_cache_guest_request_id_key
  on public.guest_reading_cache (guest_session_id, request_id);

create index if not exists guest_reading_cache_expires_at_idx
  on public.guest_reading_cache (expires_at);

alter table public.guest_reading_cache enable row level security;

revoke all on table public.guest_reading_cache from public;
revoke all on table public.guest_reading_cache from anon;
revoke all on table public.guest_reading_cache from authenticated;
grant select, insert, update, delete on table public.guest_reading_cache to service_role;

alter table public.free_reading_quota_events
  add column if not exists guest_cache_id uuid
    references public.guest_reading_cache(id) on delete set null;

create unique index if not exists free_reading_quota_events_guest_cache_id_key
  on public.free_reading_quota_events (guest_cache_id)
  where guest_cache_id is not null;

alter table public.readings
  drop constraint if exists readings_exactly_one_owner;

alter table public.readings
  add constraint readings_user_owner_only
    check (user_id is not null and guest_session_id is null)
    not valid;

create or replace function public.create_pending_guest_reading_cache(
  requested_guest_session_id uuid,
  requested_ip_hash text,
  requested_request_id uuid,
  requested_input_hash text,
  requested_kind public.reading_kind,
  requested_input jsonb
)
returns table (
  cache_id uuid,
  created boolean
)
language plpgsql
security definer
set search_path = pg_catalog
as $$
declare
  normalized_ip_hash text := btrim(coalesce(requested_ip_hash, ''));
  existing_cache public.guest_reading_cache%rowtype;
  inserted_cache public.guest_reading_cache%rowtype;
begin
  if requested_guest_session_id is null then
    raise exception 'Guest session id is required';
  end if;

  if requested_request_id is null then
    raise exception 'Request id is required';
  end if;

  if length(btrim(coalesce(requested_input_hash, ''))) = 0 then
    raise exception 'Input hash is required';
  end if;

  perform *
  from public.reserve_free_reading_quota(
    null,
    requested_guest_session_id,
    normalized_ip_hash,
    null,
    requested_request_id
  );

  select * into existing_cache
  from public.guest_reading_cache
  where guest_reading_cache.guest_session_id = requested_guest_session_id
    and guest_reading_cache.request_id = requested_request_id
  limit 1
  for update;

  if found then
    if existing_cache.input_hash <> requested_input_hash then
      raise exception 'IDEMPOTENCY_CONFLICT'
        using errcode = 'RL104';
    end if;

    if existing_cache.status = 'failed' then
      update public.guest_reading_cache
      set status = 'generating',
        title = 'Generating...',
        result = null,
        error_code = null,
        updated_at = now()
      where guest_reading_cache.id = existing_cache.id
      returning * into existing_cache;
    end if;

    update public.free_reading_quota_events
    set guest_cache_id = existing_cache.id
    where free_reading_quota_events.guest_session_id = requested_guest_session_id
      and free_reading_quota_events.request_id = requested_request_id
      and free_reading_quota_events.guest_cache_id is null;

    return query select existing_cache.id, false;
    return;
  end if;

  insert into public.guest_reading_cache (
    guest_session_id,
    request_id,
    input_hash,
    ip_hash,
    kind,
    status,
    title,
    input
  ) values (
    requested_guest_session_id,
    requested_request_id,
    requested_input_hash,
    normalized_ip_hash,
    requested_kind,
    'generating',
    'Generating...',
    requested_input
  )
  returning * into inserted_cache;

  update public.free_reading_quota_events
  set guest_cache_id = inserted_cache.id
  where free_reading_quota_events.guest_session_id = requested_guest_session_id
    and free_reading_quota_events.request_id = requested_request_id
    and free_reading_quota_events.guest_cache_id is null;

  return query select inserted_cache.id, true;
end;
$$;

create or replace function public.complete_guest_reading_cache(
  requested_cache_id uuid,
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
  update public.guest_reading_cache
  set status = 'completed',
    title = requested_title,
    result = requested_result,
    error_code = null,
    updated_at = now()
  where guest_reading_cache.id = requested_cache_id
    and guest_reading_cache.status = 'generating';
  get diagnostics changed_rows = row_count;
  if changed_rows <> 1 then
    raise exception 'GUEST_READING_CACHE_STATE_CONFLICT'
      using errcode = 'RL110';
  end if;
end;
$$;

create or replace function public.fail_guest_reading_cache(
  requested_cache_id uuid,
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
  update public.guest_reading_cache
  set status = 'failed',
    error_code = requested_error_code,
    updated_at = now()
  where guest_reading_cache.id = requested_cache_id
    and guest_reading_cache.status = 'generating';
  get diagnostics changed_rows = row_count;
  if changed_rows <> 1 then
    raise exception 'GUEST_READING_CACHE_STATE_CONFLICT'
      using errcode = 'RL110';
  end if;
end;
$$;

revoke all on function public.create_pending_guest_reading_cache(
  uuid, text, uuid, text, public.reading_kind, jsonb
) from public;
revoke all on function public.create_pending_guest_reading_cache(
  uuid, text, uuid, text, public.reading_kind, jsonb
) from anon;
revoke all on function public.create_pending_guest_reading_cache(
  uuid, text, uuid, text, public.reading_kind, jsonb
) from authenticated;
grant execute on function public.create_pending_guest_reading_cache(
  uuid, text, uuid, text, public.reading_kind, jsonb
) to service_role;

revoke all on function public.complete_guest_reading_cache(
  uuid, text, jsonb
) from public;
revoke all on function public.complete_guest_reading_cache(
  uuid, text, jsonb
) from anon;
revoke all on function public.complete_guest_reading_cache(
  uuid, text, jsonb
) from authenticated;
grant execute on function public.complete_guest_reading_cache(
  uuid, text, jsonb
) to service_role;

revoke all on function public.fail_guest_reading_cache(
  uuid, text
) from public;
revoke all on function public.fail_guest_reading_cache(
  uuid, text
) from anon;
revoke all on function public.fail_guest_reading_cache(
  uuid, text
) from authenticated;
grant execute on function public.fail_guest_reading_cache(
  uuid, text
) to service_role;
