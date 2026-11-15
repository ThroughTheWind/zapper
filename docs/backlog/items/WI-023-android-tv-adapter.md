---
id: WI-023
title: Add the Android TV / Google TV adapter
type: feature
status: review
branch: claude/project-thread-s72b34
created: 2026-11-15
updated: 2026-11-15
related: [ADR-0002, ADR-0003, SPEC-F1, SPEC-F2]
epic:
children: []
---

## Proposal (rationale)

Android TV and Google TV run on more TVs and boxes than any other platform Zapper could add: Sony,
TCL, Hisense, Philips, Chromecast with Google TV, Nvidia Shield and many more. The research names it
the second adapter to build, the roadmap lists it first after 1.0, and it is voting issue #24.

## Decision

`accepted` — 2026-11-15, Antoine, by asking for Android TV support (issue #24).

## Plan

### Requirements

- A new `adapters/androidtv` module speaking the Android TV Remote protocol v2 (research §3): Polo
  pairing on port 6467 with the 6-character hex code the TV shows, then the remote on port 6466.
- Finds TVs by mDNS (`_androidtvremote2._tcp`) and by IP address; names them and reads their MAC
  from the TV's certificate.
- Pairs once: the phone's client certificate is the token; a TV that forgets it reports
  `TokenRejected`, so pairing re-runs. The TV's certificate is pinned on first use.
- Keys with long press, text input into the focused field, app shortcuts by deep link.
- Answers the TV's pings so the connection stays up.

### Impacts

- New module `adapters/androidtv`; one line each in `settings.gradle.kts`, `app/build.gradle.kts`
  and `ZapperApplication.kt`. No contract change in `core/remote`, no UI change.
- No new dependency (ADR-0003). The research suggested Wire or protobuf-lite and Bouncy Castle;
  the protocol uses about twenty protobuf fields and one certificate shape, so both are written
  against the JDK instead.

### Approach

- Hand-written protobuf wire format (`Proto.kt`) and a minimal DER writer for the self-signed
  client certificate (`ClientIdentity.kt`), both checked against the reference implementation
  (tronikos/androidtvremote2, Apache-2.0) in tests.
- Plain `SSLSocket`s with a key manager that always presents the client certificate, and a
  trust-on-first-use pinning trust manager like Samsung's (copied, since adapters may not depend on
  each other).
- mDNS by a unicast-response query from an ordinary port, so no socket joins the multicast group.
- Key codes are Android's public `KeyEvent` constants. Keys a box may not have (HDMI 1 to 4, Exit,
  channel list, previous channel) stay `null` until confirmed on a device.

### Acceptance criteria / tests

- `AndroidTvSessionTest` extends `TvSessionContract` against a fake TV on localhost that speaks the
  real protocol over TLS with client certificates: pairing with a typo caught locally, then reuse;
  a refused pairing; a code the TV rejects; a forgotten certificate (`TokenRejected`); a changed TV
  certificate (`CertificateChanged`); pings; long press; text; app links; a dropped connection.
- `AndroidTvProtocolTest` checks the pairing secret against a vector computed with the reference
  implementation, and the protobuf encoding byte for byte.
- On a real Android TV or Google TV: discovery or add by IP, pairing, every mapped key, text,
  apps, and a reconnect after standby. **Not done**: nobody on the project owns one yet.

### Out of scope

- Live power, volume and current-app state from the TV (the protocol sends them; the contract has
  no place for them yet). Voice. A subnet sweep for networks that drop mDNS. Turning the TV on by
  the Power key over a standby connection. Nothing deferred to an item yet.

## Execution

Branch `claude/project-thread-s72b34` (the session's branch, not `feature/WI-023-…`).

- Deviation: a TV that refuses the client certificate may break the TLS handshake with a reset or
  broken pipe rather than an alert, so any failure after the TCP connect is treated as a refusal.
- Deviation: the self-signed certificate has no DNS alternative name, unlike the reference's: the
  JDK rejects one with spaces, and the TV does not read it.

## Review

All 30 module tests pass locally (JVM modules built outside the Android build, since Google's Maven
repository is unreachable from the build machine); the app build is CI's. Real-device testing is
open: see the pull request for the checklist.
