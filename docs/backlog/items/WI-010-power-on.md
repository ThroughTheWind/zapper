---
id: WI-010
title: Turn the TV on and show its power state
type: feature
status: review
branch: claude/project-thread-qlkfpf
created: 2026-11-13
updated: 2026-11-14
related: [WI-001, SPEC-F7, SPEC-AC-11]
epic:
children: []
---

## Proposal (rationale)

Turning the TV on is the one thing still forcing people to find the physical remote (US-11): Wake-on-LAN to the stored MAC, a one-time explanation when it is not enabled, and the TV's power state where reported.

Phase 2 — Samsung extras ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- Power turns the TV off when connected (`KEY_POWER`) and, when the TV is off, wakes it with Wake-on-LAN to the MAC stored at discovery (SPEC FR-7.1). Power stays enabled while the TV is off ([ErrorsAndHelp](../../design/components/ErrorsAndHelp/README.md)).
- When the TV does not wake, a one-time Info [Banner](../../design/components/Banner/README.md) explains how to enable it on the TV; later failures only show a snackbar (FR-7.2).
- The TV's power state shows where reported (Samsung `PowerState`, 2019+): the status chip reads "TV off · Power on" for a TV that is asleep or in standby, and tapping it turns the TV on ([ConnectionStatus](../../design/components/ConnectionStatus/README.md), FR-7.3).
- AC-11: with *Power On with Mobile* enabled, Power turns the TV on within 10 s.

### Impacts

- `core/remote`: `WakeOnLan` (magic packet, broadcast to `255.255.255.255` and the TV's /24 on ports 9 and 7); `TvAdapter.canPowerOn`, `powerOn`, `powerState`, `powerOnHelp` and `PowerState`; `TvConnection.powerOn`, `power`, `canPowerOn`, `powerOnHelp`.
- `adapters/samsung`: WoL to `wifiMac`, `PowerState` from `/api/v2/`, and the help text naming the TV setting. The UI never names the setting itself, so it stays brand-free (ADR-0002).
- `adapters/fake`: power on that brings the simulated TV back, or not (`wakesOnLan`).
- `core/data`: `powerOnHelpSeen`. `core/designsystem`: the chip's TV off state. `app`: Power while off, the chip, the banner.

### Approach

- `TvConnection.powerOn` sends the wake-up, then asks the TV for its power every second, sending the wake-up again every three seconds in case a packet was lost. As soon as the TV says it is on, it connects; it gives up after 15 s. Waiting on the info endpoint (2 s timeout) rather than on a WebSocket connect keeps a TV that is still booting from holding a 10 s socket timeout.
- The power state is asked after connecting, after a failed connection and five seconds after a Power key, not polled: no events exist for it (research §2.5) and the connection drops when the TV turns off anyway.
- A connected TV that reports standby (2019+ sets keep the network up) gets the Power key from the chip instead of a wake-up.

### Acceptance criteria / tests

- `WakeOnLanTest`: the packet is six `0xFF` then the MAC sixteen times; dashes work, other strings are refused; the packet goes to the limited broadcast and the TV's subnet.
- `SamsungPowerTest`: a TV with a known MAC is woken with Wake-on-LAN; the power state comes from `PowerState`; an older TV without it is on, one that does not answer is unknown; the help names the TV setting.
- `TvConnectionTest`: power on wakes a TV that is off and connects; it gives up when the TV does not wake, resending the wake-up meanwhile; a TV without a MAC cannot be powered on.
- AC-11 on the reference TV, in standby: Power turns it on within 10 s. Needs Antoine and the TV.

### Out of scope

- SmartThings power-on (cloud, ADR-0003) and Frame TV art mode.
- Asking for a MAC address by hand for a TV added by IP while it was off; discovery and probing record it.

## Execution

- 2026-11-14: built as planned on `claude/project-thread-qlkfpf`, in the same PR as WI-008 and WI-009.
- Layout Power buttons now go through the same path as the D-pad and rockers (`RemoteActions.send`), so Power while off wakes the TV and Play/Pause alternation applies to buttons too.

## Review

- Unit criteria: the JVM tests above pass locally. The app module compiles and lints in CI only.
- AC-11: **open** until it is tried on the reference TV. The research notes third-party apps already woke this set over Wi-Fi.
