-- Reading questions and free-text choice labels are transient generation input.
-- Remove them from existing records and retire server-side retries that depended
-- on reconstructing a prompt from the database.

update public.readings
set input_payload = input_payload - 'question' - 'choiceOptions',
  input_hash = encode(gen_random_bytes(32), 'hex');

-- Saju v2 stored a city code even though the current calculation contract only
-- retains a province for known times and no place for unknown times. Normalize
-- both v2 and v3 rows before advertising the questionless v4 shape.
update public.readings
set input_payload = jsonb_set(
  input_payload,
  '{birthProfile}',
  case
    when input_payload #>> '{birthProfile,birthTimePrecision}' = 'unknown'
      then (input_payload -> 'birthProfile') - 'birthTime' - 'provinceCode' - 'cityCode'
    else (input_payload -> 'birthProfile') - 'cityCode'
  end
)
where kind = 'saju'
  and schema_version in (2, 3)
  and jsonb_typeof(input_payload -> 'birthProfile') = 'object';

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
