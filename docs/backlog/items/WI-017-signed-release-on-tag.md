---
id: WI-017
title: Publish a signed APK to GitHub Releases on each tag
type: chore
status: review
branch: claude/project-thread-vpshn1
created: 2026-11-13
updated: 2026-11-13
related: [SPEC-AC-21, SPEC-NFR-15, ADR-0003]
epic:
children: []
---

## Proposal (rationale)

Distribution for the MVP is GitHub Releases (NFR-15). CI already builds a debug APK; a tag should produce a signed, F-Droid-friendly release APK.

Phase 4 — Release 1.0 ([roadmap](../../ROADMAP.md)). Moved forward on 2026-11-13: Antoine asked to
release 0.1.0 at the end of Phase 1, and that release needs this pipeline.

## Decision

`accepted` — 2026-11-13, Antoine, by asking to turn the MVP spec into phases with the basics first and customization later.

## Plan

### Requirements

- One source for the version: `zapper` in `gradle/libs.versions.toml`. `versionCode` is computed
  from it, never typed.
- Release signing reads the keystore and passwords from the environment; nothing secret is
  committed, and a build without them still produces an unsigned APK.
- Pushing `vX.Y.Z` runs tests, builds and signs the release APK, verifies it, and publishes it to
  GitHub Releases with notes from `docs/releases/X.Y.Z.md` plus the `CHANGELOG.md` section.
- A manual run of the workflow is a dry run that publishes nothing, so the secrets can be checked
  before the first tag.
- The procedure, including creating the key and the secrets, is written down for the maintainer.

### Impacts

- `app/build.gradle.kts`, `gradle/libs.versions.toml`: version derivation, signing config, `-dev`
  suffix on debug version names.
- `.github/workflows/release.yml` (new); `ci.yml` also builds the unsigned release APK, so R8 and
  release lint failures show up on pull requests rather than on a tag.
- `scripts/release-notes.sh`, `docs/releasing.md`, `docs/releases/0.1.0.md`, the `cut-release`
  skill (which `work-item` already named), `CHANGELOG.md` cut for 0.1.0.
- No runtime dependency added (ADR-0003 holds).

### Approach

- Tag-triggered workflow with `gh release create`, rather than a third-party release action: the
  `dev` pre-release already uses `gh`, and it keeps the workflow free of extra actions.
- Keystore in a base64 secret, decoded to the runner's temp dir. PKCS12, so one password.
- The workflow checks the artifact, not the build script: `apksigner verify`, `aapt2 dump badging`
  for the version name and debuggable flag, then publishes the SHA-256 of the APK and of the
  signing certificate in the notes.
- `versionCode = MAJOR * 1_000_000 + MINOR * 1_000 + PATCH`, monotonic for every SemVer bump.

### Acceptance criteria / tests

- CI on the pull request builds `:app:assembleRelease` with R8 and passes.
- `scripts/release-notes.sh 0.1.0 a b` exits 0 and prints the notes, changes and checksums;
  for a version without notes or without a changelog section it exits 1 with the reason.
- A dry run (manual workflow run with the secrets set) produces a signed `zapper-0.1.0.apk` whose
  `apksigner verify` passes and whose version name is `0.1.0`.
- Pushing `v0.1.0` publishes a GitHub Release with `zapper-0.1.0.apk` and its `.sha256` (AC-21).
- A tag that does not match the version is refused before the build.

### Out of scope

- F-Droid submission and reproducible-build verification (NFR-15 keeps F-Droid as a design
  constraint only for the MVP).
- Google Play and app bundles.
- Bumping the version for the next release; that is part of cutting that release.

## Execution

- 2026-11-13: built on the session branch `claude/project-thread-vpshn1` rather than
  `feature/WI-017-…`, because the agent environment can push only to that branch.
- No Android SDK in the agent environment, so the release build was first exercised by CI on the
  pull request.
- The 0.1.0 changelog cut drops the two "Fixed" entries from WI-020: they fixed features that had
  never been released, so a first release lists only what it adds.

## Review
