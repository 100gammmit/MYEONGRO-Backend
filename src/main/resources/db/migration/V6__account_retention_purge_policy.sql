alter table public.profiles
  add column if not exists purge_after timestamptz,
  add column if not exists purged_at timestamptz;

create index if not exists profiles_purge_due_idx
  on public.profiles (purge_after)
  where deleted_at is not null
    and purge_after is not null
    and purged_at is null;
