-- Add a report discriminator only; old v5/v6 rows and their validators remain unchanged.
CREATE FUNCTION public.is_valid_saju_v7_input(payload jsonb) RETURNS boolean
LANGUAGE plpgsql IMMUTABLE STRICT SET search_path TO 'pg_catalog' AS $$
BEGIN
  RETURN coalesce(jsonb_typeof(payload) = 'object'
    AND payload ->> 'reportType' = 'annual_monthly'
    AND public.is_valid_saju_v6_input(payload - 'reportType'), false);
EXCEPTION WHEN others THEN RETURN false;
END;
$$;

ALTER TABLE public.readings DROP CONSTRAINT readings_saju_versioned_input_check;
ALTER TABLE public.readings ADD CONSTRAINT readings_saju_versioned_input_check CHECK (
  kind <> 'saju' OR
  (schema_version = 5 AND public.is_valid_saju_v5_input(input_payload)) OR
  (schema_version = 6 AND public.is_valid_saju_v6_input(input_payload)) OR
  (schema_version = 7 AND public.is_valid_saju_v7_input(input_payload) IS TRUE)
);
