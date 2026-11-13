---
id: WI-007
title: Control TV volume with the phone's volume keys
type: feature
status: planned
branch:
created: 2026-11-13
updated: 2026-11-13
related: [WI-006, SPEC-FR-4.4, SPEC-AC-8]
epic:
children: []
---

## Proposal (rationale)

Changing volume without looking at the phone is the most common remote action (US-8).

Phase 1 — Basic Samsung remote ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- While the remote is in the foreground and a TV is connected, the phone's volume keys send Vol ± to the TV and do not change the phone volume (FR-4.4).
- On by default; the toggle lives in settings (a single switch now, the full settings screen is WI-011).

### Impacts

- `app` (`MainActivity` key handling, one preference in `core/data`).

### Approach

- Intercept `KEYCODE_VOLUME_UP/DOWN` in `dispatchKeyEvent`, with repeat on hold matching the on-screen rocker.

### Acceptance criteria / tests

- AC-8: the hardware keys change the TV volume while the app is open, and the phone volume when the switch is off or no TV is connected.

### Out of scope

- Volume keys while the app is in the background (not planned).

## Execution

## Review
