create table public.reading_preparations (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  request_id uuid not null,
  token uuid not null,
  expires_at timestamptz not null
);

create function public.acquire_reading_preparation(
  requested_user_id uuid, requested_request_id uuid, requested_token uuid,
  requested_credit_cost integer, requested_daily_grant integer,
  requested_global_daily_attempt_limit integer
) returns boolean
  language plpgsql security definer set search_path to 'pg_catalog'
as $$
declare
  available_credits integer;
begin
  perform pg_advisory_xact_lock(
    hashtextextended('reading-credit:' || requested_user_id::text, 0)
  );

  -- The caller replays requests that were inserted after its first lookup.
  if exists (
    select 1 from public.readings
    where user_id = requested_user_id and request_id = requested_request_id
  ) then
    return false;
  end if;
  if exists (
    select 1 from public.readings
    where user_id = requested_user_id and status = 'generating'
  ) or exists (
    select 1 from public.reading_preparations
    where user_id = requested_user_id and expires_at > clock_timestamp()
  ) then
    raise exception 'READING_GENERATION_IN_PROGRESS' using errcode = 'RL111';
  end if;

  perform public.apply_daily_reading_credit_reset(requested_user_id, requested_daily_grant);
  select free_credit_balance + paid_credit_balance into available_credits
  from public.profiles where id = requested_user_id;
  if available_credits is null or available_credits < requested_credit_cost then
    raise exception 'INSUFFICIENT_READING_CREDITS' using errcode = 'RL112';
  end if;
  if requested_global_daily_attempt_limit = 0 or exists (
    select 1 from public.reading_daily_capacity
    where usage_date = (clock_timestamp() at time zone 'Asia/Seoul')::date
      and attempt_count >= requested_global_daily_attempt_limit
  ) then
    raise exception 'READING_DAILY_CAPACITY_REACHED' using errcode = 'RL113';
  end if;

  insert into public.reading_preparations (user_id, request_id, token, expires_at)
  values (requested_user_id, requested_request_id, requested_token, clock_timestamp() + interval '30 seconds')
  on conflict (user_id) do update
    set request_id = excluded.request_id, token = excluded.token, expires_at = excluded.expires_at;
  return true;
end;
$$;

create function public.release_reading_preparation(requested_user_id uuid, requested_token uuid)
returns void
  language sql security definer set search_path to 'pg_catalog'
as $$
  delete from public.reading_preparations where user_id = requested_user_id and token = requested_token;
$$;

-- Keep the existing overload for a drained deployment rollback.
create function public.create_pending_reading(
  requested_user_id uuid, requested_request_id uuid, requested_input_hash text,
  requested_kind public.reading_kind, requested_spread_type text, requested_schema_version integer,
  requested_input_payload jsonb, requested_provider text, requested_model text, requested_prompt_version text,
  requested_credit_cost integer, requested_daily_grant integer, requested_global_daily_attempt_limit integer,
  requested_token uuid
) returns table(reading_id uuid, generation_id bigint, created boolean)
  language plpgsql security definer set search_path to 'pg_catalog'
as $$
begin
  perform pg_advisory_xact_lock(
    hashtextextended('reading-credit:' || requested_user_id::text, 0)
  );
  if not exists (
    select 1 from public.reading_preparations
    where user_id = requested_user_id and request_id = requested_request_id
      and token = requested_token and expires_at > clock_timestamp()
  ) then
    raise exception 'READING_GENERATION_EXPIRED' using errcode = 'RL108';
  end if;

  return query select * from public.create_pending_reading(
    requested_user_id, requested_request_id, requested_input_hash, requested_kind,
    requested_spread_type, requested_schema_version, requested_input_payload, requested_provider,
    requested_model, requested_prompt_version, requested_credit_cost, requested_daily_grant,
    requested_global_daily_attempt_limit
  );
  perform public.release_reading_preparation(requested_user_id, requested_token);
end;
$$;

revoke all on function public.acquire_reading_preparation(uuid, uuid, uuid, integer, integer, integer) from public;
revoke all on function public.release_reading_preparation(uuid, uuid) from public;
revoke all on function public.create_pending_reading(
  uuid, uuid, text, public.reading_kind, text, integer, jsonb, text, text, text, integer, integer,
  integer, uuid
) from public;
