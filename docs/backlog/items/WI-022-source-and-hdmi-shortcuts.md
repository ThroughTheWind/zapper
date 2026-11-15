---
id: WI-022
title: Switch TV inputs from Source and HDMI shortcut keys
type: feature
status: review
branch: claude/project-thread-v05i3a
created: 2026-11-14
updated: 2026-11-14
related: [WI-012, SPEC-F4]
epic:
children: []
---

## Proposal (rationale)

Switching to a console or a set-top box means opening the TV's Source menu and scrolling to the
right port. A key that goes straight to HDMI 2 saves that every time. The Source key already
exists (SPEC F4); the direct HDMI keys do not.

## Decision

`accepted` — 2026-11-14, Antoine, by saying yes to adding source shortcuts after the feasibility
check.

## Plan

### Requirements

- Brand-neutral keys `HDMI_1` to `HDMI_4` in `RemoteKey`, next to `SOURCE` (ADR-0002).
- Edit mode's catalog offers Source and HDMI 1 to 4 in the System group, and hides any of them the
  connected TV does not support (FR-4.5).
- Source and HDMI keys use the Tertiary emphasis, as the
  [RemoteKey](../../design/components/RemoteKey/README.md) design asks for "Source, input".
- The Samsung adapter sends `KEY_HDMI1` to `KEY_HDMI4`.

### Impacts

- `core/model`: `RemoteKey`, `ButtonCatalog`. Layout files gain four key names; older builds keep
  them as placeholders on import.
- `adapters/samsung`: four key codes. `app`: key titles and emphasis.

### Approach

- Gate with the existing per-key `supports()` rather than a new capability: an input key is an
  ordinary key press on every brand researched so far (Samsung, Roku). A selectable input list
  (LG, Android TV, Sony) is a different control and a separate item.
- The Samsung local channel cannot tell whether a model has the direct HDMI keys, so Samsung
  reports them supported. A model without them ignores the press.
- Not added to the default layout until they are confirmed on the reference TV.

### Acceptance criteria / tests

- `ButtonCatalogTest`: Source and HDMI 1 to 4 are in the catalog and are hidden when unsupported.
- `SamsungProtocolTest`: the keys map to `KEY_SOURCE` and `KEY_HDMI1` to `KEY_HDMI4`.
- On the reference TV (RU7020): Source opens the input menu, and each HDMI key switches to its
  port. Needs Antoine and the TV.

### Out of scope

- Listing the TV's inputs, naming them, or showing the active one: the Samsung local channel does
  not expose them.
- Other inputs (AV, component, tuner) and adapters other than Samsung.

## Execution

- 2026-11-14: built as planned on `claude/project-thread-v05i3a`, on top of WI-011 to WI-015.

## Review

- Unit criteria: written; the JVM tests run in CI (no Android toolchain in the build session).
- Reference TV: **open** until the HDMI keys are tried on the RU7020.
