-- Saju birth data is processed transiently to fulfill the user's reading request.
-- It no longer has an independently accepted consent document.

delete from public.consent_events
where document_type = 'SAJU_INPUT';

alter table public.consent_events
  drop constraint if exists consent_events_document_type_check;

alter table public.consent_events
  add constraint consent_events_document_type_check check (document_type in (
    'TERMS',
    'AI_OVERSEAS_TRANSFER'
  ));
