# Guest Consent API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the guest consent status and acceptance API from Next.js/Supabase calls to Spring Boot.

**Architecture:** A Spring MVC controller resolves the signed guest cookie and delegates consent rules to a transactional service. A Spring Data JPA repository writes to the existing Supabase PostgreSQL schema without changing it.

**Tech Stack:** Java 21, Spring Boot MVC, Spring Data JPA, PostgreSQL, JUnit 5, MockMvc

---

### Task 1: Consent Domain Contract

**Files:**
- Create: `src/main/java/com/myeongro/api/domain/consent/ConsentDocumentType.java`
- Create: `src/main/java/com/myeongro/api/domain/consent/ConsentAcceptance.java`
- Create: `src/main/java/com/myeongro/api/domain/consent/ConsentStatus.java`
- Test: `src/test/java/com/myeongro/api/domain/consent/ConsentServiceTests.java`

- [ ] Write failing tests for required-document validation and status calculation.
- [ ] Run `gradlew.bat test --tests '*ConsentServiceTests'` and verify missing types fail.
- [ ] Implement the domain records and service contract.
- [ ] Re-run the focused tests and verify they pass.

### Task 2: PostgreSQL Persistence

**Files:**
- Create: `src/main/java/com/myeongro/api/domain/consent/ConsentRepository.java`
- Create: `src/main/java/com/myeongro/api/domain/consent/JdbcConsentRepository.java`
- Create: `src/main/java/com/myeongro/api/domain/consent/ConsentService.java`

- [ ] Add repository expectations to the service tests.
- [ ] Verify the tests fail because persistence coordination is absent.
- [ ] Implement current-version queries and conflict-safe inserts.
- [ ] Verify focused tests pass.

### Task 3: Guest HTTP API

**Files:**
- Create: `src/main/java/com/myeongro/api/domain/consent/ConsentController.java`
- Create: `src/main/java/com/myeongro/api/domain/consent/ConsentRequest.java`
- Test: `src/test/java/com/myeongro/api/domain/consent/ConsentControllerTests.java`

- [ ] Write failing MockMvc tests for GET cookie issuance/reuse and strict POST validation.
- [ ] Run the controller tests and confirm expected failures.
- [ ] Implement `GET /api/consents` and `POST /api/consents`.
- [ ] Run the controller tests and confirm the frontend-compatible JSON contract.

### Task 4: Verification

**Files:**
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/resources/application.yaml`

- [ ] Add the three current consent document versions to configuration.
- [ ] Run the full `gradlew.bat test` suite.
- [ ] Run `git diff --check`.
