# Consent JPA Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the consent JDBC adapter with Spring Data JPA while organizing the feature into controller, dto, entity, repository, and service packages.

**Architecture:** `Consent` becomes the persistence entity and `ConsentRepository` becomes a Spring Data repository. `ConsentService` queries current records, creates only missing current-version entities, and preserves existing acceptance timestamps.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA, Hibernate, JUnit 5, H2

---

### Task 1: JPA Persistence Contract

**Files:**
- Create: `src/test/java/com/myeongro/api/domain/consent/repository/ConsentRepositoryTests.java`
- Create: `src/main/java/com/myeongro/api/domain/consent/entity/Consent.java`
- Move: `ConsentDocumentType.java` to `entity`
- Replace: `repository/ConsentRepository.java`

- [ ] Write a `@DataJpaTest` that saves and finds guest consents.
- [ ] Run the focused test and verify it fails because no JPA entity/repository exists.
- [ ] Implement the entity and `JpaRepository`.
- [ ] Re-run the focused test and verify persistence passes.

### Task 2: Service and DTO Packages

**Files:**
- Move: `ConsentService.java` to `service`
- Move: `ConsentRequest.java` and `ConsentStatus.java` to `dto`
- Remove: `JdbcConsentRepository.java`
- Remove: `ConsentAcceptance.java`
- Modify: `src/test/java/com/myeongro/api/domain/consent/service/ConsentServiceTests.java`

- [ ] Change service tests to mock the Spring Data repository.
- [ ] Verify tests fail against the old service contract.
- [ ] Implement entity-based status lookup and missing-record `saveAll`.
- [ ] Re-run service tests.

### Task 3: Controller Package Integration

**Files:**
- Modify: `controller/ConsentController.java`
- Move: controller tests to the matching package.

- [ ] Update controller tests to the new DTO/entity package imports.
- [ ] Verify the controller contract remains unchanged.
- [ ] Update controller imports and response mapping.
- [ ] Run focused consent tests.

### Task 4: Full Verification

- [ ] Run `gradlew.bat test`.
- [ ] Run `git diff --check`.
- [ ] Confirm no `JdbcTemplate` or `JdbcConsentRepository` remains in consent code.
