---
id: ADR-0003
title: License the app GPL-3.0 and ship no proprietary dependencies
status: accepted
date: 2026-11-13
supersedes:
superseded_by:
---

# ADR-0003 — License the app GPL-3.0 and ship no proprietary dependencies

## Context

The app is free and open source. Free remote apps on the Play Store are often repackaged with ads
and trackers. The app runs on a home network next to people's TVs, so trust matters, and F-Droid is
the natural store for it. F-Droid refuses apps that include proprietary libraries.

## Decision

- The app is licensed **GPL-3.0-only** (Antoine, 2026-11-13).
- The app includes **no proprietary or tracking dependency**: no Google Play Services, Firebase,
  Crashlytics, ads or analytics SDKs. Every dependency must have a GPL-3.0-compatible license.
- The app makes **no network calls outside the local network** unless the user starts them.
- Releases are built reproducibly enough for F-Droid. GitHub Releases is the first channel; the Play
  Store may follow.

## Consequences

**Good**

- Anyone who ships a modified version must publish its source, which blocks closed, ad-filled
  clones.
- Eligible for F-Droid, and easy to trust.

**Costs and risks**

- No Firebase crash reporting: crashes reach us only through issues.
- Apache-2.0 dependencies are fine, but some licenses (for example GPL-2.0-only) are not compatible
  and must be checked before a dependency is added.
- Companies that would only reuse code under a permissive license will not reuse the adapters.

## Alternatives considered

**Apache-2.0** lost because it lets anyone publish a closed, ad-filled fork.
**MIT** lost for the same reason, and has no patent grant.

## Revisit triggers

1. The adapters are split into a library meant for reuse by other apps (consider LGPL or Apache-2.0
   for that library only).

## Admission check

Against [the rule](README.md): (1) constrains future work ✅ · (2) a real alternative was rejected
with a stated reason ✅ · (3) reversing later costs meaningfully more ✅ · (4) not owned by another
document ✅ · (5) not an implementation detail ✅.
