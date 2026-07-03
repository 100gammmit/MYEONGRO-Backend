# Flyway Migration Policy

This MVP intentionally rewrites the cumulative V1/V3 baseline while there is no
production application data.

The runtime keeps:

- `spring.flyway.validate-on-migrate=true`
- `spring.flyway.clean-disabled=true`
- `spring.flyway.baseline-on-migrate=false`

Because of that, any local or shared dev database that already recorded the old V1 or V3 checksum in `flyway_schema_history` will fail validation after this change. Do not bypass validation silently.

For this milestone, reset affected non-production databases before starting the
app:

1. Drop and recreate the local/dev database, or recreate the Docker volume.
2. Run the Spring backend so Flyway applies the rewritten baseline from an empty
   schema.

If a shared dev database must keep manually created seed data, coordinate a
manual `flyway repair` only after confirming the schema has been rebuilt to match
the rewritten migrations. Do not use repair as a substitute for applying the
schema changes.

After the MVP has real production data, do not edit an already-applied migration.
Add a new versioned migration instead.
