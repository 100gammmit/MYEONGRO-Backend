-- Preserve v5 rows unchanged; v6 adds only the target-year public flow calendar.
CREATE FUNCTION public.is_valid_saju_v6_input(payload jsonb) RETURNS boolean
LANGUAGE plpgsql IMMUTABLE STRICT SET search_path TO 'pg_catalog' AS $$
DECLARE
  snapshot jsonb; calendar jsonb; month_entry jsonb; period jsonb;
  month_index integer := 0; flow_year integer;
  expected_start timestamptz; expected_end timestamptz; previous_end timestamptz;
  period_start timestamptz; period_end timestamptz;
BEGIN
  snapshot := payload -> 'calculationSnapshot';
  IF jsonb_typeof(snapshot) IS DISTINCT FROM 'object' OR NOT (snapshot ? 'monthlyCalendar') THEN RETURN false; END IF;
  IF public.is_valid_saju_v5_input(jsonb_set(payload, '{calculationSnapshot}', snapshot - 'monthlyCalendar')) IS DISTINCT FROM true THEN RETURN false; END IF;
  calendar := snapshot -> 'monthlyCalendar';
  IF jsonb_typeof(calendar) IS DISTINCT FROM 'object'
    OR NOT (calendar ?& ARRAY['calculationVersion','timeZone','targetYear','months'])
    OR calendar - ARRAY['calculationVersion','timeZone','targetYear','months'] <> '{}'::jsonb
    OR calendar ->> 'calculationVersion' IS DISTINCT FROM 'saju-monthly-ko-v1'
    OR calendar ->> 'timeZone' IS DISTINCT FROM 'Asia/Seoul'
    OR jsonb_typeof(calendar -> 'targetYear') IS DISTINCT FROM 'number'
    OR calendar ->> 'targetYear' IS DISTINCT FROM payload ->> 'targetYear'
    OR jsonb_typeof(calendar -> 'months') IS DISTINCT FROM 'array'
    OR jsonb_array_length(calendar -> 'months') <> 12 THEN RETURN false; END IF;
  flow_year := (calendar ->> 'targetYear')::integer;
  IF flow_year < 1900 OR flow_year > 2100 THEN RETURN false; END IF;
  FOR month_entry IN SELECT value FROM jsonb_array_elements(calendar -> 'months') LOOP
    month_index := month_index + 1;
    IF jsonb_typeof(month_entry) IS DISTINCT FROM 'object'
      OR NOT (month_entry ?& ARRAY['month','periods'])
      OR month_entry - ARRAY['month','periods'] <> '{}'::jsonb
      OR month_entry -> 'month' IS DISTINCT FROM to_jsonb(month_index)
      OR jsonb_typeof(month_entry -> 'periods') IS DISTINCT FROM 'array'
      OR jsonb_array_length(month_entry -> 'periods') <> 2 THEN RETURN false; END IF;
    expected_start := make_timestamptz(flow_year, month_index, 1, 0, 0, 0, 'Asia/Seoul');
    -- Calendar arithmetic must use Seoul, not the database session's time zone.
    expected_end := (make_date(flow_year, month_index, 1) + interval '1 month') AT TIME ZONE 'Asia/Seoul';
    previous_end := expected_start;
    FOR period IN SELECT value FROM jsonb_array_elements(month_entry -> 'periods') LOOP
      IF jsonb_typeof(period) IS DISTINCT FROM 'object'
        OR NOT (period ?& ARRAY['startInclusive','endExclusive','yearGanZhi','monthGanZhi'])
        OR period - ARRAY['startInclusive','endExclusive','yearGanZhi','monthGanZhi','stemTenGod'] <> '{}'::jsonb
        OR jsonb_typeof(period -> 'startInclusive') IS DISTINCT FROM 'string'
        OR jsonb_typeof(period -> 'endExclusive') IS DISTINCT FROM 'string'
        OR (period ->> 'startInclusive') !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\+09:00$'
        OR (period ->> 'endExclusive') !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\+09:00$'
        OR jsonb_typeof(period -> 'yearGanZhi') IS DISTINCT FROM 'string'
        OR jsonb_typeof(period -> 'monthGanZhi') IS DISTINCT FROM 'string'
        OR length(period ->> 'yearGanZhi') = 0 OR length(period ->> 'monthGanZhi') = 0
        OR (period ? 'stemTenGod' AND (NOT (snapshot ? 'dayMaster')
          OR jsonb_typeof(period -> 'stemTenGod') IS DISTINCT FROM 'string' OR length(period ->> 'stemTenGod') = 0)) THEN RETURN false; END IF;
      period_start := (period ->> 'startInclusive')::timestamptz;
      period_end := (period ->> 'endExclusive')::timestamptz;
      IF period_start <> previous_end OR period_end <= period_start OR period_end > expected_end THEN RETURN false; END IF;
      previous_end := period_end;
    END LOOP;
    IF previous_end <> expected_end THEN RETURN false; END IF;
  END LOOP;
  RETURN true;
EXCEPTION WHEN others THEN RETURN false;
END;
$$;

ALTER TABLE public.readings DROP CONSTRAINT readings_saju_v5_input_check;
ALTER TABLE public.readings ADD CONSTRAINT readings_saju_versioned_input_check CHECK (
  kind <> 'saju' OR
  (schema_version = 5 AND public.is_valid_saju_v5_input(input_payload)) OR
  (schema_version = 6 AND public.is_valid_saju_v6_input(input_payload))
);
