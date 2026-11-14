---
id: WI-015
title: Export and import layouts as files
type: feature
status: review
branch: claude/project-thread-n0w6sb
created: 2026-11-13
updated: 2026-11-14
related: [WI-012, SPEC-F12, SPEC-AC-16]
epic:
children: []
---

## Proposal (rationale)

Reuse or share a layout on another phone (US-17) through the share sheet, with a versioned JSON schema that keeps unknown buttons as disabled placeholders.

Phase 3 — Customization ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- Export a layout as a versioned JSON file through the Android share sheet (FR-12.1), from edit mode's overflow and from Settings > Layout.
- Import from a file picker; the version is checked and a newer file is refused with a reason; unknown or unsupported buttons become disabled placeholders, not dropped (FR-12.2, [LayoutExtras](../../design/components/LayoutExtras/README.md)). Tapping a placeholder says "Not supported on this TV".
- AC-16: exporting a layout and importing it on a fresh install reproduces it exactly.

### Impacts

- `core/model`: `LayoutFile` (encode, decode, `LayoutFileException`, `ImportedLayout`), `UnsupportedWidget`.
- `app`: `layout/LayoutFiles.kt` (share and pick), a `FileProvider` for the app's cache (`res/xml/file_paths.xml`) and the manifest entry.

### Approach

- The file is `{ "format": "zapper-layout", "version": 1, "layout": { … } }` with every default written out, so a file says exactly what it holds.
- Widgets are read one by one. One that does not decode (a new widget type, a key this version does not know) becomes an `UnsupportedWidget` holding the original JSON, which export writes back unchanged, so a newer Zapper can read it again. Overlapping or off-grid widgets move to the first free cell, so edit mode's rules hold.
- Only the user moves the file: the share sheet and the system file picker. Zapper sends nothing anywhere (ADR-0003). No new dependency: `FileProvider` is in `androidx.core`, already used.
- Import in edit mode is an edit (Undo, then Done to keep it); in Settings it replaces the TV's layout at once.

### Acceptance criteria / tests

- `LayoutFileTest`: export then import gives an equal layout; the file names its format and version; a newer version is refused with "newer version"; non-layout files are refused; unknown widget types and keys become placeholders in place; a placeholder survives a second round trip; overlapping widgets move to a free cell.
- AC-16 on a phone: export, uninstall, install, pair, import: the layout is the same.

### Out of scope

- Opening a `.json` shared to Zapper from another app (an intent filter); import goes through Zapper's own picker.
- Several named layouts per TV.

## Execution

- 2026-11-14: built as planned with WI-012, on `claude/project-thread-n0w6sb`.

## Review

- Unit criteria pass locally. AC-16: **open** until tried on a phone.
