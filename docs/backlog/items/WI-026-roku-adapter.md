---
id: WI-026
title: Support Roku TVs and Roku players
type: feature
status: review
branch: claude/project-thread-7m3mmq
created: 2026-11-15
updated: 2026-11-15
related: [ADR-0002, ADR-0003, WI-025]
epic:
children: []
---

## Proposal (rationale)

Issue [#26](https://github.com/ThroughTheWind/zapper/issues/26) asks for Roku: Roku sticks and
boxes, and Roku TVs sold by TCL, Hisense, Sharp and others. Roku's External Control Protocol is the
only officially documented TV protocol in the research (§5) and needs no pairing, so it is the
cheapest brand to add and a large share of TVs sold in North America.

## Decision

`accepted` — 2026-11-15, Antoine, by asking to "tackle #26 now".

## Plan

### Requirements

- A new `adapters/roku` module, plain Kotlin/JVM, depending on `:core:remote` and OkHttp only.
- Discovery over SSDP `roku:ecp`, and add by IP, both confirmed by `/query/device-info`.
- Keys from Roku's documented list, with press and release for holding a key; undocumented keys
  map to `null`. Power, volume, channels and HDMI inputs only on a Roku TV.
- Text one `Lit_` keypress per character, then Enter.
- The Roku's app list and launching apps; `KnownApps` mapped to Roku channel ids.
- Power on: `PowerOn` (Fast TV start), then Wake-on-LAN; power state from `power-mode`.
- A Roku whose "Control by mobile apps" setting blocks phones fails with an explanation naming the
  setting, and is not retried.

### Impacts

- `core/remote`: a new `TvError.ControlDisabled(message)`. Not retried by `TvConnection`, which
  only retries `Unreachable` and `ConnectionLost`. The pairing screen gives it its own title.
- `app`: the adapter registered in `AppContainer`; no other UI change (ADR-0002).
- Docs: research §5, `docs/adapters.md`, the adapters rule, README, CHANGELOG.
- Risk: written from documentation only. Nobody on the project owns a Roku.

### Approach

- ECP over OkHttp, which the app already ships. XML answers are flat, so a small regex reader
  replaces an XML parser, which also avoids XML entity expansion entirely. Android's
  `DocumentBuilderFactory` does not take the features that would make it safe.
- No socket to hold: `connect()` reads device-info and the app list (which also reveals a blocked
  setting); a key that fails to reach the Roku marks the session lost, so the app reconnects.
- POWER is a toggle in Zapper and on/off on Roku, so the session reads `power-mode` first.
- SSDP is copied from the Samsung adapter, since adapters share nothing outside core; moving it to
  `core/remote` is finding F-004.

### Acceptance criteria / tests

- `RokuSessionTest` extends `TvSessionContract` against a fake Roku on localhost, and covers: no
  pairing and nothing stored, keypress names, keydown/keyup, undocumented keys never sent, the
  power toggle, a player without TV keys, text encoding (space, `+`, `/`, accents, emoji), the
  app list without inputs, launching known and TV apps, an app not installed (by list and by 404),
  a blocked Roku at connect and mid-session, nothing listening, a Roku going away, and a non-Roku
  on port 8060.
- `RokuAdapterTest` covers device-info for a TV and a player, a non-Roku answer, probe,
  SSDP discovery de-duplication, power state, and power on by `PowerOn`, Wake-on-LAN, and not
  for a player or a blocked TV.
- On a real Roku (needs a tester, through a preview build, WI-025): discovery and add by IP, each
  key, holding a direction, typing in a search field, the app list and opening Netflix and
  YouTube, power off and on with and without Fast TV start, and the message with "Control by
  mobile apps" set to Limited.

### Out of scope

- App icons from `/query/icon/<id>`: `TvApp` has no icon field. Nothing deferred yet; a new item if
  testers ask.
- Live state (active app, media position): ECP has no events and polling is not needed by any
  screen today.
- Subnet sweep for networks that drop multicast, as Samsung has: add by IP covers it. Nothing
  deferred.
- Moving SSDP to `core/remote`: finding F-004.

## Execution

Branch `claude/project-thread-7m3mmq` (the project's session branch, rather than
`feature/WI-026-roku-adapter`). As for WI-023 and WI-024, this session cannot reach Google's Maven
repository, so the adapter and `core/remote` tests ran in a JVM-only Gradle build of
`core/model`, `core/remote`, `adapters/samsung` and `adapters/roku`, and ktlint 1.8.0 ran from its
CLI. The Android build, lint and `checkAdapterBoundaries` run in CI.

## Review

- Automated: 28 Roku tests pass (19 session including the 5 contract cases, 9 adapter);
  `core/remote` and Samsung tests still pass with the new error.
- Not yet checked on a real Roku: every item in the last acceptance bullet is open.
