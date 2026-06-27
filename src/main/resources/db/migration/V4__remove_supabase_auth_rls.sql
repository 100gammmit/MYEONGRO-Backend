drop policy if exists profiles_select_own on public.profiles;
drop policy if exists profiles_update_own on public.profiles;

drop policy if exists readings_select_own on public.readings;
drop policy if exists readings_insert_own on public.readings;
drop policy if exists readings_update_own on public.readings;
drop policy if exists readings_delete_own on public.readings;

drop policy if exists consents_select_own on public.consents;
drop policy if exists consents_insert_own on public.consents;

drop policy if exists purchases_select_own on public.purchases;

drop policy if exists followups_select_own on public.followups;
drop policy if exists followups_insert_own on public.followups;

drop policy if exists generation_records_select_own on public.generation_records;

drop policy if exists guest_transfers_select_own on public.guest_ownership_transfers;

alter table public.profiles disable row level security;
alter table public.readings disable row level security;
alter table public.consents disable row level security;
alter table public.purchases disable row level security;
alter table public.followups disable row level security;
alter table public.generation_records disable row level security;
alter table public.guest_ownership_transfers disable row level security;
alter table public.payment_webhook_events disable row level security;
alter table public.free_reading_quota_events disable row level security;
alter table public.guest_reading_cache disable row level security;
