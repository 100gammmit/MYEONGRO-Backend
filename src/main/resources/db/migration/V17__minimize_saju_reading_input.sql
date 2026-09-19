-- Saju v5 retains only calculation facts needed to render a saved reading.
-- Pre-release Saju rows are deleted instead of guessing a privacy-safe conversion.

delete from public.readings where kind = 'saju';

create or replace function public.is_valid_saju_v5_input(payload jsonb)
returns boolean
language plpgsql
immutable
strict
set search_path = pg_catalog
as $$
declare
  snapshot jsonb;
  pillars jsonb;
  pillar_name text;
  pillar jsonb;
  relation jsonb;
  item jsonb;
  current_cycle jsonb;
  annual jsonb;
  uncertainty jsonb;
begin
  if jsonb_typeof(payload) <> 'object'
    or not (payload ?& array['targetYear', 'calculationSnapshot'])
    or payload - array['targetYear', 'calculationSnapshot'] <> '{}'::jsonb
    or jsonb_typeof(payload -> 'targetYear') <> 'number'
    or (payload ->> 'targetYear') !~ '^[0-9]{4}$' then
    return false;
  end if;

  snapshot := payload -> 'calculationSnapshot';
  if jsonb_typeof(snapshot) <> 'object'
    or not (snapshot ?& array[
      'calculationVersion', 'engine', 'engineVersion', 'cityCatalogVersion',
      'targetYear', 'pillars', 'fiveElements', 'relations',
      'limitations', 'uncertainty'
    ])
    or snapshot - array[
      'calculationVersion', 'engine', 'engineVersion', 'cityCatalogVersion',
      'targetYear', 'pillars', 'dayMaster', 'fiveElements', 'relations',
      'currentLuckCycle', 'annualFortune', 'limitations', 'uncertainty'
    ] <> '{}'::jsonb
    or jsonb_typeof(snapshot -> 'calculationVersion') <> 'string'
    or jsonb_typeof(snapshot -> 'engine') <> 'string'
    or jsonb_typeof(snapshot -> 'engineVersion') <> 'string'
    or jsonb_typeof(snapshot -> 'cityCatalogVersion') <> 'string'
    or jsonb_typeof(snapshot -> 'targetYear') <> 'number'
    or snapshot ->> 'targetYear' <> payload ->> 'targetYear'
    or (snapshot ? 'dayMaster' and jsonb_typeof(snapshot -> 'dayMaster') <> 'string')
    or jsonb_typeof(snapshot -> 'fiveElements') <> 'object'
    or (snapshot -> 'fiveElements') - array['wood', 'fire', 'earth', 'metal', 'water'] <> '{}'::jsonb
    or jsonb_typeof(snapshot -> 'relations') <> 'array'
    or jsonb_typeof(snapshot -> 'limitations') <> 'array' then
    return false;
  end if;

  pillars := snapshot -> 'pillars';
  if jsonb_typeof(pillars) <> 'object'
    or not (pillars ?& array['year', 'month', 'day', 'time'])
    or pillars - array['year', 'month', 'day', 'time'] <> '{}'::jsonb then
    return false;
  end if;

  foreach pillar_name in array array['year', 'month', 'day', 'time'] loop
    pillar := pillars -> pillar_name;
    if pillar <> 'null'::jsonb and (jsonb_typeof(pillar) <> 'object'
      or not (pillar ? 'ganZhi')
      or pillar - array['ganZhi', 'stemTenGod'] <> '{}'::jsonb
      or jsonb_typeof(pillar -> 'ganZhi') <> 'string'
      or (pillar ? 'stemTenGod' and jsonb_typeof(pillar -> 'stemTenGod') <> 'string')) then
      return false;
    end if;
  end loop;

  for item in select value from jsonb_each(snapshot -> 'fiveElements') loop
    if jsonb_typeof(item) <> 'number' then
      return false;
    end if;
  end loop;

  for relation in select value from jsonb_array_elements(snapshot -> 'relations') loop
    if jsonb_typeof(relation) <> 'object'
      or not (relation ?& array['type', 'members'])
      or relation - array['type', 'members'] <> '{}'::jsonb
      or jsonb_typeof(relation -> 'type') <> 'string'
      or jsonb_typeof(relation -> 'members') <> 'array' then
      return false;
    end if;
    for item in select value from jsonb_array_elements(relation -> 'members') loop
      if jsonb_typeof(item) <> 'string' then
        return false;
      end if;
    end loop;
  end loop;

  if snapshot ? 'currentLuckCycle' then
    current_cycle := snapshot -> 'currentLuckCycle';
    if current_cycle <> 'null'::jsonb and (
      jsonb_typeof(current_cycle) <> 'object'
      or not (current_cycle ?& array['startYear', 'endYear', 'ganZhi'])
      or current_cycle - array['startYear', 'endYear', 'ganZhi'] <> '{}'::jsonb
      or jsonb_typeof(current_cycle -> 'startYear') <> 'number'
      or jsonb_typeof(current_cycle -> 'endYear') <> 'number'
      or jsonb_typeof(current_cycle -> 'ganZhi') <> 'string'
    ) then
      return false;
    end if;
  end if;

  annual := snapshot -> 'annualFortune';
  if annual <> 'null'::jsonb and (
    jsonb_typeof(annual) <> 'object'
    or not (annual ?& array['year', 'ganZhi'])
    or annual - array['year', 'ganZhi', 'stemTenGod'] <> '{}'::jsonb
    or jsonb_typeof(annual -> 'year') <> 'number'
    or jsonb_typeof(annual -> 'ganZhi') <> 'string'
    or (annual ? 'stemTenGod' and jsonb_typeof(annual -> 'stemTenGod') <> 'string')
  ) then
    return false;
  end if;

  if jsonb_path_exists(
    snapshot -> 'limitations',
    '$[*] ? (@ == "BIRTH_TIME_UNKNOWN" || @ == "APPROXIMATE_BIRTH_TIME")'
  ) then
    return false;
  end if;
  for item in select value from jsonb_array_elements(snapshot -> 'limitations') loop
    if jsonb_typeof(item) <> 'string' then
      return false;
    end if;
  end loop;

  uncertainty := snapshot -> 'uncertainty';
  if jsonb_typeof(uncertainty) <> 'object'
    or not (uncertainty ? 'varyingFields')
    or uncertainty - 'varyingFields' <> '{}'::jsonb
    or jsonb_typeof(uncertainty -> 'varyingFields') <> 'array' then
    return false;
  end if;
  for item in select value from jsonb_array_elements(uncertainty -> 'varyingFields') loop
    if jsonb_typeof(item) <> 'string' then
      return false;
    end if;
  end loop;

  return true;
exception
  when others then
    return false;
end;
$$;

alter table public.readings
  add constraint readings_saju_v5_input_check check (
    kind <> 'saju'
    or (
      schema_version = 5
      and public.is_valid_saju_v5_input(input_payload)
    )
  );
