---
id: WI-006
title: Build the default remote with the design system's widgets
type: feature
status: planned
branch:
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

## Review
