---
id: WI-020
title: Polish the app after the first TV test
type: fix
status: review
branch: feature/WI-020-first-test-polish
created: 2026-11-13
updated: 2026-11-13
related: [WI-001, WI-003, WI-006, SPEC-F1, SPEC-F4]
epic:
children: []
---

## Proposal (rationale)

The first run against the reference TV (WI-001) worked, and Antoine reported three problems: the remote page scrolls because its grid takes the full screen height and ignores the top bar; the TV name shows raw HTML entities such as `&amp;`; and the app has no icon.

## Decision

`accepted` — 2026-11-13, Antoine, by asking for the three fixes and a rebuild after testing WI-001.

## Plan

### Requirements

- A remote page fits the space under the top bar on a phone without scrolling. It scrolls only when a cell would fall below the minimum touch target.
- TV names decode HTML entities (`&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`, numeric references), including for TVs already saved with an escaped name.
- The launcher shows an adaptive Zapper icon with a monochrome layer for themed icons.

### Impacts

- `core/model` (new `HtmlEntities`), `adapters/samsung` (`SamsungDeviceInfo`), `core/data` (`ZapperRepository`), `app` (`RemoteCanvas`, manifest, launcher resources).

### Approach

- The grid cell size is the smallest of the width fit, the height fit and the design maximum, measured with `BoxWithConstraints` inside the pager page.
- The Samsung adapter decodes the name it reads from the TV; the repository decodes on read, so names saved before this fix display correctly without a migration.
- The icon is a vector adaptive icon: a remote with a D-pad and a rocker on the brand's indigo. The design system has no logo, so the drawing is original and uses only theme colors.

### Acceptance criteria / tests

- Unit: entity decoding (named, numeric, unknown entities left as is); a Samsung TV name with `&amp;` is decoded.
- On the reference phone: the remote page does not scroll, the TV name reads correctly, the launcher shows the icon. Needs Antoine.

### Out of scope

- A designed brand logo. Nothing else deferred.

## Execution

Branch `feature/WI-020-first-test-polish`.

- `RemotePageGrid` sizes cells to fit both width and height of the page.
- `String.decodeHtmlEntities()` in `core/model`, used by `SamsungDeviceInfo.parse` and by `ZapperRepository` when it reads saved TVs.
- Adaptive launcher icon (`mipmap-anydpi/ic_launcher*.xml`, vector foreground and monochrome layers), wired in the manifest.

## Review

- Unit tests on the JVM: 69, all passing (4 new: 3 in `HtmlEntitiesTest`, 1 in `SamsungDiscoveryTest`).
- The layout and the icon are checked by CI's build and lint only; on-device checks need Antoine.
