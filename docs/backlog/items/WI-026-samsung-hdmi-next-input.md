---
id: WI-026
title: Switch HDMI inputs on Samsung TVs that ignore the direct HDMI keys
type: feature
status: in_progress
branch: claude/project-thread-ylideg
created: 2026-11-15
updated: 2026-11-15
related: [WI-022, ADR-0002, ADR-0003]
epic:
children: []
---

## Proposal (rationale)

WI-022 added HDMI 1 to 4 keys that send `KEY_HDMI1` to `KEY_HDMI4`. On the reference RU7020 (2019)
they do nothing, and community reports say the same of other Tizen TVs from 2018 on (Q70R, Q90R,
QN90A, S95B). The button is offered, pressed, and silently ignored. `KEY_HDMI`, which switches to
the next HDMI input, is reported working on the same generation.

## Decision

`accepted` — 2026-11-15, Antoine, by asking to investigate the HDMI shortcuts not working. The
direction was chosen by Antoine on 2026-11-15 from three options: replace the direct keys with a
next-input key on Samsung (chosen), keep both, or a Source-menu macro per port.

## Plan

### Requirements

- A brand-neutral `RemoteKey.HDMI_NEXT` ("Next HDMI" in the catalog, labelled "HDMI" on the key).
- Samsung sends `KEY_HDMI` for it and no longer sends `KEY_HDMI1` to `KEY_HDMI4`, so the catalog
  hides HDMI 1 to 4 on Samsung and a key already placed shows as unsupported.

### Impacts

- `core/model` (new enum value, catalog entry), `adapters/samsung` (key map), `app` (key label).
- Saved layouts keep their HDMI 1 to 4 buttons; on Samsung they are disabled and explain why.
- Research doc: the Samsung key list notes the direct keys are ignored.

### Approach

- Chosen: next-input key, which the local channel supports. Considered and not taken: a Source
  menu macro per port (the menu order depends on connected devices, so it lands on the wrong port),
  and the SmartThings API (cloud, against ADR-0003).

### Acceptance criteria / tests

- `SamsungProtocolTest`: `HDMI_NEXT` maps to `KEY_HDMI`, HDMI 1 to 4 map to null.
- `LayoutEditingTest`: the catalog offers Next HDMI and hides direct HDMI keys a TV lacks.
- On the RU7020, the HDMI key moves to the next HDMI input (preview build).

### Out of scope

- Picking a specific port on Samsung: no local way is known. Nothing deferred.

## Execution

Branch `claude/project-thread-ylideg` (the session's required branch name, not `feature/…`).

## Review
