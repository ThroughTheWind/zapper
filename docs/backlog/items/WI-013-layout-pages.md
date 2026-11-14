---
id: WI-013
title: Add, rename and reorder remote pages
type: feature
status: review
branch: claude/project-thread-n0w6sb
created: 2026-11-13
updated: 2026-11-14
related: [WI-012, SPEC-FR-8.5, SPEC-AC-14]
epic:
children: []
---

## Proposal (rationale)

Several pages per layout, swiped horizontally (US-15). Follows [PageManager](../../design/components/PageManager/README.md) and [RemotePages](../../design/components/RemotePages/README.md).

Phase 3 — Customization ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- In edit mode a page bar under the top bar shows a pill per page, the current one `primary`, and "+ Page" ([PageManager](../../design/components/PageManager/README.md)).
- Tap a page to edit it; tap the current page again for Rename, Move left, Move right and Delete. Deleting asks first when the page has buttons; the last page cannot be deleted (FR-8.5).
- On the remote, pages swipe horizontally with dots below, as they already do.
- AC-14: a user can add a second page and swipe between pages.

### Impacts

- `core/model`: `LayoutEditing.addPage`, `renamePage`, `movePage`, `deletePage`, `freshPageId`.
- `app`: `PageBar`, the page name dialog, the delete confirmation.

### Approach

- Pages are edits like any other, so they go through the same undo history and are saved with Done.
- Edit mode shows one page at a time, chosen from the page bar rather than by swiping, so a horizontal drag always moves a widget.

### Acceptance criteria / tests

- `LayoutEditingTest`: pages are added, renamed (not to a blank name), reordered and deleted; the last page cannot be deleted; new pages get unused ids.
- AC-14 on a phone: add a page, put a button on it, tap Done, swipe to it on the remote.

### Out of scope

- Reordering by long-press drag in the page bar: Move left and Move right do the same, and work with TalkBack.

## Execution

- 2026-11-14: built as planned with WI-012, on `claude/project-thread-n0w6sb`.

## Review

- Unit criteria pass locally. AC-14: **open** until tried on a phone.
