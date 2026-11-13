---
id: WI-001
title: Prove the Samsung path on the reference TV
type: spike
status: planned
branch:
created: 2026-11-13
updated: 2026-11-13
related: [SPEC-A2, SPEC-A4, ADR-0002]
epic:
children: []
---

## Proposal (rationale)

Every Samsung item rests on protocol behaviour that has only been read about. The [research](../../research/tv-remote-protocols.md#2-samsung-tizen-the-first-target) leaves open questions specific to the reference TV (UE75RU7020, 2019, firmware T-MSLDEUC-1510.0): whether `PowerState` is reported, whether `ed.installedApp.get` returns apps, and which key codes the set accepts. Ten minutes against the real TV settles them before code depends on them.

Phase 1 — Basic Samsung remote ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- Run the scaffold debug APK against the reference TV: SSDP `M-SEARCH`, `GET http://IP:8001/api/v2/`, pairing on `wss://IP:8002`, then every key in `RemoteKey`.
- Record the `/api/v2/` response (model, `wifiMac`, `PowerState`), the `ms.channel.connect` payload, and the `ed.installedApp.get` answer.
- Note any key the TV ignores and the codes for `SETTINGS` and `PLAY_PAUSE`, which are unmapped today.

### Impacts

- `adapters/samsung` key map (`SamsungKeys.kt`): fill or correct codes.
- Captured messages become fixtures for the Samsung tests (`adapters/samsung/src/test/resources/`).
- Spec assumptions A2 and A4 get their final status.

### Approach

- Antoine runs the debug APK from CI on his phone; findings go into this item's Execution section.
- A small debug-only screen or log dump may be added to capture raw messages; it is removed or kept behind the debug build.

### Acceptance criteria / tests

- Pairing succeeds and the token is reused after an app restart.
- Each `RemoteKey` is marked works / ignored / unknown in the Execution section.
- `/api/v2/` and `ed.installedApp.get` responses are saved as test fixtures.

### Out of scope

- Wake-on-LAN and text input are verified in WI-010 and WI-008, not here.

## Execution

## Review
