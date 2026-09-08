-- The product no longer offers a recovery window after account deletion.
-- Permanently remove accounts that were already withdrawn under the former
-- retention policy before removing the obsolete lifecycle columns.

delete from public.profiles
where deleted_at is not null;

drop index if exists public.profiles_purge_due_idx;
drop index if exists public.profiles_active_idx;

alter table public.profiles
  drop column if exists deleted_at,
  drop column if exists purge_after,
  drop column if exists purged_at;
