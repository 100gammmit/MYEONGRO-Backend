-- Bind signup completion recovery to the exact identity-level signup generation.
-- Existing rows remain valid for login eligibility but cannot satisfy a new pending signup recovery.

alter table public.adult_eligibility_assertions
  add column signup_generation_id varchar(64);

create unique index adult_eligibility_assertions_signup_generation_unique
  on public.adult_eligibility_assertions (signup_generation_id)
  where signup_generation_id is not null;
