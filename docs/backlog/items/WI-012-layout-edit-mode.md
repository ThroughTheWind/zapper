---
id: WI-012
title: Edit the remote: move, resize, add and remove buttons
type: feature
status: review
branch: claude/project-thread-n0w6sb
created: 2026-11-13
updated: 2026-11-14
related: [WI-006, SPEC-F8, SPEC-AC-13]
epic:
children: []
---

## Proposal (rationale)

The customizable layout (US-12 to US-14): explicit edit mode, drag to move, snap-resize handles, a catalog to add from, per-button options. Follows [EditMode](../../design/components/EditMode/README.md), [ButtonCatalog](../../design/components/ButtonCatalog/README.md), [ButtonConfig](../../design/components/ButtonConfig/README.md) and [CustomiseFlow](../../design/components/CustomiseFlow/README.md).

Phase 3 — Customization ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- Edit mode opens only from the pencil in the remote's top bar or Settings > Layout, never by a long press (FR-8.2). The top bar becomes Undo · Edit layout · Done; keys stop sending.
- Drag a widget: it lifts, snaps cell by cell with a `DragSnap` haptic, and the drop target shows `primary-container` when free and `error-container` when it overlaps. Overlaps are refused; nothing reflows ([EditMode](../../design/components/EditMode/README.md)).
- The selected widget's handle resizes between its allowed spans only (`WidgetType.allowedSpans`).
- Remove with the badge or by dropping on the trash zone that replaces the top bar while dragging ([PageManager](../../design/components/PageManager/README.md)).
- "Add a button" opens the catalog: the spec's groups as chips, search across all groups, unsupported buttons hidden, tap to place in the first free cell (FR-8.3, FR-8.8, [ButtonCatalog](../../design/components/ButtonCatalog/README.md)).
- Tapping a widget opens its options: label (6 characters on a 1x1 key), icon, colour from theme roles, repeat while held, size and move ([ButtonConfig](../../design/components/ButtonConfig/README.md), FR-8.4).
- Spacer and Label widgets, and the unsupported placeholder ([LayoutExtras](../../design/components/LayoutExtras/README.md)).
- Every drag has a non-drag equivalent: Size and Move in the options sheet, and TalkBack actions Move left/right/up/down and Remove.
- AC-13: move, resize, add and remove a button; the changes persist after a restart.

### Impacts

- `core/model`: `LayoutEditing` (fits, first free slot, move, resize, add, remove, update), `ButtonCatalog`, `KeyIcons`; `LayoutButton` gains `color` and `repeat`; new `SpacerWidget`, `LabelWidget`, `UnsupportedWidget`.
- `core/remote`: `TvConnection.installedApps()` for the catalog's Apps group.
- `app`: `layout/` (editor view model, edit canvas, catalog and options sheets, page bar, trash zone), `RemoteCanvas` draws the new widget types, colours, icons and repeat.

### Approach

- Edit mode works on a copy of the TV's layout in `LayoutEditorViewModel`; Done saves it, Back asks before discarding. The rules live in `LayoutEditing` as pure functions that return null to refuse, so they are tested on the JVM and the UI only asks.
- The canvas draws each widget exactly as the remote does and lays a gesture layer on top, so the widget sends nothing and the drawing never drifts from the real remote.
- The grid shows one spare row under the lowest widget (at least 8, at most 12 rows) so there is always somewhere to drop.

### Acceptance criteria / tests

- `LayoutEditingTest`: moves to a free cell; refuses overlaps and leaving the grid; resizes only to allowed spans and not over a neighbour; removing frees cells; adding takes the first free place and a fresh id; a full page refuses; options change in place; repeat defaults.
- `ButtonCatalogTest`: every spec group has items, composites are single items, installed apps come first under the known id, unsupported buttons are hidden.
- AC-13 on a phone: move, resize, add and remove a button, tap Done, force-stop the app, reopen: the layout is as edited.

### Out of scope

- The ±1.5° wiggle on entering edit mode, and dragging from the catalog onto the grid (tap places in the first free cell instead).
- An icon picker over all Material Symbols: the picker offers a curated set of 22, so a layout file never carries drawing data.
- Editing the target of a key (its action) after placing it: remove it and add another from the catalog.

## Execution

- 2026-11-14: built as planned on `claude/project-thread-n0w6sb`, with WI-011, WI-013, WI-014 and WI-015 in one PR stacked on PR #15.
- Volume and channel keys now repeat while held by default, since `repeat` defaults from the action; before, only the rockers repeated.

## Review

- Unit criteria: `LayoutEditingTest`, `EditHistoryTest`, `ButtonCatalogTest` pass locally on the JVM. The app compiles and lints in CI only.
- AC-13: **open** until tried on a phone.
