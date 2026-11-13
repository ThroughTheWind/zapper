---
id: WI-003
title: Find Samsung TVs on the network and add one by IP
type: feature
status: planned
branch:
created: 2026-11-13
updated: 2026-11-13
related: [WI-002, SPEC-F1, SPEC-AC-1, SPEC-AC-2, SPEC-NFR-5]
epic:
children: []
---

## Proposal (rationale)

Users should not need to know their TV's IP address (US-1), but must not be blocked when discovery fails (US-2).

Phase 1 — Basic Samsung remote ([roadmap](../../ROADMAP.md)).

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- Samsung discovery: SSDP `M-SEARCH` for `urn:samsung.com:device:RemoteControlReceiver:1`, confirmed by `GET http://IP:8001/api/v2/`; dedupe by MAC/UUID (FR-1.2).
- Scan screen lists TVs with name, model and brand icon, times out at ~10 s with "Not found? Add by IP" and troubleshooting tips (FR-1.3).
- Manual add probes the IP with every adapter (FR-1.4).
- Permissions stay within NFR-5; cleartext only toward LAN addresses for the `:8001` info endpoint.

### Impacts

- `adapters/samsung` (discovery, probe), `app` (multicast lock provider, screens), `network_security_config.xml`.
- New permission `VIBRATE` is not needed here; no location permission.

### Approach

- UI follows the design system's [DiscoveryScan](../../design/components/DiscoveryScan/README.md), [DeviceCard](../../design/components/DeviceCard/README.md), [TextField](../../design/components/TextField/README.md) and [ErrorsAndHelp](../../design/components/ErrorsAndHelp/README.md) specs.
- SSDP over a `MulticastSocket` with a held `WifiManager.MulticastLock`; HTTP probe with OkHttp.
- Check the Android 16+ local network permission at implementation (NFR-5) and add the onboarding step if the target SDK requires it.

### Acceptance criteria / tests

- AC-1: the reference TV appears within 10 s on the same Wi-Fi.
- AC-2: adding it by IP works with discovery blocked.
- SSDP parsing and the `/api/v2/` probe are unit-tested with recorded responses from WI-001.

### Out of scope

- Discovery for other brands (each adapter adds its own). mDNS is not needed for Samsung.

## Execution

## Review
