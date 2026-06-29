alter table public.profiles
  add column if not exists deleted_at timestamptz;

create index if not exists profiles_active_idx
  on public.profiles (id)
  where deleted_at is null;
