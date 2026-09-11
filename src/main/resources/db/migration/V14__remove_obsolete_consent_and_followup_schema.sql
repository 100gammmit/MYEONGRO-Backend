-- Retire the pre-event consent snapshot and the unimplemented follow-up
-- reading design. The service has never been deployed, so legacy notice-only
-- consent events do not represent production audit records.

delete from public.consent_events
where document_type in ('PRIVACY', 'SENSITIVE_DATA');

alter table public.consent_events
  drop constraint if exists consent_events_document_type_check;

alter table public.consent_events
  add constraint consent_events_document_type_check check (document_type in (
    'TERMS',
    'AI_OVERSEAS_TRANSFER',
    'SAJU_INPUT'
  ));

drop table if exists public.followups;
drop type if exists public.followup_status;

drop table if exists public.consents;
drop type if exists public.consent_document_type;
