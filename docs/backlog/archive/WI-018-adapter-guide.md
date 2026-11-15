---
id: WI-018
title: Write the guide for adding a TV brand
type: docs
status: done
branch: claude/project-thread-i8pmcn
created: 2026-11-13
updated: 2026-11-15
related: [WI-002, SPEC-FR-11.4, SPEC-AC-20]
epic:
children: []
---

## Proposal (rationale)

Contributors should be able to stub a new brand adapter from `docs/adapters.md` alone, with Samsung and the fake adapter as references (US-19).

Phase 4 — Release 1.0 ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- `docs/adapters.md` explains how to add a brand (SPEC FR-11.4), with Samsung as the reference and
  the fake adapter for features without a network.
- A reviewer can follow it to stub a new brand adapter (SPEC AC-20, US-19).
- The guide cannot drift from the contract silently.

### Impacts

- New `docs/adapters.md`; CONTRIBUTING, README and the AGENTS routing table point to it.
- `core/remote` tests: an example adapter and its test.

### Approach

- The stub the guide starts from is real code: `ExampleAdapter.kt` and `ExampleAdapterTest.kt` in
  `core/remote`'s tests, run against `TvSessionContract` in CI. A contract change that breaks the
  example breaks the build, which is what keeps the guide honest; a code block in Markdown would not.
- The guide links to the files rather than copying them, and covers what the code cannot say:
  module rules, key mapping policy, error mapping, testing against the real protocol, registration
  and what to put in the pull request.

### Acceptance criteria / tests

- `ExampleAdapterTest` passes the shared contract plus pairing once, a refused pairing and a
  rejected token.
- `node .ai/rungs.mjs check`: every link in the guide resolves.
- A reviewer reads the guide and can name every file to create or change for a new brand.

### Out of scope

- A real second brand (After 1.0 in the [roadmap](../../ROADMAP.md)).
- Generating a module from a template script.

## Execution

- 2026-11-15: built as planned on `claude/project-thread-i8pmcn`.

## Review

- Example adapter test: runs in CI with `:core:remote:test`.
- Links: `node .ai/rungs.mjs check` passes.
- Reviewer walkthrough: **open** for Antoine's review of the pull request.
- 2026-11-15: closed as `done` by Antoine, who asked to move every item in review to done. The
  branch is merged and shipped in a release. Any criterion marked open above was not demonstrated
  separately before closing.
