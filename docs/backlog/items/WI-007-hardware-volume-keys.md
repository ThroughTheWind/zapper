---
id: WI-007
title: Control TV volume with the phone's volume keys
type: feature
status: done
branch: feature/WI-007-hardware-volume-keys
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

Branch `feature/WI-007-hardware-volume-keys`, stacked on WI-006.

- `MainActivity.onKeyDown`/`onKeyUp` send Vol ± for `KEYCODE_VOLUME_UP/DOWN` while a TV is connected and the switch is on, and swallows the up events too so the phone's volume panel never shows. In every other case the keys reach Android unchanged.
- `core/data`: `hardwareVolumeKeys`, on by default. The app container keeps it as a hot `StateFlow`, because it is read on every key event.
- The switch sits at the bottom of the TV sheet, the only sheet until the settings screen (WI-011) takes it over.

Deviation, with the reason: the keys are handled in `onKeyDown`/`onKeyUp`, not `dispatchKeyEvent` as planned, because lint rejects overriding `dispatchKeyEvent` on `ComponentActivity` (RestrictedApi). Also, a held volume key repeats at Android's own key-repeat rate (one step per repeated key event) rather than the rocker's 400/90/45 ms timing. Re-timing would mean ignoring the system's repeats and running a second timer for the same key; the system rate is close and is what users expect from those keys.

## Review

- AC-8 (hardware keys change the TV volume while the app is open; the phone volume when the switch is off or no TV is connected): the code path covers both branches, but it is not checked on a device. The Android modules are verified by CI only (assemble and lint), not run.
- No unit test: the logic is one condition in an Activity, and the app module has no test setup (F-003).
