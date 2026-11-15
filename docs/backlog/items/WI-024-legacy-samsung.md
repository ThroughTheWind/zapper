---
id: WI-024
title: Support Samsung TVs from 2008 to 2013
type: feature
status: review
branch: claude/project-thread-d061jb
created: 2026-11-15
updated: 2026-11-15
related: [WI-003, WI-004, ADR-0002, SPEC-NFR-8]
epic:
children: []
---

## Proposal (rationale)

Zapper only talks to Samsung TVs from 2016 on (SPEC NFR-8). Issue #33 asks for the older ones. The
2008–2013 sets (series A to F) still work as TVs and are common as second sets; their remote
protocol is a small TCP protocol on port 55000 with an on-TV "Allow" prompt (research §2.1, §2.9).
The 2014–2015 sets (H, some J) need an encrypted PIN handshake that only one open-source project
implements, under EPL-2.0.

## Decision

`accepted` — 2026-11-15, Antoine, by asking for a thread to add support for older Samsung models
(issue #33). Scoped here to 2008–2013; 2014–2015 is out of scope (see below).

## Plan

### Requirements

- A 2008–2013 Samsung TV can be added by IP, or found by SSDP when its answer names Samsung.
- Pairing shows the TV's "Allow" prompt once; later connections reuse the same id and skip it.
- A refused prompt, an unanswered prompt and a device removed in the TV's settings each give the
  existing `TvError` the app already explains (`PairingRefused`, `PairingTimedOut`, `TokenRejected`).
- Keys use the legacy codes; codes no legacy implementation lists map to `null`.
- TVs saved before this change keep the Tizen session.

### Impacts

- `adapters/samsung` only: same adapter id `samsung`, so no app, `core` or settings change. The
  session chosen depends on a new `protocol=legacy` device property set at discovery.
- SPEC NFR-8, research §2.1 and a new §2.9, README, CHANGELOG.

### Approach

- Inside the Samsung adapter rather than a new module: same brand in the UI, same SSDP, and
  ADR-0002 forbids one adapter depending on another, so a separate module would duplicate SSDP.
- `LegacyProtocol` encodes and decodes packets with no I/O; `LegacySamsungSession` runs one TCP
  socket with a reader thread. The TV has no token, so the random id the phone sends is stored as
  the token: forgetting it (re-pair) pairs again under a new id.
- Probe: the Tizen info endpoint first, then an open port 55000. Discovery only tries port 55000 for
  SSDP answers that name Samsung, so another brand's renderer is not claimed.
- Written from the protocol as documented by MIT-licensed projects (samsungctl, samsung-tv-control);
  no openHAB (EPL) code was read or copied.

### Acceptance criteria / tests

- `LegacyProtocolTest`: key and handshake framing byte for byte; every TV answer recognised.
- `LegacySamsungSessionTest` extends `TvSessionContract` against a fake TV on localhost: pairing
  once then reusing the id, a refused prompt, a rejected saved id, an unanswered prompt, a TV that
  closes the handshake (a 2014–2015 model), nothing listening, legacy key codes, a dropped connection.
- `LegacyDiscoveryTest`: probe by IP, SSDP filtered to Samsung, session routing, power state, and a
  TV saved before this change still getting the Tizen session.
- On a real 2008–2013 TV (not available to Antoine, whose TV is a 2019 RU7020): pairing, each key,
  reconnect after turning the TV off and on.

### Out of scope

- 2014–2015 TVs (encrypted "SmartView 1" pairing on port 8080). They now fail with a clear
  "not supported yet" message instead of a timeout. Issue #33 stays open for them; no item yet.
- Text input and app launch on legacy TVs: not declared, since no implementation confirms them.
- Turning a legacy TV on: it drops off the network in standby.
- Finding a legacy TV by the subnet sweep: the sweep looks for the Tizen info port only.

## Execution

Branch `claude/project-thread-d061jb` (the session's branch, not `feature/WI-024-…`). The Android
build could not run in the session (Google's Maven repository was blocked); `:adapters:samsung:test`
and spotless ran in a JVM-only build of `core/model`, `core/remote` and `adapters/samsung`. CI runs
the full build.

## Review

`:adapters:samsung:test` passes, including the three new test classes above. Real-device testing is still open.
