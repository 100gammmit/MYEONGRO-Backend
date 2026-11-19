-- saju-ko-v5 readings may carry calculationBasis: the applied day boundary, equation of time and longitude unit.
-- It holds no birth data, codes or longitude. This is a superset of the previous v6/v7 rule: payloads without
-- calculationBasis (every existing row) validate exactly as before, and no row is changed.

CREATE FUNCTION public.is_valid_saju_calculation_basis(basis jsonb) RETURNS boolean
LANGUAGE plpgsql IMMUTABLE STRICT SET search_path TO 'pg_catalog' AS $$
BEGIN
  RETURN coalesce(
    jsonb_typeof(basis) = 'object'
    AND basis ?& ARRAY['dayBoundary', 'equationOfTime', 'coordinateSource']
    AND basis - ARRAY['dayBoundary', 'equationOfTime', 'coordinateSource'] = '{}'::jsonb
    AND jsonb_typeof(basis -> 'dayBoundary') = 'string'
    AND basis ->> 'dayBoundary' IN ('MIDNIGHT', 'HOUR_23')
    AND jsonb_typeof(basis -> 'equationOfTime') = 'boolean'
    AND jsonb_typeof(basis -> 'coordinateSource') = 'string'
    AND basis ->> 'coordinateSource' IN ('reference', 'province', 'city'),
    false);
EXCEPTION WHEN others THEN RETURN false;
END;
$$;

-- Keep the V3 validator body unchanged under a new name and put the basis check in front of it.
ALTER FUNCTION public.is_valid_saju_v6_input(jsonb) RENAME TO is_valid_saju_v6_monthly_input;

CREATE FUNCTION public.is_valid_saju_v6_input(payload jsonb) RETURNS boolean
LANGUAGE plpgsql IMMUTABLE STRICT SET search_path TO 'pg_catalog' AS $$
DECLARE
  snapshot jsonb;
BEGIN
  snapshot := payload -> 'calculationSnapshot';
  IF jsonb_typeof(snapshot) IS DISTINCT FROM 'object' THEN RETURN false; END IF;
  IF snapshot ? 'calculationBasis' THEN
    IF public.is_valid_saju_calculation_basis(snapshot -> 'calculationBasis') IS DISTINCT FROM true THEN
      RETURN false;
    END IF;
    payload := jsonb_set(payload, '{calculationSnapshot}', snapshot - 'calculationBasis');
  END IF;
  RETURN coalesce(public.is_valid_saju_v6_monthly_input(payload), false);
EXCEPTION WHEN others THEN RETURN false;
END;
$$;

-- is_valid_saju_v7_input calls is_valid_saju_v6_input by name, so it now accepts the basis too. The CHECK
-- constraint references functions by OID and still points at the renamed function, so it is recreated (as in V4).
ALTER TABLE public.readings DROP CONSTRAINT readings_saju_versioned_input_check;
ALTER TABLE public.readings ADD CONSTRAINT readings_saju_versioned_input_check CHECK (
  kind <> 'saju' OR
  (schema_version = 5 AND public.is_valid_saju_v5_input(input_payload)) OR
  (schema_version = 6 AND public.is_valid_saju_v6_input(input_payload)) OR
  (schema_version = 7 AND public.is_valid_saju_v7_input(input_payload) IS TRUE)
);
