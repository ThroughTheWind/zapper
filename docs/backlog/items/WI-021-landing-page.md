---
id: WI-021
title: Publish the landing page on GitHub Pages
type: docs
status: review
branch: claude/project-thread-238rhr
created: 2026-11-13
updated: 2026-11-14
related: [WI-017]
epic:
children: []
---

## Proposal (rationale)

Zapper has no web presence besides the repository. The 0.1.0 release (WI-017) needs a page that
says what the app does today, where the project is going, and where to download it. The landing
design system drafted earlier showed customization, app shortcuts and text input as available,
which Phase 1 does not ship.

## Decision

`accepted` — 2026-11-13, Antoine, by asking to review the landing page together and implement it as
GitHub Pages, adapted with its design system to the current phase and the planned ones.

## Plan

### Requirements

- A static page at `https://throughthewind.github.io/zapper/`, built from the landing design system.
- Only what the current release does is shown as working; planned work is labelled with its phase.
- Downloads point to GitHub Releases; links point to `ThroughTheWind/zapper` and to files that exist.
- No third-party requests except the GitHub API for the release label (ADR-0003's spirit for the site).

### Impacts

- New `site/`, `.github/workflows/pages.yml`, `docs/design/web/` (the landing design system).
- Two fast gates in `.ai/gates.toml`: generated tokens and the icon sprite stay in sync.
- One-time repository setting: Pages source is GitHub Actions.

### Approach

- `site/` deployed by Actions rather than Pages from `docs/`, so the Markdown docs are not published
  as a Jekyll site beside the page.
- Icons as an SVG sprite of the used symbols instead of the multi-megabyte icon font.
- The phone mockup mirrors `DefaultLayouts.standard`.

### Acceptance criteria / tests

- The page renders in light and dark, at 390px and 1280px, with no console errors.
- The demo works with pointer and keyboard; the arrow keys scroll the page unless the TV has focus.
- `node .ai/rungs.mjs check` passes, including `site-tokens` and `site-icons`.

### Out of scope

- A custom domain, screenshots of the real app, and translations. Nothing deferred.

## Execution

Branch `claude/project-thread-238rhr` (the session's assigned branch, instead of
`feature/WI-021-…`).

The first version of the page covered Phase 1 only, with app shortcuts as a "coming in Phase 2" placeholder.
On 2026-11-14 Antoine asked for it to reflect Phase 3 before merging, once Phases 2 and 3 were
built (PR #15, PR #16). The features, the phone mockup (Keyboard key, Netflix and YouTube
shortcuts, three pages), the demo and the roadmap (Phases 1 to 3 done, 1.0 next) now match the
app after those PRs. Merge this after them.

The page ships with release 0.2.0 (Phases 1 to 3), so the version label's fallback is `v0.2.0`;
the live label still comes from the latest release.

## Review

Checked with Playwright in Chromium: desktop dark and light, phone dark; demo D-pad, OK and volume.
