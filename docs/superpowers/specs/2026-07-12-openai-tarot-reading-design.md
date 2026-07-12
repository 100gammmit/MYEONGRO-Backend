# OpenAI Tarot Reading Design

## Goal

Generate real Korean three-card Major Arcana readings through OpenAI while
keeping saju on the deterministic demo generator until tarot is stabilized.

## Scope

- Load the tarot system prompt from
  `classpath:prompts/tarot/major-arcana-3card-ko-v1.md` as UTF-8.
- Route tarot generation to OpenAI and saju generation to the existing demo
  implementation.
- Use the configured OpenAI model and output-token limit.
- Require exactly three reading sections and two or three guidance items.
- Reject structurally or semantically invalid model output.
- Preserve the existing pending, completed, failed, quota, idempotency, and
  retry persistence boundaries.
- Return the existing 502 response when OpenAI generation fails. Do not fall
  back to demo output and do not add automatic retries in this milestone.

## Architecture

`ReadingCreationService` continues to depend on one `ReadingGenerator`. A
kind-routing generator delegates tarot requests to an OpenAI tarot generator
and saju requests to `DemoReadingGenerator`. This avoids enabling OpenAI for
saju merely because tarot is enabled.

The tarot generator owns four responsibilities:

1. Load and retain the configured classpath prompt.
2. Serialize the validated question and canonical card input as the user
   message.
3. Request strict JSON Schema output from OpenAI.
4. Deserialize and validate the returned `ReadingResult` before it reaches a
   completion repository.

## Response Contract

The OpenAI response keeps the existing shape:

```json
{
  "title": "string",
  "summary": "string",
  "sections": [
    { "heading": "과거 - 카드 이름", "body": "string" },
    { "heading": "현재 - 카드 이름", "body": "string" },
    { "heading": "조언 - 카드 이름", "body": "string" }
  ],
  "guidance": ["string", "string"],
  "disclaimer": "string"
}
```

The JSON Schema requires all five properties, rejects unknown properties,
requires exactly three sections, and requires two or three guidance items.
Every string must be non-empty.

Application validation additionally requires section headings in this order:

1. `과거`
2. `현재`
3. `조언`

Any provider error, empty response, JSON parsing error, or contract violation is
mapped to `OpenAiReadingGenerationException`. Existing service code records the
pending reading as failed and returns 502 through the controller.

## Configuration And Metadata

- `app.reading.openai.model` selects the OpenAI model.
- `app.reading.openai.max-output-tokens` limits generated output.
- `app.reading.prompts.tarot` selects the tarot prompt resource.
- generation metadata records provider `openai` and the active model for tarot.
- saju generation metadata remains `demo` until its own OpenAI milestone.
- the tarot prompt version is derived from an explicit configuration value,
  not silently inferred from the filename.

Because metadata differs by reading kind, metadata selection must use the same
kind routing as generation.

## Persistence Boundary

No Flyway migration is required. `readings.result` and
`guest_reading_cache.result` already accept JSONB, while PostgreSQL completion
functions own atomic state transitions. Model-output validation remains in the
application adapter before either completion function is called.

This avoids duplicating tarot-specific response rules in PostgreSQL and leaves
room for a different saju response contract later.

## Verification

- Unit tests for prompt loading, OpenAI request options, exact collection
  bounds, semantic section validation, and stable exception mapping.
- Routing tests proving tarot uses OpenAI while saju stays on demo.
- Reading creation tests proving invalid generation terminates in failed state.
- One full backend test run at milestone completion.
- One explicitly controlled live API smoke call using the configured project
  key, followed by verification that a valid result is persisted.

## Deferred Work

- Saju OpenAI generation.
- Automatic retries, backoff, and retry UX.
- Streaming responses.
- Database-level validation of provider-specific JSON result shapes.
