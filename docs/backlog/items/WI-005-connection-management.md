---
id: WI-005
title: Reconnect automatically and manage saved TVs
type: feature
status: done
branch: feature/WI-005-connection-management
created: 2026-11-13
updated: 2026-11-13
related: [WI-002, WI-004, SPEC-F3, SPEC-AC-4, SPEC-AC-12, SPEC-NFR-3]
epic:
children: []
---

## Proposal (rationale)

The remote must be ready the moment the app opens and survive Wi-Fi blips and TV sleep without the user doing anything (US-4, US-5, NFR-3).

Phase 1 — Basic Samsung remote ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- Reconnect to the last TV on app open (FR-3.1) and after network return, with backoff.
- A persistent status indicator: connected / connecting / offline / TV off (FR-3.2).
- Saved TVs: switch, rename, forget, set default, view info (FR-3.3).
- Connection held only while in the foreground (FR-3.4, NFR-12); a press while disconnected triggers a reconnect and is not queued (FR-3.5).

### Impacts

- `app` (connection controller, lifecycle observer, network callback), `core/data` (saved TVs).

### Approach

- UI follows [ConnectionStatus](../../design/components/ConnectionStatus/README.md), [TvSwitcher](../../design/components/TvSwitcher/README.md), [DeviceCard](../../design/components/DeviceCard/README.md) and [Banner](../../design/components/Banner/README.md).
- `ConnectivityManager.NetworkCallback` drives reconnects; `ProcessLifecycleOwner` drops the socket in the background.

### Acceptance criteria / tests

- AC-4: killing and reopening the app reconnects with no TV prompt.
- AC-12: Wi-Fi off and on reconnects within 5 s of the network returning.
- Reconnect backoff and the no-queue rule are unit-tested on the fake adapter.

### Out of scope

- Background service or notification remote (v1.1).

## Execution

Branch `feature/WI-005-connection-management`, stacked on WI-004.

- `core/remote`: `TvConnection` replaces the app's `ConnectionController`, so the reconnect rules run on the JVM against the fake adapter. It retries an unreachable TV or a dropped connection with 1, 2, 4, 8, then 15 s backoff while in the foreground, never retries a pairing outcome, retries at once when the network returns, drops the socket on `pause()` and restores it on `resume()`, and turns a press while disconnected into a reconnect without replaying it.
- `adapters/fake`: `online` and `dropConnections()` simulate a TV that is off or a network that drops; `connectAttempts` counts tries.
- `core/data`: rename, forget (with the TV's secrets), and a default TV that the app opens on before the last used one. Saving a TV after a reconnect keeps the name the user gave it.
- App: `MainActivity.onStart`/`onStop` drive `resume`/`pause` (no new dependency, so `ProcessLifecycleOwner` was not needed with one activity); `NetworkMonitor` uses the default network callback; the remote's top bar shows the `ConnectionStatusChip` and opens the `TvSwitcher` sheet (switch, rename, open on this TV, info, forget, add a TV).
- Design system: `ConnectionStatusChip`.

Deviations, with the reason:

- Keys render disabled while offline, as the [ConnectionStatus](../../design/components/ConnectionStatus/README.md) design says, so in the UI the reconnect is started from the chip's "Offline · Retry" or happens on its own with backoff. FR-3.5's rule (a press reconnects and is not queued) is implemented and tested in `TvConnection`, and applies to any press that arrives while disconnected.
- The switcher shows live status for the current TV only; other saved TVs read "Saved". Probing every saved TV each time the sheet opens is extra network work the MVP does not need; "TV off" with Power on arrives with Wake-on-LAN (WI-010).

## Review

- Unit tests on the JVM (58 tests, all passing; 7 new in `TvConnectionTest`): a paired TV reconnects without pairing and is saved; an unreachable TV is retried at 1 s, then 2 s, and connects when it comes back; the network returning retries at once; a dropped connection comes back; a refused pairing is not retried; a press while disconnected reconnects and is not replayed; pausing drops the connection, does no work in the background, and resuming restores it.
- AC-4 (kill and reopen reconnects with no TV prompt): the app reconnects to the saved TV with its stored token on open; not yet checked on a device.
- AC-12 (Wi-Fi off and on reconnects within 5 s): the network callback triggers an immediate retry, tested at the `TvConnection` level; not yet checked on a device.
- The Android modules are verified by CI only (assemble and lint), not run.
