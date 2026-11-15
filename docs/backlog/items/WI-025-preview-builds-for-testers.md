---
id: WI-025
title: Ship preview builds of unmerged branches to testers
type: chore
status: review
branch: claude/project-thread-2itz49
created: 2026-11-15
updated: 2026-11-15
related: [ADR-0003]
epic:
children: []
---

## Proposal (rationale)

The next adapters (Android TV, older Samsung sets) can only be proven on TVs the maintainer does
not own. The people who voted for them do own them, but today the only way to hand them a build is
a CI artifact, which needs a GitHub login and expires, or the `dev` debug pre-release, which is
`main` only, includes the simulated TV, and is signed with a throwaway key so it never updates in
place. Without a way to ship a branch before merge, an adapter merges untested on real hardware.

## Decision

`accepted` — 2026-11-15, Antoine, by saying yes to a workflow that publishes a preview pre-release
from a pull request or branch on demand.

## Plan

### Requirements

- A `preview` build type: release code and shrinking, no simulated TV, application id suffix
  `.preview`, app name "Zapper Preview", version `X.Y.Z-preview.N` with `versionCode` N.
- A hand-run workflow that takes a pull request number or branch, builds it, signs the APK with a
  separate preview key, verifies it, and publishes it as a GitHub pre-release with its checksum.
- Optionally comments on the pull request and on listed issues with the link.
- Works with Obtainium's pre-release tracking without disturbing users who follow releases.
- Documented: one-time key setup, how to run it, what testers do.

### Impacts

- `app/build.gradle.kts`, a `preview` resource override, `.github/workflows/preview.yml`, one CI
  step, `docs/previews.md`, links from `README.md` and `docs/releasing.md`.
- Four new repository secrets, `ZAPPER_PREVIEW_*`, added by the maintainer.
- Risk: the workflow builds code that has not been reviewed. Contained by building in a job with no
  secrets, no write token and a read-only Gradle cache, and signing in a job that never checks the
  branch out.

### Approach

- **Separate key, not the release key.** Previews are a different application id, so the key does
  not have to match the release for updates. A separate key means unreviewed code is never signed
  as the real app's identity, and losing it costs one reinstall of the preview.
- **Sign after the build** with `apksigner` instead of a Gradle signing config, so no secret is in
  the environment while the branch's build scripts run.
- **Version code is the workflow run number**, which only grows, so any newer preview installs over
  any older one whichever branch it came from.
- **Tag is the version name without a `v`**, so it equals the installed version name (Obtainium's
  version check) and cannot match `release.yml`'s `v*.*.*` trigger.
- Considered and rejected: reusing the `dev` debug pre-release (debug build, simulated TV, new key
  on every run); one moving `preview` tag (testers on two brands would overwrite each other's build).

### Acceptance criteria / tests

- CI builds `:app:assemblePreview` on every pull request, including this one.
- `actionlint` (with shellcheck) passes on `preview.yml` and `ci.yml`; `ktlint` passes on the build
  script; `node .ai/rungs.mjs check` passes.
- After merge and secret setup, a run on a pull request publishes a pre-release whose APK
  `aapt2 dump badging` shows `…zapper.preview`, label "Zapper Preview", the run number as version
  code, and not debuggable (the workflow checks all four), installs beside Zapper on a phone, and
  comments on the pull request. Needs Antoine: the workflow can only be run once it is on `main`.

### Out of scope

- Publishing previews automatically on every push to a pull request; on demand is enough while
  previews are a few per adapter. Nothing deferred.
- Cleaning up old previews automatically; documented as a manual step. Nothing deferred.
- A distinct launcher icon for the preview. Nothing deferred.

## Execution

Branch `claude/project-thread-2itz49`. The branch name follows the session's branch rule rather
than `chore/WI-025-…`.

No Android SDK is reachable from the session that wrote this, so the Gradle change is proven by CI's
new `Preview APK (unsigned)` step, not locally. The workflow itself cannot run before it is on
`main` (GitHub only offers hand-run workflows from the default branch).

## Review

Pending CI and the first real run after merge.
