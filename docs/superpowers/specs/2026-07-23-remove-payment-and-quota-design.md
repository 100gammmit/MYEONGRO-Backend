# Payment And Reading Quota Removal Design

## Goal

Remove payment, purchase, paid-tier, and daily reading quota concepts from the current service. Keep one authenticated-user reading flow with consent, idempotency, generation state transitions, retry, records, and account deletion behavior.

## Scope

This milestone changes the Backend repository, Front repository, and the two root product/API specification documents. It does not add a replacement monetization model, a new rate limit, or a compatibility API for payment clients.

## Database Design

Existing Flyway migrations V1 through V7 remain immutable. A new V8 migration performs the destructive policy removal for databases that have already applied the earlier migrations.

V8 will:

- drop payment confirmation and webhook functions;
- drop `payment_webhook_events`, `purchases`, and `purchase_status`;
- drop `reserve_free_reading_quota` and `free_reading_quota_events`;
- replace `create_pending_free_reading`, `complete_free_reading_generation`, and `fail_free_reading_generation` with `create_pending_reading`, `complete_reading_generation`, and `fail_reading_generation`;
- remove `readings.tier` and `reading_tier`;
- preserve the unique authenticated owner/request contract, input hash comparison, reading/generation state checks, and retry function;
- use `reading:` and `reading-retry:` idempotency key prefixes for newly created generation records.

The migration explicitly removes dependent functions and constraints instead of relying on broad `CASCADE` drops. Existing rows are retained; only payment, tier, and quota records are discarded.

## Backend Design

`ReadingCreationService` will no longer hash or accept the client IP for reading creation. `PendingReadingCommand` and `JdbcReadingCreationRepository` will use the generic V8 function signature without an IP hash. The quota SQL-state mapping, `FreeReadingQuotaExceededException`, and controller 429 handler will be removed.

The create, complete, fail, retry, consent, draw-session consumption, and OpenAI boundaries remain otherwise unchanged. Account purge will hard-delete all due soft-deleted readings because purchases no longer exist as a retention guard.

The existing uncommitted OpenAI model and temperature changes are user-owned. They remain in the worktree and are excluded from this milestone's commits. The `app.reading.ip-hash-secret` removal in the already-dirty `application.yaml` will be staged separately from those user changes.

## Frontend Design

The Front will remove the dedicated 429 daily-free-reading message and rename tests or descriptions that classify reading creation as free. Existing authenticated proxying, draw-session recovery, consent, generic API error handling, and records behavior remain unchanged.

The Front branch is stacked on `feature/tarot-route-consent-flow` because that branch owns the current tarot experience where the quota message exists. Integration must merge that parent work before or together with this branch.

## Documentation Design

`mvp-requirements.md` and `spring-backend-api-db-spec.md` will define a single reading model with no payment, paid tier, purchase, quota event, daily cap, or payment API roadmap. References to free readings will become readings where they describe the core flow. The documents may state explicitly that the current service has no payment or paid-tier model, but will not retain obsolete implementation contracts.

## Error And Compatibility Policy

- Quota SQL states `RL101`, `RL102`, and `RL103` disappear from the active V8 contract.
- HTTP 429 is no longer a reading quota response.
- Idempotency and generation conflicts keep their existing status behavior.
- No payment endpoint compatibility layer is introduced because payment is outside the active product contract.
- Historical V1 through V7 SQL can still contain retired definitions; V8 is the authoritative final schema transition.

## Verification

- Backend repository tests cover generic function names/signatures, idempotency, state transitions, account purge without purchases, and absence of quota exception mapping.
- A fresh PostgreSQL replay applies V1 through V8 and verifies removed tables, types, columns, and functions plus the retained reading flow.
- Front tests verify ordinary authenticated reading proxy behavior and generic API error handling without a quota-specific 429 message.
- Root documents are searched for obsolete payment, purchase, paid-tier, and quota contracts after editing.
- Related tests run during implementation; each repository receives one full verification at milestone completion.

## Risks

V8 irreversibly deletes payment and quota rows. This is intentional for the current project policy. Deployments that need historical payment records must export them before applying V8; the application will no longer read or preserve those records.
