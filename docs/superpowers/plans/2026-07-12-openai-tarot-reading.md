# OpenAI Tarot Reading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate validated three-card Korean tarot readings through OpenAI while keeping saju on the deterministic demo generator.

**Architecture:** A routing `ReadingGenerator` delegates tarot to a prompt-backed OpenAI generator and saju to the existing demo generator. The same router resolves kind-specific generation metadata so persistence records the provider actually used. The OpenAI adapter enforces strict JSON Schema and application-level semantic validation before existing completion functions persist JSONB.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Spring AI 1.1.7, Jackson, JUnit 5, AssertJ, Mockito, PostgreSQL JSONB.

## Global Constraints

- Tarot uses `classpath:prompts/tarot/major-arcana-3card-ko-v1.md` as UTF-8.
- Saju remains deterministic demo output.
- Tarot failures never fall back to demo output and remain existing 502 failures.
- `sections` contains exactly three items ordered as `과거`, `현재`, `조언`.
- `guidance` contains two or three non-blank items.
- Existing quota, idempotency, pending/completed/failed, retry, and PostgreSQL function boundaries remain unchanged.
- No Flyway migration is added because provider-specific result validation belongs before JSONB persistence.

---

### Task 1: Prompt-backed validated OpenAI tarot generator

**Files:**
- Modify: `src/main/java/com/myeongro/api/domain/reading/service/OpenAiReadingGenerator.java`
- Create: `src/main/java/com/myeongro/api/domain/reading/service/TarotReadingResultValidator.java`
- Modify: `src/test/java/com/myeongro/api/domain/reading/service/OpenAiReadingGeneratorTests.java`
- Create: `src/test/java/com/myeongro/api/domain/reading/service/TarotReadingResultValidatorTests.java`
- Modify: `src/main/resources/prompts/tarot/major-arcana-3card-ko-v1.md`

**Interfaces:**
- `TarotReadingResultValidator.validate(ReadingResult result)` returns normally only for a complete tarot response and throws `IllegalArgumentException` otherwise.
- `OpenAiReadingGenerator.generate(ReadingKind, String, Map<String,Object>)` returns a validated `ReadingResult` or throws `OpenAiReadingGenerationException`.

- [ ] **Step 1: Add failing validator tests**

Cover a valid three-section response, wrong section count, wrong heading order,
blank required strings, and guidance counts outside two through three.

- [ ] **Step 2: Run validator tests and confirm failure**

Run:

```powershell
.\gradlew.bat test --tests "*TarotReadingResultValidatorTests"
```

Expected: FAIL because `TarotReadingResultValidator` does not exist.

- [ ] **Step 3: Implement semantic validator**

Implement a focused class with this public contract:

```java
public final class TarotReadingResultValidator {
    public void validate(ReadingResult result) {
        // Require non-null/non-blank title, summary, disclaimer.
        // Require exactly three non-null sections with non-blank heading/body.
        // Require headings 0..2 to contain 과거, 현재, 조언 respectively.
        // Require two or three non-blank guidance values.
    }
}
```

- [ ] **Step 4: Update OpenAI generator tests before implementation**

Require the captured system message to contain text loaded from the classpath
prompt. Assert `sections.minItems=3`, `sections.maxItems=3`,
`guidance.minItems=2`, and `guidance.maxItems=3`. Assert malformed but
deserializable output maps to `OpenAiReadingGenerationException`.

- [ ] **Step 5: Run OpenAI generator tests and confirm failure**

Run:

```powershell
.\gradlew.bat test --tests "*OpenAiReadingGeneratorTests"
```

Expected: FAIL because the old constructor uses a hard-coded prompt and weak
array bounds.

- [ ] **Step 6: Load the prompt and enforce the response contract**

Inject the configured Spring `Resource`, load it with UTF-8, use it as the
system message, set exact JSON Schema array bounds, deserialize the response,
and invoke `TarotReadingResultValidator`. Keep provider, parsing, empty output,
and validation failures behind `OpenAiReadingGenerationException`.

Update the prompt JSON example to show all three sections and two guidance
items, matching the Schema.

- [ ] **Step 7: Run Task 1 tests**

Run:

```powershell
.\gradlew.bat test --tests "*OpenAiReadingGeneratorTests" --tests "*TarotReadingResultValidatorTests"
```

Expected: PASS.

- [ ] **Step 8: Commit Task 1**

```powershell
git add src/main/java/com/myeongro/api/domain/reading/service/OpenAiReadingGenerator.java src/main/java/com/myeongro/api/domain/reading/service/TarotReadingResultValidator.java src/test/java/com/myeongro/api/domain/reading/service/OpenAiReadingGeneratorTests.java src/test/java/com/myeongro/api/domain/reading/service/TarotReadingResultValidatorTests.java src/main/resources/prompts/tarot/major-arcana-3card-ko-v1.md
git commit -m "feat: generate validated tarot readings with OpenAI"
```

---

### Task 2: Kind routing and accurate generation metadata

**Files:**
- Create: `src/main/java/com/myeongro/api/domain/reading/service/ReadingGeneratorRouter.java`
- Create: `src/main/java/com/myeongro/api/domain/reading/service/ReadingGenerationMetadataResolver.java`
- Modify: `src/main/java/com/myeongro/api/domain/reading/service/DemoReadingGenerator.java`
- Modify: `src/main/java/com/myeongro/api/domain/reading/service/OpenAiReadingGenerator.java`
- Modify: `src/main/java/com/myeongro/api/domain/reading/service/ReadingCreationService.java`
- Modify: `src/main/java/com/myeongro/api/domain/reading/service/ReadingGenerationMetadataConfig.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/java/com/myeongro/api/domain/reading/service/ReadingGeneratorBeanSelectionTests.java`
- Modify: `src/test/java/com/myeongro/api/domain/reading/service/ReadingCreationServiceTests.java`

**Interfaces:**
- `ReadingGeneratorRouter` implements `ReadingGenerator` and delegates by `ReadingKind`.
- `ReadingGenerationMetadataResolver.resolve(ReadingKind kind)` returns the provider, model, and prompt version used for reservation records.

- [ ] **Step 1: Write failing routing and metadata tests**

Assert tarot calls only the OpenAI delegate, saju calls only the demo delegate,
tarot metadata is `openai` with the configured model/version, and saju metadata
is `demo` with deterministic demo identifiers.

- [ ] **Step 2: Run routing tests and confirm failure**

Run:

```powershell
.\gradlew.bat test --tests "*ReadingGeneratorBeanSelectionTests" --tests "*ReadingCreationServiceTests"
```

Expected: FAIL because metadata and generator selection are currently global.

- [ ] **Step 3: Implement routing and metadata resolution**

Make both concrete generators injectable delegates and expose one primary
router as the `ReadingGenerator` consumed by `ReadingCreationService`. Replace
the single `ReadingGenerationMetadata` dependency in that service with
`ReadingGenerationMetadataResolver`, resolving metadata after `ReadingKind`
validation and before creating a pending record.

Add an explicit tarot prompt version property:

```yaml
app:
  reading:
    prompts:
      tarot: classpath:prompts/tarot/major-arcana-3card-ko-v1.md
      tarot-version: major-arcana-3card-ko-v1
```

- [ ] **Step 4: Run Task 2 tests**

Run:

```powershell
.\gradlew.bat test --tests "*ReadingGeneratorBeanSelectionTests" --tests "*ReadingCreationServiceTests"
```

Expected: PASS.

- [ ] **Step 5: Commit Task 2**

```powershell
git add src/main/java/com/myeongro/api/domain/reading/service src/test/java/com/myeongro/api/domain/reading/service src/main/resources/application.yaml
git commit -m "refactor: route reading generation by kind"
```

---

### Task 3: Milestone verification and live OpenAI smoke

**Files:**
- Modify only if a verified defect is found during smoke testing.

**Interfaces:**
- Uses the public `POST /api/readings` flow and existing persistence completion paths.
- Produces one completed tarot result with three sections and two or three guidance items.

- [ ] **Step 1: Run focused reading test package**

```powershell
.\gradlew.bat test --tests "com.myeongro.api.domain.reading.*"
```

Expected: PASS.

- [ ] **Step 2: Run the milestone's one full backend verification**

```powershell
.\gradlew.bat test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run one controlled live OpenAI smoke**

Start the configured application with PostgreSQL and Redis available, submit a
valid three-card tarot request through the existing API after satisfying its
session and consent requirements, and verify:

- the HTTP response is successful;
- the response contains exactly three sections in `과거`, `현재`, `조언` order;
- guidance contains two or three non-blank values;
- the completed user reading or guest cache stores the same result JSON;
- generation metadata records provider `openai`, the configured model, and
  prompt version `major-arcana-3card-ko-v1`.

If the live call fails, inspect provider status without logging the API key,
fix only verified implementation defects, rerun related tests, then repeat one
smoke request.

- [ ] **Step 4: Commit any smoke-only fix**

If no code changes were needed, skip this commit. Otherwise stage only the
verified fix and its regression test and commit with a specific `fix:` message.

- [ ] **Step 5: Request Review Desk review**

Send the repository, branch and HEAD commit, goal, change summary, exact test
commands, live smoke result, persistence boundary decision, and request thread
ID `019ec06b-0b08-7471-97ac-e01c5c5c3a03` to Review Desk thread
`019ebac7-a70b-7d72-a8d1-af39af21a3fb` using the required template.
