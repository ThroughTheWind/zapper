**Authoritative for:** which items exist and what state each is in, and the next free id.
**Not authoritative for:** why any item exists or how to do it — that is the item file.

# Backlog

The board. One row per live work item, grouped by status. Items live in
[`items/`](items/); finished work moves to [`archive/`](archive/).

<!-- NEXT-ID: WI-022 -->
<!-- Claim from this marker and bump it on your own branch. `node .ai/rungs.mjs check` refuses a duplicate. -->

## In progress

| Id | Title | Type | Branch |
| --- | --- | --- | --- |
| — | *nothing in flight* | | |

## Review

| Id | Title | Type | Branch |
| --- | --- | --- | --- |
| [WI-021](items/WI-021-landing-page.md) | Publish the landing page on GitHub Pages | docs | `claude/project-thread-238rhr` |
| [WI-017](items/WI-017-signed-release-on-tag.md) | Publish a signed APK to GitHub Releases on each tag | chore | `claude/project-thread-vpshn1` |

## Planned

| Id | Title | Type |
| --- | --- | --- |
| [WI-001](items/WI-001-samsung-reference-tv-spike.md) | Prove the Samsung path on the reference TV | spike |

## Accepted

| Id | Title | Type |
| --- | --- | --- |
| [WI-008](items/WI-008-text-input.md) | Type into the TV from the phone keyboard | feature |
| [WI-009](items/WI-009-app-shortcuts.md) | Launch TV apps from shortcut buttons | feature |
| [WI-010](items/WI-010-power-on.md) | Turn the TV on and show its power state | feature |
| [WI-011](items/WI-011-appearance-and-feedback-settings.md) | Let users pick theme, colors, key style and haptics | feature |
| [WI-012](items/WI-012-layout-edit-mode.md) | Edit the remote: move, resize, add and remove buttons | feature |
| [WI-013](items/WI-013-layout-pages.md) | Add, rename and reorder remote pages | feature |
| [WI-014](items/WI-014-layout-undo-reset-per-tv.md) | Undo edits, reset layouts and keep one layout per TV | feature |
| [WI-015](items/WI-015-layout-import-export.md) | Export and import layouts as files | feature |
| [WI-016](items/WI-016-accessibility-onboarding-polish.md) | Pass accessibility review and polish onboarding and errors | feature |
| [WI-018](items/WI-018-adapter-guide.md) | Write the guide for adding a TV brand | docs |

## Proposed

| Id | Title | Type |
| --- | --- | --- |
| — | | |

## Deferred

| Id | Title | Revisit when |
| --- | --- | --- |
| — | | |

---

Rejected and completed items keep their files — see [`archive/`](archive/). A rejection is part of
the record, with its reason; deleting one loses the answer to "why not?" and invites the same
proposal again.
