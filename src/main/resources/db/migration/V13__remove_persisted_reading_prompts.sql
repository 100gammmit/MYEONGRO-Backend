-- Reading questions and free-text choice labels are transient generation input.
-- Remove them from existing records and retire server-side retries that depended
-- on reconstructing a prompt from the database.

update public.readings
set input_payload = input_payload - 'question' - 'choiceOptions',
  input_hash = encode(gen_random_bytes(32), 'hex');

update public.readings
set schema_version = case kind
    when 'tarot' then 2
    when 'saju' then 4
  end
where schema_version > 0;

alter table public.readings
  add constraint readings_no_persisted_prompt_text_check check (
    not (input_payload ? 'question')
    and not (input_payload ? 'choiceOptions')
  );

drop function if exists public.start_failed_reading_retry(
  uuid, uuid, text, text, text, integer, integer
);
