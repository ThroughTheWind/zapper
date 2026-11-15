**Authoritative for:** how a Zapper version is numbered, signed, and published to GitHub Releases.
**Not authoritative for:** what goes into a version (the [roadmap](ROADMAP.md)) or what changed (the [changelog](../CHANGELOG.md)).

# Releasing Zapper

Zapper ships as a signed APK on [GitHub Releases](https://github.com/ThroughTheWind/zapper/releases)
(spec NFR-15). Pushing a tag `vX.Y.Z` runs [`release.yml`](../.github/workflows/release.yml), which
tests, builds, signs and verifies the APK, then publishes it with its release notes. The `dev`
pre-release is separate: CI refreshes it with a debug build on every push to `main`.

## Versions

- The version lives once, as `zapper` in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml).
- The Android `versionCode` is derived from it in `app/build.gradle.kts` as
  `MAJOR * 1_000_000 + MINOR * 1_000 + PATCH`, so 0.1.0 is 1000. Never type it by hand.
- Debug builds append `-dev` to the version name and install as a separate app
  (`io.github.throughthewind.zapper.debug`).
- Versions follow [Semantic Versioning](https://semver.org/). Before 1.0, a minor version is a
  roadmap milestone and a patch version is a fix.

## One-time setup: the signing key

Only the maintainer does this, once. **The key is the app's identity:** Android installs an update
only if it is signed with the same key, so a lost key means every user has to uninstall and
reinstall. Keep the keystore file and its password in a password manager, and a second copy
somewhere offline.

1. Create the keystore (needs a JDK, which Android Studio includes). Pick a long password; it is
   asked twice, and the same one is used for the key.

   ```bash
   keytool -genkeypair -v -storetype PKCS12 -keystore zapper-release.jks \
     -alias zapper -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Zapper"
   ```

2. Add four repository secrets, under *Settings → Secrets and variables → Actions → New repository
   secret*, or with the GitHub CLI:

   | Secret | Value |
   | --- | --- |
   | `ZAPPER_KEYSTORE_BASE64` | The keystore file, base64-encoded on one line |
   | `ZAPPER_KEYSTORE_PASSWORD` | The keystore password |
   | `ZAPPER_KEY_ALIAS` | `zapper` |
   | `ZAPPER_KEY_PASSWORD` | The same password (PKCS12 keystores use one password) |

   ```bash
   # macOS: use `base64 -i zapper-release.jks` instead of `base64 -w0 zapper-release.jks`.
   base64 -w0 zapper-release.jks | gh secret set ZAPPER_KEYSTORE_BASE64 --repo ThroughTheWind/zapper
   gh secret set ZAPPER_KEYSTORE_PASSWORD --repo ThroughTheWind/zapper   # prompts for the value
   gh secret set ZAPPER_KEY_ALIAS --repo ThroughTheWind/zapper --body zapper
   gh secret set ZAPPER_KEY_PASSWORD --repo ThroughTheWind/zapper        # prompts for the value
   ```

   On Windows PowerShell, encode with
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes("zapper-release.jks")) | Set-Clipboard` and
   paste it into the web form.

3. Check the setup with a dry run: *Actions → Release → Run workflow* on `main`. It builds and
   signs the APK and attaches it to the run without publishing anything. If a secret is missing,
   the run says which one.

Never commit the keystore. `*.jks` and `*.keystore` are in `.gitignore`.

## Cutting a release

1. **Check `main` is ready:** the roadmap phase for this version is merged and CI is green.
2. **Prepare it on a branch** `chore/release-X.Y.Z`, in one pull request:
   - set `zapper = "X.Y.Z"` in `gradle/libs.versions.toml` (skip if it already says so);
   - in [`CHANGELOG.md`](../CHANGELOG.md), rename `## [Unreleased]` to `## [X.Y.Z] - YYYY-MM-DD`
     and add a fresh empty `## [Unreleased]` above it;
   - write `docs/releases/X.Y.Z.md`: a title line, then what the release is for in plain words,
     how to install it, and its known limits. The workflow appends the changelog section and the
     checksums.
3. **Merge it**, and optionally run the dry run above on `main` to see the exact release notes in
   the run summary.
4. **Tag the merge commit** on `main` and push the tag:

   ```bash
   git switch main && git pull
   git tag -a vX.Y.Z -m "Zapper X.Y.Z"
   git push origin vX.Y.Z
   ```

   Do not create the release from the GitHub web page: the workflow creates it, and fails if the
   release already exists.
5. **Check the release page:** `zapper-X.Y.Z.apk` and its `.sha256` are attached, and the APK
   installs over the previous release on a phone.

The workflow refuses a tag that does not match the version, a version without release notes or a
changelog section, missing secrets, and an APK that is unsigned, debuggable or carries the wrong
version.

If the workflow failed **before publishing** (no release on the Releases page), fix it on `main`,
then delete the tag and push it again on the fixed commit. Re-running the failed run does not help:
it runs the workflow as it was at the tagged commit.

```bash
git push origin :refs/tags/vX.Y.Z && git tag -d vX.Y.Z
git switch main && git pull
git tag -a vX.Y.Z -m "Zapper X.Y.Z" && git push origin vX.Y.Z
```

If a release went out broken, do not move or reuse its tag: fix on `main` and release `X.Y.Z+1`.
