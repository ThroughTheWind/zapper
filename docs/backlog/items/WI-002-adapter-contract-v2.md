---
id: WI-002
title: Evolve the adapter contract and add a fake TV adapter
type: feature
status: planned
branch:
created: 2026-11-13
updated: 2026-11-13
related: [ADR-0002, SPEC-F11, SPEC-AC-18, SPEC-AC-19]
epic:
children: []
---

## Proposal (rationale)

The scaffold contract only covers connect and send. The spec (F11) and the [research](../../research/tv-remote-protocols.md#11-proposed-adapter-architecture-kotlin) need discovery, a manual-IP probe, pairing as a typed flow (confirm on TV, enter a PIN), press/release for hold-to-repeat, typed errors and capabilities that can grow after connecting. A fake adapter lets the UI be built and tested with no TV (AC-18).

Phase 1 — Basic Samsung remote ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- `TvAdapter` gains `discover(): Flow<DiscoveredTv>` and `probe(host): DiscoveredTv?`.
- `ConnectionState.AwaitingPairing` carries a `PairingRequest` (confirm on TV, or enter a code with a `submit`).
- `TvSession.capabilities` becomes a `StateFlow`; keys support click, press and release (`Capability.LONG_PRESS`).
- A typed `TvError` (unreachable, refused, token rejected, timeout, unsupported).
- `adapters/fake`: a scripted fake TV for debug builds and tests, registered only in the debug build.
- An architecture test fails the build if `app` or `core` references an adapter's types beyond registration (AC-19).

### Impacts

- `core/remote`, `core/model`, `adapters/samsung` (migrated to the new contract), `app` (registration).
- New module `adapters/fake`, `debugImplementation` only.
- ADR-0002 stays valid; this is its first concrete shape.

### Approach

- Follow §11 of the research, minus facets the MVP does not need yet (pointer, inputs): keep them out until a phase needs them.
- Keep adapters pure Kotlin/JVM; platform services (multicast lock, NSD) are passed in as small interfaces from `app`.

### Acceptance criteria / tests

- Unit tests for the registry, pairing state machine and macro/press timing against the fake adapter.
- The Samsung adapter passes the same contract test suite as the fake one.
- `./gradlew test lint` green; the debug app runs end to end on the fake adapter.

### Out of scope

- Other brands' adapters (v1.x). Pointer/touchpad facet (v1.1).

## Execution

## Review
