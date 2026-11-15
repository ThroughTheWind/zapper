---
id: WI-014
title: Undo edits, reset layouts and keep one layout per TV
type: feature
status: review
branch: claude/project-thread-n0w6sb
created: 2026-11-13
updated: 2026-11-14
related: [WI-012, SPEC-FR-8.6, SPEC-FR-8.7, SPEC-AC-15]
epic:
children: []
---

## Proposal (rationale)

Recover from a bad edit (US-16), and keep a layout per TV with an option to copy one from another TV.

Phase 3 — Customization ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- Undo in edit mode reverts the last edit, again and again (FR-8.6).
- "Reset to default" in edit mode's overflow and in Settings > Layout restores the default layout, after a confirmation ([Dialog](../../design/components/Dialog/README.md)). AC-15.
- Each saved TV keeps its own layout; a TV without one uses the default; "Copy layout from another TV" takes another TV's (FR-8.7). Forgetting a TV forgets its layout.

### Impacts

- `core/model`: `EditHistory` (bounded at 50 edits).
- `core/data`: `StoredState.deviceLayouts`; `ZapperRepository.layoutFor`, `saveLayout(deviceId, …)`, `resetLayout`, `copyLayout`; `forgetDevice` drops the layout. The old `layouts` list is no longer read: no build ever wrote it.
- `app`: the remote draws the current TV's layout and follows TV switches; edit mode's overflow and Settings > Layout offer reset and copy.

### Approach

- In edit mode, reset and copy are edits: Undo brings the previous layout back until Done. In Settings they save at once, after the confirmation.
- The default is `DefaultLayouts.standard` for every adapter today; a per-adapter default can come with a second brand.

### Acceptance criteria / tests

- `EditHistoryTest`: undo goes back one edit at a time; an edit that changes nothing is not recorded; `changed` is false once every edit is undone; the history is bounded.
- AC-15 on a phone: edit the layout, then Settings > Layout > Reset to default: the factory layout is back.
- With two saved TVs, an edit on one leaves the other's layout as it was; Copy layout from another TV gives the current TV the other's layout.

### Out of scope

- Redo.
- A default layout per adapter (one brand today).

## Execution

- 2026-11-14: built as planned with WI-012, on `claude/project-thread-n0w6sb`.

## Review

- Unit criteria pass locally. AC-15 and the per-TV criterion: **open** until tried on a phone (the second needs two saved TVs; the debug build's fake TV can be the second).
