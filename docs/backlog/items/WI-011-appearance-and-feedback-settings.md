---
id: WI-011
title: Let users pick theme, colors, key style and haptics
type: feature
status: review
branch: claude/project-thread-n0w6sb
created: 2026-11-13
updated: 2026-11-14
related: [SPEC-F9, SPEC-F10, SPEC-AC-17]
epic:
children: []
---

## Proposal (rationale)

Make the app feel like the user's own (US-18): light/dark/system/OLED, dynamic color or a preset, key style and haptic strength, applied instantly. Follows [theming-and-customisation](../../design/theming-and-customisation.md) and [SettingsScreens](../../design/components/SettingsScreens/README.md).

Phase 3 — Customization ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- Settings > Appearance: theme System / Light / Dark, an OLED black switch, colour Dynamic (Android 12+) or one of the six preset seeds, key style Rounded / Squircle / Circle / Square, and a live preview row ([SettingsScreens](../../design/components/SettingsScreens/README.md), [SettingsControls](../../design/components/SettingsControls/README.md), [theming-and-customisation](../../design/theming-and-customisation.md)).
- Settings > Feedback: haptics Off / Light / Strong, default Light (FR-10.1); a click sound, off by default (FR-10.2).
- Settings > Controls: the phone volume keys switch (moved from the TV sheet) and the repeat speed slider, five steps from 150 to 45 ms.
- Every choice applies at once, without a restart (AC-17), and survives a restart.
- The settings root lists TVs, Layout, Appearance, Feedback, Controls and About with a summary of each, as the spec's settings summary does. Layout's entries are built by WI-012, WI-014 and WI-015.

### Impacts

- `core/model`: `AppearanceSettings` and its enums, serializable, every field defaulted.
- `core/data`: `StoredState.appearance`, `ZapperRepository.appearance` / `updateAppearance`. The JSON reader now coerces unknown enum values to defaults, so a downgrade cannot make the whole file unreadable.
- `core/designsystem`: `AppearancePrefs` gains `oledBlack`, `clickSound`, `repeatIntervalMs`; OLED applies to a dark System theme too; `RemoteMotion` takes the repeat interval; Strong haptics use the vibrator's predefined heavy clicks (API 29+), with the light feedback as the fallback.
- `app`: `settings/` (screen, view model, mapping), the theme read from a hot `StateFlow` in `AppContainer`, a Settings row in the TV sheet in place of the volume keys switch.

### Approach

- One `AppearanceSettings` value in the stored JSON, mapped to the design system's `AppearancePrefs` in the app, so `core/data` never depends on the UI module.
- `MainActivity` collects it and passes it to `ZapperTheme`; Compose recomposes the whole tree, which is what makes the change instant.
- The click sound is `View.playSoundEffect(CLICK)` on `KeyPress`, which follows the phone's own touch-sounds setting.

### Acceptance criteria / tests

- AC-17: switching theme (System, Light, Dark, OLED) and colour (Dynamic, presets) changes the open screen at once, with no restart. Checked on a phone.
- The key style, haptic strength and repeat speed change the remote's keys at once, and all settings survive a restart.
- `AppearanceSettings.repeatIntervalMs` maps the five steps to 150 … 45 ms (covered by the model compiling and the settings screen; the mapping is a list lookup).

### Out of scope

- The "Key labels: Icons / Icons + text" option in theming-and-customisation is not in the spec; not built.
- A custom seed colour (the design system defers it).

## Execution

- 2026-11-14: built on `claude/project-thread-n0w6sb`, stacked on PR #15 (Phase 2), in one PR with WI-012 to WI-015.
- Added the Controls page (volume keys, repeat speed) because the settings screen needed a home for the volume keys switch, which the TV sheet held only "before the settings screen arrives".

## Review

- AC-17: **open** until tried on a phone. The app compiles and lints in CI only (Android SDK downloads are blocked where this was built).
