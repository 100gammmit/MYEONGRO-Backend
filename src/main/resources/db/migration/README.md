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

## Fresh Docker Postgres replay check

Use a disposable PostgreSQL container and a separate port when checking whether
the full Flyway chain can replay on an empty PostgreSQL database. This avoids
deleting the normal local development volume. Do not use `docker compose -p`
for this check while `compose.yaml` keeps a fixed `container_name`; the fixed
name collides with the normal local Postgres container.

Use `POSTGRES_PORT=55432` for the replay database.

PowerShell:

```powershell
docker run -d --name myeongro-flyway-check-postgres `
  -e POSTGRES_DB=myeongro `
  -e POSTGRES_USER=<local-postgres-user> `
  -e POSTGRES_PASSWORD=<local-postgres-password> `
  -p 55432:5432 `
  postgres:16-alpine
```

Then run the backend with a JDBC URL that points at port `55432` and keep
`spring.flyway.validate-on-migrate=true`. For a throwaway replay database only,
you may also set `spring.flyway.clean-disabled=false` if you intentionally run a
manual Flyway clean before replaying migrations.

After startup succeeds, check that `public.flyway_schema_history` contains the
expected versioned migrations through the latest `V*__*.sql` file.

Cleanup:

```powershell
docker rm -f myeongro-flyway-check-postgres
```

The V1 compatibility shim creates Supabase legacy roles only when they are
missing. Docker Postgres runs migrations as the `POSTGRES_USER` superuser, so
that is acceptable for local replay. If a fresh managed PostgreSQL database is
initialized with a non-`CREATEROLE` account, create the `anon`, `authenticated`,
and `service_role` roles in provisioning first or run migrations with a role
that can create them.
