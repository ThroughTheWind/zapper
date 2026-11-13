---
id: WI-003
title: Find Samsung TVs on the network and add one by IP
type: feature
status: done
branch: feature/WI-003-samsung-discovery
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

Branch `feature/WI-003-samsung-discovery`, stacked on WI-002.

- `adapters/samsung`: `Ssdp` (M-SEARCH, resent every 3 s, headers parsed case-insensitively), `SamsungDeviceInfo` (parses `/api/v2/`: name without the `[TV]` prefix, model, MAC, UUID, `TokenAuthSupport`, `PowerState`), `SamsungAdapter.discover()` (SSDP, then probe each answering host once) and `probe(host)` (2 s timeouts). TVs without token auth (2016–2017) get `ws://:8001`, others `wss://:8002`.
- `TvDevice.properties` / `DiscoveredTv.properties` carry adapter facts such as the protocol variant.
- App: welcome screen (OnboardingFlow step 1), discovery screen (DiscoveryScan indicator, DeviceCards as TVs arrive, 10 s timeout, tips Banner with "Scan again", IP field that validates and probes every adapter), a `ConnectionController` shared by every screen, the Wi-Fi multicast lock held while scanning, and `network_security_config.xml` allowing cleartext for the LAN info endpoint.
- Design system: `Banner`, `DeviceCard` and `DiscoveryScanIndicator` components, built to their specs in `docs/design/components/`. Icons come from `material-icons-extended` (Apache-2.0).
- The app opens on the remote when a TV is saved, otherwise on the welcome screen.

Deviation: the Android 16+ local network permission is not requested. It is not enforced at targetSdk 36; recorded as F-002 for when the target moves to 37.

## Review

- SSDP request and header parsing, the `/api/v2/` parser (reference TV fixture and non-Samsung answers), probe found / not found, and discovery probing each host once: 7 tests, run locally.
- AC-1 (TV listed within 10 s) and AC-2 (add by IP when discovery is blocked) need the reference TV; not yet checked on a device. The debug build's simulated TV answers discovery and `192.0.2.1`, so both paths can be tried without a TV; the Android build is verified by CI only (assemble and lint), not run.
- Permissions: INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE, VIBRATE, within NFR-5. No location permission.
