---
id: WI-027
title: Ship the app in French, Spanish, German and Polish
type: feature
status: review
branch: claude/project-thread-9d5emg
created: 2026-11-26
updated: 2026-11-26
related: [ADR-0002, ADR-0003]
epic:
children: []
---

## Proposal (rationale)

Zapper is English only (SPEC NFR-10), and none of its copy was in string resources: every label,
message and accessibility description was typed into Kotlin. A French, Spanish, German or Polish
user gets an English remote, and a community translator has nothing to translate.

## Decision

`accepted` — 2026-11-26, Antoine, by asking to "add language packs to the app: English, French,
Spanish, German, Polish". This reverses the earlier "English only" constraint in
`.ai/session.md` and SPEC open question 4.

## Plan

### Requirements

- Every user-facing string in `app` and `core/designsystem` comes from a string resource,
  including accessibility labels and snackbar messages.
- French, Spanish, German and Polish translations of every string, with real plurals (Polish
  `one`/`few`/`many`/`other`).
- The app follows the phone's language; Android 13+ lists the five languages in the system's
  per-app language setting, and Settings > Language opens it.
- Copy that comes from core or adapter code (errors, import failures, the Samsung power-on help)
  shows in the app's language.

### Impacts

- `app`, `core/designsystem`: strings moved to `res/values*/strings.xml`; ViewModels emit `UiText`
  instead of `String`, so messages resolve in the screen's language.
- `core/remote`: new `LocalizedText`; `TvAdapter.powerOnHelp` becomes `LocalizedText?` (adapter
  contract, docs/adapters.md). `core/model`: `LayoutFileException` carries a `Reason`.
- `app/build.gradle.kts`: `generateLocaleConfig` and `localeFilters`.
- Docs: SPEC NFR-10, AGENTS.md conventions, session constraints, design README, new
  `docs/translations.md`.

### Approach

- Chosen: Android string resources per module, resolved in composition (`stringResource`) or from
  `UiText` in ViewModels. Adapters, being JVM modules, carry their own translations in
  `LocalizedText`.
- Per-app language: the platform's own setting on Android 13+ (`generateLocaleConfig`), opened from
  Settings. Considered and not taken: AppCompat's `setApplicationLocales` back-port, which needs
  `AppCompatActivity` and an AppCompat theme for a picker that only matters below Android 13.
- Default layout pages keep their stored English names; while unrenamed they display translated.

### Acceptance criteria / tests

- `node scripts/check-translations.mjs` passes: no copy typed into Compose code, every language has
  every key, placeholder and plural quantity. Its self-test proves both directions.
- `LayoutFileTest` asserts the import failure reasons; `LocalizedTextTest` the fallback;
  `SamsungPowerTest` that the help covers all four languages.
- CI: `./gradlew lint` (MissingTranslation) and `:app:assembleDebug` pass.
- On a phone set to each language, onboarding, pairing, the remote, edit mode and Settings read in
  that language, and nothing is cut off.

### Out of scope

- Languages beyond these five, and a translation platform (Weblate, Crowdin): nothing deferred
  until a contributor asks.
- An in-app language picker below Android 13: those phones follow the system language.
- Checking Samsung's localized menu names on a real TV per language: recorded in
  `docs/translations.md` as unverified.

## Execution

Branch `claude/project-thread-9d5emg` (the session's required branch name, not `feature/…`).

- No Android SDK was reachable from the session that built this, so the app was not compiled
  locally; `ktlint`, the translation gate and the rungs checks were run, and CI is the first build.
- Deviation: single digit keys showed "Digit 1" (and "DIG" in the catalog) because the key title
  fell back to the enum name. Translating that fallback would have translated the bug, so the title
  is now the digit itself.

## Review

To fill once CI has run and the app has been tried in each language.
