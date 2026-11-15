---
id: WI-016
title: Pass accessibility review and polish onboarding and errors
type: feature
status: review
branch: claude/project-thread-i8pmcn
created: 2026-11-13
updated: 2026-11-15
related: [SPEC-NFR-9, SPEC-NFR-2]
epic:
children: []
---

## Proposal (rationale)

Release quality: TalkBack on the default layout, font scaling, contrast in every theme, cold start under 1.5 s, and the first-run flow from [OnboardingFlow](../../design/components/OnboardingFlow/README.md) and [ErrorsAndHelp](../../design/components/ErrorsAndHelp/README.md).

Phase 4 — Release 1.0 ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- TalkBack can use the default layout (SPEC NFR-9): every key is one button named by its action,
  a double tap presses it once, a disabled key is announced as disabled, and nothing is read twice.
- The status chip and banners are announced when they change, so a TalkBack user hears the TV drop.
- Touch targets of at least 48 dp, including the status chip and the D-pad chevrons for TalkBack.
- Font scaling: text follows the system size everywhere; key text shrinks to fit its key rather
  than being cut off, and the welcome screen scrolls rather than hiding "Find my TV".
- WCAG AA contrast in every bundled theme, checked by a test.
- [ErrorsAndHelp](../../design/components/ErrorsAndHelp/README.md) Offline: an error banner with
  Retry and Switch TV, and a tapped disabled key says "Not connected" with Reconnect
  ([RemoteKey](../../design/components/RemoteKey/README.md): Disabled).
- Cold start to a usable remote under 1.5 s (SPEC NFR-2), measured on the reference phone.

### Impacts

- `core/designsystem`: `RemoteKey` semantics and a `KeyText` that auto-sizes; DPad, Rocker,
  NumberPad, Banner and ConnectionStatusChip semantics; a JUnit contrast test (the module's first
  unit test).
- `app`: the remote's offline banner and Not connected snackbar, key labels, welcome and discovery
  headings.

### Approach

- `clearAndSetSemantics` on each key with `onClick`, `onLongClick` and `disabled()`, rather than
  `clickable`, so the custom press, hold-to-repeat and haptics stay as they are.
- A `LocalDisabledKeyPress` composition local lets the remote say why a disabled key does nothing
  without adding a parameter to every widget; edit mode does not provide it.
- Contrast is a unit test over the generated schemes (`schemeFor` for every seed and mode, and the
  power colours), not a review step, so a token change that breaks AA fails CI. Dynamic colour
  comes from the phone and is not checkable here.
- Startup: no code change unless the measurement fails. Compose already ships its baseline profiles
  through `profileinstaller`.

### Acceptance criteria / tests

- `ContrastTest`: every bundled theme meets 4.5:1 for text and icons on the surfaces keys use, and
  3:1 for the Ghost key outline; power colours in light and dark.
- On a phone with TalkBack on: swiping through the default layout reads each key once by its action
  ("Volume up", "OK", "Open Netflix"), a double tap sends it, and with the TV off keys read
  "disabled".
- At the largest system font size: key text shrinks inside its key, and the welcome screen scrolls
  to "Find my TV".
- With the TV unplugged: the error banner shows Retry and Switch TV; tapping a key shows "Not
  connected" with Reconnect.
- Cold start measured with `adb shell am start -W` on the reference phone: `TotalTime` under 1500 ms.

### Out of scope

- Hardware keyboard and switch access focus rings (RemoteKey: Focused). A finding if needed.
- Android 16+ local network permission screen: not enforced at the current target SDK (36).
- Translations (English only until the spec says otherwise).

## Execution

- 2026-11-15: built as planned on `claude/project-thread-i8pmcn`, on top of the Phase 3 branch
  (WI-011 to WI-015, WI-022), which was not merged yet.
- No local Android toolchain in the build session: formatting was checked with ktlint 1.8.0, and the
  Kotlin compile, the contrast test and lint run in CI.

## Review

- `ContrastTest`: runs in CI. A script over the same generated colours found no pair below AA before
  the test was written.
- TalkBack, font size, offline and cold start criteria: **open** until tried on a phone with the
  reference TV (RU7020).
