**Authoritative for:** the order in which the MVP is delivered, and what each phase must prove before the next starts.
**Not authoritative for:** what the app does (the [spec](SPEC.md)), how it looks (the [design system](design/README.md)), or the state of any item (the [backlog](backlog/BACKLOG.md)).

# Roadmap

The MVP from the [spec](SPEC.md), cut into phases. **Basics first:** a remote that reliably controls
the reference Samsung TV ships before anything is customizable. Every screen, from the first phase,
is built from the [design system](design/README.md) components as specified, so customization later
edits the same widgets instead of replacing them.

| Phase | Goal | Items | Exit (spec acceptance criteria) |
| --- | --- | --- | --- |
| 0. Foundations | Repo, stack, CI, adapter contract, theme | done (PR #1, #2) | AC-21 (build part) |
| **1. Basic Samsung remote** | Find, pair and control the TV every day with a fixed default layout. Ships as **0.1.0** | WI-001 → WI-007, WI-017 | AC-1 → AC-8, AC-12, AC-18, AC-19, AC-21 |
| 2. Samsung extras | Text input, app shortcuts, power on | WI-008 → WI-010 | AC-9 → AC-11 |
| 3. Customization | Theme and haptics settings, edit mode, pages, reset, import/export | WI-011 → WI-015 | AC-13 → AC-17 |
| 4. Release 1.0 | Accessibility, onboarding, adapter guide | WI-016, WI-018 | All ACs |

This reorders the spec's milestones in one way: theming and haptic settings (F9, F10) move into
Phase 3 with the rest of customization. Phase 1 still uses the full theme with its defaults (dynamic
color, dark first, light haptics); only the choices are deferred.

## Phase 1 — Basic Samsung remote

Order matters: the spike settles the protocol facts the rest depends on, and the contract lands
before the screens that use it.

1. [WI-001](backlog/items/WI-001-samsung-reference-tv-spike.md) Prove the Samsung path on the reference TV (needs Antoine and the TV).
2. [WI-002](backlog/items/WI-002-adapter-contract-v2.md) Evolve the adapter contract and add a fake TV adapter.
3. [WI-003](backlog/items/WI-003-samsung-discovery.md) Find Samsung TVs and add one by IP.
4. [WI-004](backlog/items/WI-004-pairing-and-credentials.md) Guide pairing and keep credentials safe (closes finding F-001).
5. [WI-005](backlog/items/WI-005-connection-management.md) Reconnect automatically and manage saved TVs.
6. [WI-006](backlog/items/WI-006-default-remote.md) Build the default remote with the design system's widgets.
7. [WI-007](backlog/items/WI-007-hardware-volume-keys.md) Control TV volume with the phone's volume keys.

WI-003 to WI-005 can run in parallel once WI-002 is merged; WI-006 needs only WI-002.

**Release 0.1.0.** Each phase ends with a release on GitHub Releases, so the signed-release
pipeline ([WI-017](backlog/items/WI-017-signed-release-on-tag.md)) moved here from Phase 4. Phase 1
is 0.1.0, Phase 2 0.2.0, Phase 3 0.3.0, and Phase 4 is 1.0.0. How to cut one is in
[releasing](releasing.md).

## Phase 2 — Samsung extras

[WI-008](backlog/items/WI-008-text-input.md) text input,
[WI-009](backlog/items/WI-009-app-shortcuts.md) app shortcuts,
[WI-010](backlog/items/WI-010-power-on.md) power on. Independent of each other.

## Phase 3 — Customization

[WI-011](backlog/items/WI-011-appearance-and-feedback-settings.md) appearance and feedback settings,
[WI-012](backlog/items/WI-012-layout-edit-mode.md) edit mode, then
[WI-013](backlog/items/WI-013-layout-pages.md) pages,
[WI-014](backlog/items/WI-014-layout-undo-reset-per-tv.md) undo, reset and per-TV layouts, and
[WI-015](backlog/items/WI-015-layout-import-export.md) import and export, which all build on edit mode.

## Phase 4 — Release 1.0

[WI-016](backlog/items/WI-016-accessibility-onboarding-polish.md) accessibility, onboarding and polish, and
[WI-018](backlog/items/WI-018-adapter-guide.md) adapter guide.

## After 1.0

From the spec's out-of-scope list: other brands one adapter at a time (Android TV / Google TV, LG
webOS and Roku first, per the [research](research/tv-remote-protocols.md#0-tldr-and-recommendations)),
touchpad mode, home screen widget and Quick Settings tile, macros.
