---
id: WI-023
title: Prepare releases for F-Droid and app stores
type: chore
status: review
branch: claude/project-thread-07pvc6
created: 2026-11-15
updated: 2026-11-15
related: [WI-017, WI-021, ADR-0003]
epic:
children: []
---

## Proposal (rationale)

Zapper ships only as an APK on GitHub Releases (SPEC NFR-15). ADR-0003 names F-Droid as the
natural store and Google Play as a possible later one. A store evaluation (2026-11-15) found the
code itself already fit for F-Droid (no proprietary or tracking dependency), but three pieces of
release plumbing missing for any store:

- no store metadata (description, icon, changelog) in the format F-Droid and other stores read;
- no Android App Bundle, which Google Play requires;
- no evidence that the release build is reproducible. F-Droid can publish the developer-signed
  APK, under the same key as GitHub, only if its own build from source matches it byte for byte.
  The same key everywhere also means one registration for Google's Android developer verification.

Obtainium already installs from GitHub Releases; users only need to be told.

## Decision

`accepted` — 2026-11-15, Antoine, choosing "prep PR + reproducibility check" after the store
evaluation. Submitting to any store is a separate decision and not part of this item.

## Plan

### Requirements

- `fastlane/metadata/android/en-US/` holds the title, short and full description, a 512×512 icon
  and the 1.0.0 changelog, in the layout F-Droid reads.
- The release workflow also builds and signs `app-release.aab`, attached to the workflow run (not
  to the GitHub Release, which stays APK-only for people installing by hand).
- A workflow rebuilds the release APK unsigned in two different environments (the release runner's
  Temurin JDK, and a Debian container with Debian's OpenJDK, like F-Droid's builder) from two
  different checkout paths, and fails if the APKs differ. Run for a tag, it also checks that the
  unsigned build matches the published signed APK once the signature is copied over, which is the
  exact check F-Droid runs.
- README and the landing page tell people they can follow releases with Obtainium.
- `docs/releasing.md` describes the AAB and the reproducibility check.

### Impacts

- `.github/workflows/release.yml`, a new `.github/workflows/reproducible.yml`, `README.md`,
  `site/index.html`, `docs/releasing.md`. No app code changes.
- Risk: the release build may turn out not to be reproducible; then the workflow fails and the
  fix is a new item.

### Approach

- Fastlane layout over F-Droid's in-repo `metadata/` because F-Droid, IzzyOnDroid and Play upload
  tools all read it.
- The reproducibility workflow runs on pull requests that touch the build, so a change that breaks
  reproducibility fails before it reaches a tag; by hand for any tag.
- The store icon is the launcher's adaptive icon flattened to its visible 72dp area, generated
  once from the same paths as `ic_launcher_foreground.xml`.

### Acceptance criteria / tests

- `reproducible.yml` passes on this pull request (two environments produce identical APKs), or it
  fails with a diffoscope report that names the difference.
- Run by hand for `v1.0.0` after merge, it reports whether F-Droid could ship the published APK.
- A release dry run attaches `zapper-X.Y.Z.aab` to the run, signed with the release key.
- `node .ai/rungs.mjs check` passes.

### Out of scope

- Submitting to F-Droid, Google Play or any other store, and registering for Android developer
  verification: decisions for Antoine, after this lands.
- Phone screenshots for the store listing: they must be taken on a real phone with a TV.
- Fixing reproducibility if the check fails: a new item.
- Android 17's local network permission (targetSdk 37): nothing deferred here, a later item.

## Execution

- Branch `claude/project-thread-07pvc6`, not `feature/WI-023-…`: the agent session that did this
  work may push only to that branch.

## Review

- Two environments produce identical APKs: PR #39's `Reproducible build` run built the unsigned
  release APK with Temurin on the runner and with Debian's OpenJDK 21.0.13 in `debian:trixie`, and
  both came out as SHA-256 `bd0a77fba4944f9646f21f1cec09d45fdeacdc889e1d93fb1909323a6f177cbd`.
- Not yet checked: the comparison with the published `v1.0.0` APK (needs the workflow on `main`
  and a run with `tag: v1.0.0`), and the signed AAB (needs a release dry run).
- `node .ai/rungs.mjs check` passes.
