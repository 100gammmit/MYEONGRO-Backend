# Payment And Reading Quota Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove payment, paid-tier, purchase, and daily reading quota behavior while preserving authenticated reading idempotency and generation state transitions.

**Architecture:** Add an append-only V8 Flyway migration that converts the final schema to a single reading model and replaces quota-named PostgreSQL functions with generic reading functions. Align Spring JDBC/service/controller code, the current Front tarot flow, and the two root specifications with that final schema without modifying V1-V7.

**Tech Stack:** Java 21, Spring Boot, JdbcTemplate, Flyway, PostgreSQL 16, Next.js, TypeScript, Vitest, Markdown.

## Global Constraints

- Keep V1 through V7 immutable and add V8.
- Preserve authenticated owner/request idempotency, input hash checks, generation state transitions, retry, records, consent, draw-session consumption, and OpenAI behavior.
- Remove all payment, purchase, paid-tier, quota event, daily-cap, IP-hash, and HTTP 429 reading-quota contracts.
- Do not include the user's uncommitted OpenAI model or temperature changes in milestone commits.
- Run related tests during implementation and one full test verification per repository at milestone completion.

---

### Task 1: Define The V8 Final Schema Contract

**Files:**
- Create: `src/main/resources/db/migration/V8__remove_payment_and_reading_quota.sql`
- Modify: `src/test/java/com/myeongro/api/database/FlywayBaselineContractTests.java`
- Modify: `src/test/java/com/myeongro/api/database/FlywayFreshPostgresReplayTests.java`

**Interfaces:**
- Produces: `public.create_pending_reading(uuid, uuid, text, reading_kind, text, integer, jsonb, text, text, text)`.
- Produces: `public.complete_reading_generation(uuid, bigint, text, jsonb)` and `public.fail_reading_generation(uuid, bigint, text)`.
- Removes: payment/quota tables and functions, `readings.tier`, `reading_tier`, and `purchase_status`.

- [ ] Add contract assertions for V8 generic functions and explicit drop statements; run the database contract test and confirm it fails because V8 is absent.
- [ ] Add V8 with generic create/complete/fail functions. The create function must lock or query the existing `(user_id, request_id)` reading, compare `input_hash`, preserve RL104/RL105/RL106/RL110, and insert one reading plus one generation record without quota reservation.
- [ ] Explicitly drop payment functions, webhook/purchase/quota tables, old free-named functions, `readings.tier`, and retired enums without broad cascade.
- [ ] Extend fresh replay assertions to query PostgreSQL catalogs for absence of removed objects and presence of generic functions.
- [ ] Run the database contract test and fresh PostgreSQL replay when connection properties are available.

### Task 2: Align Backend Reading And Account Persistence

**Files:**
- Modify: `src/main/java/com/myeongro/api/domain/reading/repository/PendingReadingCommand.java`
- Modify: `src/main/java/com/myeongro/api/domain/reading/repository/JdbcReadingCreationRepository.java`
- Modify: `src/main/java/com/myeongro/api/domain/reading/service/ReadingCreationService.java`
- Modify: `src/main/java/com/myeongro/api/domain/reading/controller/ReadingController.java`
- Delete: `src/main/java/com/myeongro/api/domain/reading/exception/FreeReadingQuotaExceededException.java`
- Modify: `src/main/java/com/myeongro/api/domain/profile/repository/JdbcAccountPurgeRepository.java`
- Modify: related repository/service/controller/profile tests.

**Interfaces:**
- `ReadingCreationService.createUserReading(UUID userId, UUID requestId, ReadingCreateRequest request)` no longer consumes a remote address.
- `PendingReadingCommand` no longer contains `ipHash`.
- JDBC calls V8 generic function names and keeps RL104/RL110 conflict mapping.

- [ ] Update repository, service, controller, and purge tests first to expect generic function names, no IP argument/secret, no quota exception mapping, and unconditional due-reading purge; run them and confirm contract failures.
- [ ] Remove IP hashing and quota exception code, update SQL calls/signatures, and remove the purchase guard from account purge.
- [ ] Remove `app.reading.ip-hash-secret` while preserving the user's unrelated `application.yaml` edit as an unstaged hunk.
- [ ] Run the focused Backend tests and confirm they pass.

### Task 3: Remove Frontend Daily Quota Behavior

**Files:**
- Modify: `src/components/tarot-experience.tsx`
- Modify: `src/components/tarot-experience.test.tsx`
- Modify: `src/app/api/readings/route.test.ts`
- Modify: any additional tests discovered by the quota/payment reference scan.

**Interfaces:**
- Reading errors use the existing generic backend message/fallback path.
- No status 429 has a daily-free-reading-specific message.

- [ ] Update tests to require generic reading terminology and generic handling for a 429 response; run focused tests and confirm the old mapping fails the new expectation.
- [ ] Remove the 429 quota message and rename free-reading test descriptions/fixtures that are part of the active reading contract.
- [ ] Run focused Front tests and confirm they pass.

### Task 4: Update Product And Backend Specifications

**Files:**
- Modify: `D:/GitHub/MYEONGRO/docs/mvp-requirements.md`
- Modify: `D:/GitHub/MYEONGRO/docs/spring-backend-api-db-spec.md`

**Interfaces:**
- Defines one authenticated reading model with no payment, paid tier, purchase, quota event, daily cap, or payment roadmap contract.

- [ ] Replace active “free reading” terminology with “reading” where it describes the core flow.
- [ ] Remove payment/purchase/quota tables, functions, errors, retention guards, implementation order, and future payment API statements.
- [ ] State that the current service has no payment or paid-tier model and that monetization is not represented in the reading persistence contract.
- [ ] Search both documents for obsolete identifiers and resolve every active-contract occurrence.

### Task 5: Verify, Commit, And Request Review

**Files:**
- All files changed by Tasks 1-4.

- [ ] Run `git diff --check` in Backend and Front.
- [ ] Run one full Backend Gradle test suite and one full Front test/typecheck/lint verification according to repository scripts.
- [ ] Confirm removed active identifiers remain only in historical V1-V7 migrations or migration-removal assertions.
- [ ] Commit Backend and Front changes separately, staging only milestone-owned hunks.
- [ ] Send one cross-repository review request to MYEONGRO Review Desk with request thread ID `019ec06b-0b08-7471-97ac-e01c5c5c3a03`.
- [ ] Resolve any changes-requested findings on the same branches and re-request review.
- [ ] After approval, write one milestone Notion report and present merge order, including the Front parent-branch dependency.

