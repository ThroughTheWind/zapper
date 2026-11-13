---
id: WI-004
title: Guide pairing and keep credentials safe
type: feature
status: planned
branch:
created: 2026-11-13
updated: 2026-11-13
related: [WI-002, SPEC-F2, SPEC-AC-3, SPEC-AC-5, SPEC-NFR-6, F-001]
epic:
children: []
---

## Proposal (rationale)

First pairing must succeed on the first try (US-3) and never be needed again (US-4). Today tokens are stored in plain JSON and the TV's certificate is not pinned ([F-001](../FINDINGS.md)).

Phase 1 — Basic Samsung remote ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- Shared pairing UI with the states connecting → awaiting TV approval → paired / denied / timed out, and a PIN step for adapters that need it (FR-2.1, FR-2.2).
- Tokens and the TV's certificate pin are encrypted at rest with an Android Keystore key (FR-2.3, NFR-6).
- The Samsung adapter pins the TV certificate on first pairing and refuses a different one afterwards (closes F-001).
- A rejected token re-runs pairing automatically with an explanation (FR-2.4).

### Impacts

- `core/data` (encrypted credential store replacing the plain `tokens` map), `adapters/samsung` (`TvTls` becomes trust-on-first-use), `app` (pairing screens).
- Migration: existing plain tokens from scaffold builds are dropped; users pair once more.

### Approach

- UI follows [PairingFlow](../../design/components/PairingFlow/README.md), [PairingProgress](../../design/components/PairingProgress/README.md) and [Dialog](../../design/components/Dialog/README.md).
- AES-GCM key in the Android Keystore, ciphertext stored through DataStore. No third-party crypto library (ADR-0003).

### Acceptance criteria / tests

- AC-3: first connection shows the TV prompt; after accepting, the app reaches Connected and the token is saved.
- AC-5: denying the prompt shows a clear error with a retry action.
- Unit test: a different certificate after pairing is refused; a rejected token triggers re-pairing.

### Out of scope

- PIN-based pairing UI is built but only exercised by the fake adapter until a PIN brand ships.

## Execution

## Review
