---
id: WI-019
title: Find the TV when the network drops SSDP
type: feature
status: done
branch: feature/WI-019-discovery-fallback
created: 2026-11-13
updated: 2026-11-13
related: [WI-003, WI-001, SPEC-F1, SPEC-AC-1]
epic:
children: []
---

## Proposal (rationale)

On the first run against the reference TV (WI-001), Antoine reported that the TV does not show up in discovery. WI-003 only sent one SSDP search target, and research §1 already warns that SSDP is often dropped by mesh routers, ISP boxes and AP isolation.

## Decision

`accepted` — 2026-11-13, Antoine, by reporting the failure while testing WI-001 under his request to execute WI-001 to WI-007.

## Plan

### Requirements

- SSDP searches every target a Samsung TV answers: Samsung's remote control receiver, DIAL and the UPnP media renderer (research §2.1).
- When multicast finds nothing, a sweep of the phone's own subnet for the TV's info port finds it (research §1).
- Each candidate host is confirmed by the TV's info endpoint and listed once.

### Impacts

- `adapters/samsung` only (`Ssdp`, `SamsungAdapter.discover`, new `SubnetScan`). No contract change.

### Approach

- SSDP and the sweep run in parallel and feed one probe step that dedupes by host and by TV identity.
- The sweep uses plain `java.net` (the adapter stays a JVM module): site-local IPv4 interfaces, /22 or smaller, TCP connect to 8001 with a 400 ms timeout, 48 at a time, nearest addresses first.

### Acceptance criteria / tests

- Unit: a TV reachable only through the sweep is listed; a TV found by both paths is listed once; the sweep covers the subnet nearest first and skips networks larger than /22; it finds a host that accepts the port.
- AC-1 on the reference TV: listed within 10 s. Needs Antoine.

### Out of scope

- mDNS and other brands' ports, until a second adapter exists. Nothing else deferred.

## Execution

Branch `feature/WI-019-discovery-fallback`.

- `Ssdp.search` takes a list of search targets and sends all of them each round.
- `SubnetScan` sweeps the phone's site-local networks for the info port; `SamsungAdapter.discover` merges it with SSDP.

## Review

- Unit tests on the JVM (65, all passing; 4 new in `SamsungDiscoveryTest`) cover the four criteria above.
- AC-1 on the reference TV: not checked yet. The root cause on Antoine's network is not confirmed; this change covers the two likeliest causes (a search target the TV does not answer, and multicast being dropped).
