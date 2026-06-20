# Guest Consent API Design

## Scope

Implement the existing frontend contract for guest users only:

- `GET /api/consents` returns current required-consent status.
- `GET` creates a signed `myeongro_guest` cookie when none is valid.
- `POST /api/consents` requires a valid signed guest cookie.
- `POST` accepts only all three required document types.
- Supabase JWT users are deferred to the authentication phase.

## Architecture

`ConsentController` owns HTTP parsing and cookie headers. `GuestSessionSigner`
validates the server-owned guest identity. `ConsentService` validates the three
required documents and coordinates transactional persistence through the
Spring Data JPA `ConsentRepository` and `ConsentEntity`.

## Data Contract

Required document types and versions:

- `terms`: `2026-06-10`
- `privacy`: `2026-06-10`
- `sensitive-data`: `2026-06-10`

Repeated acceptance preserves the original `accepted_at` by querying current
document versions and saving only missing consent entities.

## Error Contract

- Missing or invalid guest cookie on `POST`: HTTP 400.
- Unknown fields, invalid document types, or missing required documents:
  HTTP 400.
- `GET` replaces an invalid cookie with a newly issued guest session.

## Verification

Unit tests cover service validation and status calculation. MVC tests cover
cookie issuance, cookie reuse, invalid `POST`, strict request parsing, and the
frontend JSON response shape. The full Gradle suite verifies Spring context
startup and existing Flyway contracts.
