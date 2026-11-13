---
id: WI-006
title: Build the default remote with the design system's widgets
type: feature
status: done
branch: feature/WI-006-default-remote
created: 2026-11-13
updated: 2026-11-13
related: [WI-002, SPEC-F4, SPEC-AC-6, SPEC-AC-7, SPEC-NFR-1, SPEC-NFR-9]
epic:
children: []
---

## Proposal (rationale)

The everyday remote is the product's core (US-6, US-7). It replaces the scaffold's button grid with the design system's real widgets, in a fixed default layout. Editing it comes in Phase 3.

Phase 1 — Basic Samsung remote ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- Default layout with Power, Source, Home, Back, Menu, D-pad + OK, Vol ± and Mute, Ch ±, Play/Pause, Rewind, Fast-forward, Info/Guide, and a number pad on its own page (FR-4.1).
- Volume, channel and D-pad repeat while held, using press/release where the TV supports it (FR-4.3).
- Keys the adapter does not support show as disabled (FR-4.5).
- Haptics on every press at the default level (FR-10.1 default only; the setting comes in WI-011).
- Press to command on the wire < 50 ms p95 (NFR-1); content descriptions and 48 dp+ targets (NFR-9).

### Impacts

- `core/designsystem` (widgets), `core/model` (layout model gains widget types needed for D-pad, rockers and number pad), `app` (remote screen replaces the scaffold screen).

### Approach

- Build the widgets exactly as specified: [RemoteScreen](../../design/components/RemoteScreen/README.md), [RemotePages](../../design/components/RemotePages/README.md), [DPad](../../design/components/DPad/README.md), [Rocker](../../design/components/Rocker/README.md), [NumberPad](../../design/components/NumberPad/README.md), [MediaControls](../../design/components/MediaControls/README.md), [RemoteKey](../../design/components/RemoteKey/README.md), with the grid, motion and haptics from [the design system](../../design/README.md) and [motion-and-haptics](../../design/motion-and-haptics.md).
- The layout model grows the design system's widget types (Key, DPad, Rocker, NumberPad, MediaBar) now, so Phase 3 edits the same model instead of migrating it.

### Acceptance criteria / tests

- AC-6: every default button has the expected effect on the reference TV.
- AC-7: holding Vol+ raises the volume until release.
- Compose UI tests on the fake adapter: each widget sends the right action; disabled keys send nothing.

### Out of scope

- Editing, extra pages, app shortcuts (Phase 2 and 3). Touchpad (v1.1).

## Execution

Branch `feature/WI-006-default-remote`, stacked on WI-005.

- `core/model`: a layout is now pages of widgets (`RemotePage`, `LayoutWidget`) with the design system's types: `LayoutButton`, `DPadWidget` (3x3 or 4x4), `RockerWidget` (volume or channel, 1x3 or 1x2), `NumberPadWidget` (3x4) and `MediaBarWidget` (4x1 or 3x1). Sizes the design does not allow are refused. `RemoteKey` gains `EXIT`, `CHANNEL_LIST` and `PREVIOUS_CHANNEL`, mapped for Samsung from the research key list.
- `DefaultLayouts.standard` follows the RemoteScreen preview on the 4-column grid: Power, Source, Menu, Settings; the D-pad beside Home, Back, Mute; volume and channel rockers around Guide and Info; the media bar; and a Numbers page with the number pad.
- Design system: `DPad` (wedge hit areas, OK at 40%, hold repeat, swipe mode at 24 dp per step, five TalkBack buttons), `Rocker`, `NumberPad`, `MediaControls`, and a shared hold-to-repeat helper with the motion tokens' 400/90/45 ms timing.
- App: `RemoteCanvas` lays pages out on the grid (cells between 64 and 96 dp) in a horizontal pager with page dots. Keys the TV does not support, and every key while offline, render disabled.

Deviations, with the reason:

- The two 2x1 cells for Netflix and YouTube stay empty until app shortcuts ship (WI-009), rather than hold keys the design does not place there. The design's Apps page also waits for WI-009.
- Holding a key repeats clicks from the phone instead of sending Press then Release. Whether a Samsung TV repeats a held key by itself is not confirmed yet; the spike (WI-001) checks it on the reference TV, and switching is local to the widgets.
- Samsung has no play/pause toggle key, so the play/pause button alternates Pause and Play, starting with Pause.
- Compose UI tests were not added: the app module has no UI test setup and none can run here. Recorded as F-003.

## Review

- Unit tests on the JVM (61, all passing): no two widgets of a default page overlap or leave the grid, the default remote has every key FR-4.1 lists, numbers are on their own page, and widgets refuse sizes the design does not allow.
- AC-6 (every default button works on the reference TV) and AC-7 (holding Vol+ raises the volume until release): need the reference TV (WI-001). Not checked.
- "Compose UI tests on the fake adapter: each widget sends the right action; disabled keys send nothing": **not met**, see F-003.
- NFR-1 (press to wire under 50 ms p95) is not measured yet; it belongs to the spike on the reference TV.
- The Android modules are verified by CI only (assemble and lint), not run.
