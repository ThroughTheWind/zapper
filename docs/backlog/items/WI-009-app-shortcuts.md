---
id: WI-009
title: Launch TV apps from shortcut buttons
type: feature
status: review
branch: claude/project-thread-qlkfpf
created: 2026-11-13
updated: 2026-11-14
related: [WI-001, SPEC-F6, SPEC-AC-10]
epic:
children: []
---

## Proposal (rationale)

One-tap streaming apps (US-10). The TV's app list may be empty on some firmware, so a bundled list of known app ids is needed (FR-6.1). Buttons follow [AppShortcut](../../design/components/AppShortcut/README.md).

Phase 2 — Samsung extras ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- App shortcut buttons open their app on the TV in one tap, whether the TV lists its apps or not (SPEC FR-6.1, AC-10).
- Shortcuts follow [AppShortcut](../../design/components/AppShortcut/README.md): 2x1 tile with a monogram well and the name (1x1 and 2x2 variants), tertiary by default, `Success` haptic when the TV takes the launch, `Failure` haptic and a snackbar when it does not (FR-6.2).
- The default layout gets Netflix and YouTube between the rockers on Main, and a new Apps page with every bundled app, as in [RemoteScreen](../../design/components/RemoteScreen/README.md) and [RemotePages](../../design/components/RemotePages/README.md).
- Layouts stay brand-neutral (ADR-0002): a shortcut names a bundled app id such as `netflix`, and each adapter translates it.

### Impacts

- `core/model`: `KnownApps`, the bundled brand-neutral app list; the default layout's Netflix/YouTube shortcuts and Apps page (Main, Apps, Numbers).
- `core/remote`: `TvSession.installedApps()`, `TvApp`, `TvError.AppNotInstalled`; `TvConnection.launchApp`, and `sendText` now returns the error rather than a Boolean.
- `adapters/samsung`: `ed.installedApp.get` and `ed.apps.launch`, a table of Samsung ids per known app (`SamsungApps.kt`), and the `APP_LAUNCH` capability.
- `adapters/fake`: app launching, with every bundled app but Plex installed so the failure path shows in debug builds.
- `core/designsystem`: the `AppShortcut` component. `app`: shortcuts on the canvas and launch feedback.

### Approach

- The Samsung session asks the TV for its app list once per connection (3 s timeout). A known app opens under the first of its ids the TV lists (Netflix has a 2019 and an older id); a TV that lists apps but not this one fails with `AppNotInstalled`; a TV that does not answer gets the newest id.
- The table is Kotlin data in the Samsung module, not a JSON asset: a JSON file would need an Android asset or a resource loader in a plain JVM module for no gain at seven entries.
- Monograms instead of the TV's icons: Samsung's `icon` field is a path on the TV that needs another request, and the design allows initials (FR-6.2).

### Acceptance criteria / tests

- `SamsungProtocolTest`: list and launch messages; the installed list parses with native apps marked; every known app has a Samsung id.
- `SamsungSessionTest`: a known app opens under the id the TV lists; an app missing from the list fails with `AppNotInstalled`; a TV that does not list apps gets the newest id.
- `TvConnectionTest`: a shortcut opens its app on the simulated TV, and one it lacks reports `AppNotInstalled`.
- `DefaultLayoutsTest`: pages are Main, Apps, Numbers; Netflix and YouTube sit on Main and every known app on Apps, all 2x1 and labelled.
- AC-10 on the reference TV: the Netflix shortcut opens Netflix. Needs Antoine and the TV.

### Out of scope

- Picking other installed apps as shortcuts, colours per shortcut and resizing: edit mode (WI-012) and per-button options (FR-8.4).
- The TV's own app icons, and the REST launch fallback (`POST /api/v2/applications/<id>`), which research marks unreliable on recent firmware.

## Execution

- 2026-11-14: built as planned on `claude/project-thread-qlkfpf`, in the same PR as WI-008 and WI-010.
- `TvConnection.sendText` from WI-008 now returns a `TvError?` instead of a Boolean, so a failed launch and a failed send can both say why.

## Review

- Unit criteria: the JVM tests above pass locally. The app module compiles and lints in CI only.
- AC-10: **open** until it is tried on the reference TV.
