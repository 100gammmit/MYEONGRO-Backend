# Single-Source Tarot Prompt Config Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `src/main/resources/application.yaml` the only place that selects versioned tarot prompt resources while preserving filename-derived runtime prompt version tracking.

**Architecture:** Production continues to inject six prompt resources from the main YAML and `TarotPromptCatalog` derives prompt versions from their filenames. Tests stop loading production prompt versions and instead use a fixed, unversioned fixture set under `src/test/resources/prompts/tarot/fixtures`, so production prompt changes do not require Java or test YAML edits.

**Tech Stack:** Java 21, Spring Boot, Spring Resource abstraction, JUnit 5, AssertJ, Gradle

## Global Constraints

- Keep the current branch `refactor/decouple-prompt-tests`.
- Keep `generation_records.prompt_version` and filename-derived version composition unchanged.
- Do not add automatic latest-version discovery.
- Do not change production prompt Markdown content.
- Concrete versioned tarot prompt paths may appear in `src/main/resources/application.yaml` only.
- Follow `AGENTS.md`: work inline, run related tests during implementation, run the full suite once at the milestone boundary, and request Review Desk approval before completion.

---

### Task 1: Add stable, unversioned tarot test fixtures

**Files:**
- Create: `src/test/resources/prompts/tarot/fixtures/common.md`
- Create: `src/test/resources/prompts/tarot/fixtures/cards.md`
- Create: `src/test/resources/prompts/tarot/fixtures/spreads/daily-one-card.md`
- Create: `src/test/resources/prompts/tarot/fixtures/spreads/mind-three-card.md`
- Create: `src/test/resources/prompts/tarot/fixtures/spreads/relationship-three-card.md`
- Create: `src/test/resources/prompts/tarot/fixtures/spreads/choice-five-card.md`

**Interfaces:**
- Consumes: `TarotPromptCatalog(Resource common, Resource cards, Resource daily, Resource mind, Resource relationship, Resource choice)` and its existing `- major-* / ` card-entry parser contract.
- Produces: six stable classpath resources rooted at `prompts/tarot/fixtures` that never encode a production prompt version.

- [ ] **Step 1: Confirm the current test code contains production version references**

Run:

```powershell
rg -n 'common-ko-v[0-9]+|major-arcana-ko-v[0-9]+|spreads/.+-ko-v[0-9]+' src/test
```

Expected: matches in test YAML and Java tests. This establishes the structural failure being removed.

- [ ] **Step 2: Create the common fixture**

Create `common.md` with non-production test-only content:

```markdown
Test-only tarot prompt common instructions.
Use only the selected card entries and spread contract supplied below.
```

- [ ] **Step 3: Create the card fixture**

Create `cards.md` with the exact 22 canonical IDs, one entry per line, and the parser-required ` / ` separator:

```markdown
Test-only major arcana entries:
- major-00-fool / test meaning
- major-01-magician / test meaning
- major-02-high-priestess / test meaning
- major-03-empress / test meaning
- major-04-emperor / test meaning
- major-05-hierophant / test meaning
- major-06-lovers / test meaning
- major-07-chariot / test meaning
- major-08-strength / test meaning
- major-09-hermit / test meaning
- major-10-wheel-of-fortune / test meaning
- major-11-justice / test meaning
- major-12-hanged-man / test meaning
- major-13-death / test meaning
- major-14-temperance / test meaning
- major-15-devil / test meaning
- major-16-tower / test meaning
- major-17-star / test meaning
- major-18-moon / test meaning
- major-19-sun / test meaning
- major-20-judgement / test meaning
- major-21-world / test meaning

All test cards are upright.
```

- [ ] **Step 4: Create the four spread fixtures**

Use these exact minimal contents:

`daily-one-card.md`:

```markdown
Test spread: daily_one_card
Required positions in order: today
```

`mind-three-card.md`:

```markdown
Test spread: mind_three_card
Required positions in order: emotion, underlying_need, self_action
```

`relationship-three-card.md`:

```markdown
Test spread: relationship_three_card
Required positions in order: my_heart, relationship_flow, check_point
```

`choice-five-card.md`:

```markdown
Test spread: choice_five_card
Required positions in order: desire, fear, core_value, option_a, option_b
```

- [ ] **Step 5: Check fixture structure**

Run:

```powershell
$ids = rg -o 'major-[0-9]{2}-[a-z-]+' src/test/resources/prompts/tarot/fixtures/cards.md
$ids.Count
$ids | Sort-Object -Unique | Measure-Object | Select-Object -ExpandProperty Count
```

Expected: both counts are `22`.

---

### Task 2: Redirect all test configuration to stable fixtures

**Files:**
- Modify: `src/test/resources/application.yaml`
- Modify: `src/test/java/com/myeongro/api/domain/reading/service/OpenAiReadingGeneratorTests.java`
- Modify: `src/test/java/com/myeongro/api/domain/reading/service/ReadingGeneratorBeanSelectionTests.java`
- Test: `src/test/java/com/myeongro/api/domain/reading/service/OpenAiReadingGeneratorTests.java`
- Test: `src/test/java/com/myeongro/api/domain/reading/service/ReadingGeneratorBeanSelectionTests.java`
- Test: `src/test/java/com/myeongro/api/global/config/ApplicationYamlContractTests.java`
- Test: `src/test/java/com/myeongro/api/domain/reading/service/ReadingGenerationMetadataResolverTests.java`

**Interfaces:**
- Consumes: the six fixture resources produced by Task 1.
- Produces: test contexts and manually constructed catalogs that are independent of all production prompt filenames.

- [ ] **Step 1: Replace test YAML prompt paths**

Set the six values under `app.reading.prompts.tarot` to:

```yaml
common: classpath:prompts/tarot/fixtures/common.md
cards: classpath:prompts/tarot/fixtures/cards.md
spreads:
  daily-one-card: classpath:prompts/tarot/fixtures/spreads/daily-one-card.md
  mind-three-card: classpath:prompts/tarot/fixtures/spreads/mind-three-card.md
  relationship-three-card: classpath:prompts/tarot/fixtures/spreads/relationship-three-card.md
  choice-five-card: classpath:prompts/tarot/fixtures/spreads/choice-five-card.md
```

- [ ] **Step 2: Replace `ReadingGeneratorBeanSelectionTests` property values**

Keep the six property keys unchanged and replace only their values with the same fixture paths from Step 1. Do not add version assertions.

- [ ] **Step 3: Replace `OpenAiReadingGeneratorTests.catalog()` resources**

Construct the catalog with:

```java
return new TarotPromptCatalog(
	new ClassPathResource("prompts/tarot/fixtures/common.md"),
	new ClassPathResource("prompts/tarot/fixtures/cards.md"),
	new ClassPathResource("prompts/tarot/fixtures/spreads/daily-one-card.md"),
	new ClassPathResource("prompts/tarot/fixtures/spreads/mind-three-card.md"),
	new ClassPathResource("prompts/tarot/fixtures/spreads/relationship-three-card.md"),
	new ClassPathResource("prompts/tarot/fixtures/spreads/choice-five-card.md")
);
```

- [ ] **Step 4: Verify all concrete production version references are gone from test code and test YAML**

Run:

```powershell
rg -n 'common-ko-v[0-9]+|major-arcana-ko-v[0-9]+|spreads/.+-ko-v[0-9]+' src/test
```

Expected: no matches and exit code `1` from `rg`.

- [ ] **Step 5: Run related tests**

Run:

```powershell
.\gradlew.bat test --tests "com.myeongro.api.domain.reading.service.OpenAiReadingGeneratorTests" --tests "com.myeongro.api.domain.reading.service.ReadingGeneratorBeanSelectionTests" --tests "com.myeongro.api.global.config.ApplicationYamlContractTests" --tests "com.myeongro.api.domain.reading.service.ReadingGenerationMetadataResolverTests"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit the implementation**

```powershell
git add src/test/resources/application.yaml src/test/resources/prompts/tarot/fixtures src/test/java/com/myeongro/api/domain/reading/service/OpenAiReadingGeneratorTests.java src/test/java/com/myeongro/api/domain/reading/service/ReadingGeneratorBeanSelectionTests.java
git commit -m "test: isolate tarot prompt fixtures"
```

---

### Task 3: Verify the single source and complete the review cycle

**Files:**
- Verify: `src/main/resources/application.yaml`
- Verify: all files under `src/main` and `src/test`
- Report: Notion milestone page after approval

**Interfaces:**
- Consumes: the production YAML contract and stable test fixture structure from Tasks 1-2.
- Produces: a reviewed merge-candidate commit where only the main YAML selects production prompt versions.

- [ ] **Step 1: Verify concrete version references are confined to production YAML**

Run:

```powershell
rg -n 'common-ko-v[0-9]+|major-arcana-ko-v[0-9]+|spreads/.+-ko-v[0-9]+' src/main src/test --glob '*.java' --glob '*.yaml'
```

Expected: exactly six matches, all in `src/main/resources/application.yaml`.

- [ ] **Step 2: Verify version derivation remains dynamic**

Inspect `TarotPromptCatalog.version(TarotSpreadType)` and confirm it still joins `common.version()`, `cards.version()`, and the selected spread version derived by `read(Resource)` from each resource filename. No production code change is expected.

- [ ] **Step 3: Run whitespace validation**

Run:

```powershell
git diff --check
```

Expected: no output and exit code `0`.

- [ ] **Step 4: Run the full milestone validation once**

Run:

```powershell
.\gradlew.bat test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Request Review Desk review**

Commit any remaining plan bookkeeping if needed, then send the required review template to thread `019ebac7-a70b-7d72-a8d1-af39af21a3fb` with request thread ID `019f8fb2-399d-77a0-a160-cc42e7d75ffa`, the current branch and HEAD, actual validation commands, and the invariant that runtime prompt version tracking remains filename-derived.

- [ ] **Step 6: Resolve review findings or finish reporting**

If the result is `changes-requested`, fix it on the same branch, rerun related validation, commit, and request re-review. If the result is `approved`, create one Notion milestone report containing the goal, final scope, design decision, approved commit, validation results, remaining issues, and next milestone condition. Do not merge without explicit user authorization.
