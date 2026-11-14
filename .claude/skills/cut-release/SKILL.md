---
name: cut-release
description: >-
  Ship a Zapper version to GitHub Releases: set the version, cut the changelog section, write the
  release notes, merge, and tell the maintainer the tag to push. Use when asked to "cut / ship /
  prepare / publish release X.Y.Z", "release 0.x", or "package the release". Executing a backlog
  item is /work-item; this skill only ships what is already merged.
disable-model-invocation: true
---

# Cut a release

**Authority:** [`docs/releasing.md`](../../../docs/releasing.md). Follow its "Cutting a release"
steps; this skill adds only what an agent gets wrong.

1. **Re-derive readiness from git, not the board.** The phase's items are merged on `main` and the
   latest CI run on `main` is green. If not, stop and say what is missing.
2. **One pull request, branch `chore/release-X.Y.Z`,** holding only the version, the changelog cut
   and `docs/releases/X.Y.Z.md`. No code.
3. **Prove the notes render before merging:**
   `scripts/release-notes.sh X.Y.Z check check` must exit 0 and print the notes.
4. **Do not push the tag yourself unless the maintainer said to in this session.** A pushed tag
   publishes a release that users install and that cannot be quietly withdrawn. Hand over the two
   `git tag` / `git push` lines instead.
5. **Never create the release in the GitHub UI or API**: the workflow creates it, and fails if it
   already exists.
