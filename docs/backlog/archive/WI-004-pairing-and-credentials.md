---
id: WI-004
title: Guide pairing and keep credentials safe
type: feature
status: done
branch: feature/WI-004-pairing-and-credentials
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

Branch `feature/WI-004-pairing-and-credentials`, stacked on WI-003.

- `core/remote`: `CredentialStore` holds named secrets per TV (`read`, `write`, `remove`, `clear`), with `token`/`saveToken` helpers; `TvError.CertificateChanged`; `connectOrRepair` and `forgetAndConnect` hold the re-pair policy so it is tested on the JVM rather than inside the app; `InMemoryCredentialStore` test fixture.
- `core/data`: `KeystoreCipher` (AES-256-GCM, key in the Android Keystore, IV prepended); `ZapperRepository` stores each secret sealed and Base64-encoded under `secrets`. A secret that no longer decrypts reads as absent, so the TV pairs again. The old plain `tokens` field is no longer read. `allowBackup` was already false, so sealed secrets never leave the phone without their key.
- `adapters/samsung`: `PinningTrustManager` records the leaf certificate's SHA-256 on first use and refuses any other afterwards. The pin is saved only after the TV accepts the connection. Legacy `ws://` TVs have no certificate to pin.
- `adapters/fake`: a second simulated TV at `192.0.2.2` that asks for the code `1234`, so the PIN step can be used.
- Design system: `PairingHero`, `PairingSteps`, `PinEntry` and `PairingOutcome` in `components/PairingProgress.kt`.
- App: `PairingScreen` (connecting, approve on TV, code entry, paired with a `Success` haptic then the remote after 1 s, refused or timed out with a `Failure` haptic and "Try again"). Choosing a TV now goes through it. A saved TV that asks to pair again reopens it with the reason above the hero.

Deviation, with the reason: a rejected token re-pairs automatically, but a **changed certificate does not**. A different certificate can mean a different machine answering at the TV's address, so the flow explains it and waits for the user to tap "Pair again". Only the token is dropped on a rejected token; the certificate pin is kept, so the new pairing still has to reach the same TV.

## Review

- Unit tests, run locally on the JVM modules (51 tests, all passing): first pairing pins the certificate, the same certificate is accepted again, a different certificate fails with `CertificateChanged` and keeps the old pin (`SamsungPinningTest`, real TLS through MockWebServer); a rejected token is forgotten and pairing runs again while other secrets stay, a changed certificate is not re-trusted, `forgetAndConnect` pairs from scratch (`RepairTest`); the code TV accepts only its code (`FakeTvAdapterTest`).
- AC-3 (TV prompt, then Connected and the token saved): the token-saving half is covered by `SamsungSessionTest`; the on-screen flow is not yet checked on a device. The Android modules are verified by CI only (assemble and lint), not run.
- AC-5 (denying shows a clear error with retry): `PairingScreen` maps `PairingRefused` to "The TV said no", the Device Connection Manager path and "Try again". Not yet checked on a device.
- Encryption at rest uses the Android Keystore, which has no JVM implementation, so `KeystoreCipher` is not unit-tested; it needs a device check with the reference TV (WI-001).
