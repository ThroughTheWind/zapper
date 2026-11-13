---
id: WI-005
title: Reconnect automatically and manage saved TVs
type: feature
status: planned
branch:
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

## Review
