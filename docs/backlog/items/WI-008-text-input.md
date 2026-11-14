---
id: WI-008
title: Type into the TV from the phone keyboard
type: feature
status: review
branch: claude/project-thread-qlkfpf
created: 2026-11-13
updated: 2026-11-14
related: [SPEC-F5, SPEC-AC-9]
epic:
children: []
---

## Proposal (rationale)

Searching with a D-pad is painful (US-9). Samsung accepts text only while a TV keyboard is on screen, and signals it with `imeStart`/`imeEnd` events.

Phase 2 — Samsung extras ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- A Keyboard key on the remote opens the phone keyboard in a [TextInputBar](../../design/components/TextInputBar/README.md); the text goes to the TV's focused field (SPEC FR-5.1).
- The bar opens by itself when the TV opens its on-screen keyboard (Samsung `ms.remote.imeStart`) and closes when the TV closes it (`imeEnd`), on Back, or on a tap outside it.
- A TV without text input greys the Keyboard key out, and the bar cannot open (FR-5.2).
- Text is never queued: sending while disconnected fails with a snackbar and starts a reconnect (FR-3.5).

### Impacts

- `core/remote`: `TvSession.tvKeyboard`; `TvConnection.capabilities`, `tvKeyboard` and `sendText`.
- `core/model`: a new `ButtonAction.OpenKeyboard` needing `Capability.TEXT_INPUT`; the default Main page's top row becomes Power, Source, Keyboard, Menu, as in [RemotePages](../../design/components/RemotePages/README.md). Settings leaves it: no Samsung code is confirmed for it, so it was always greyed out.
- `adapters/samsung`: `SendInputString` and `SendInputEnd` messages, `imeStart`/`imeEnd` events, and the `TEXT_INPUT` capability.
- `adapters/fake`: text input, and `showKeyboard()` to simulate the TV keyboard.
- `core/designsystem`: the `TextInputBar` component. `app`: the overlay, the snackbar host and `adjustResize`.

### Approach

- Samsung `SendInputString` is sent once, on Send, followed by `SendInputEnd` to confirm, rather than live on every keystroke. Whether the TV appends or replaces on each `SendInputString` is not confirmed on the reference TV, and live typing would garble the field if it appends. The status line therefore says "sends when you tap Send" instead of the design's "typing live". Switching to live typing is a small change once the spike (WI-001) settles it.
- Send stays possible while the TV has not reported its keyboard: the 2019 firmware may not send `imeStart`, and the status line already asks the user to open a search field first.

### Acceptance criteria / tests

- `SamsungProtocolTest`: text is base64 in `SendInputString`, then `SendInputEnd`; `imeStart`/`imeEnd` parse as keyboard events.
- `SamsungSessionTest`: typed text reaches the TV and is confirmed; the session follows the TV keyboard.
- `TvConnectionTest`: text reaches the simulated TV and the TV keyboard is followed; text while disconnected is refused, not queued.
- `DefaultLayoutsTest` / `RemoteLayoutTest`: the Main page has a Keyboard button; it survives a JSON round trip and needs `TEXT_INPUT`.
- AC-9 on the reference TV: with YouTube search focused, typing "stranger" enters it. Needs Antoine and the TV.

### Out of scope

- Live, per-keystroke typing (above). Mirroring the TV field's current text into the bar (`imeUpdate`).
- A keyboard for TVs without a text field (pointer, on-screen keyboard navigation).

## Execution

- 2026-11-14: built as planned on `claude/project-thread-qlkfpf` (the session's branch; one PR carries all of Phase 2, one commit per item).

## Review

- Unit criteria: the JVM tests above pass locally (`:core:model`, `:core:remote`, `:adapters:samsung`, `:adapters:fake`). The app module compiles and lints in CI only (no Android SDK in the authoring environment).
- AC-9: **open** until it is tried on the reference TV.
