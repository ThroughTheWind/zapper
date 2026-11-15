**Authoritative for:** how a branch or pull request is shipped to testers before it merges, and what testers are told.
**Not authoritative for:** releases and their signing key ([releasing](releasing.md)).

# Preview builds

A preview lets people try a change, typically a new TV brand, on their own TV before it merges.
The [Preview workflow](../.github/workflows/preview.yml) builds a branch or pull request and
publishes it as a GitHub **pre-release** with the APK attached: a plain public download, unlike CI
artifacts, which need a GitHub login and expire.

| | Release | Preview | `dev` pre-release |
| --- | --- | --- | --- |
| App name | Zapper | Zapper Preview | Zapper (debug) |
| Application id | `…zapper` | `…zapper.preview` | `…zapper.debug` |
| Built from | a `vX.Y.Z` tag on `main` | any branch or pull request, on demand | every push to `main` |
| Build | release, shrunk | release, shrunk | debug, with the simulated TV |
| Signed with | release key | **preview key** | the CI runner's throwaway debug key |
| Tag | `vX.Y.Z` | `X.Y.Z-preview.N` | `dev` (moved each time) |

Because the preview has its own application id, it installs **beside** the release: testers keep a
working remote while they try the preview. Each preview's `versionCode` is its run number `N`, so a
newer preview installs over an older one even when it comes from a different branch.

## One-time setup: the preview key

Previews are signed with their own key, never the release key. The workflow runs code that has not
been reviewed yet, and the release key is the app's identity (see [releasing](releasing.md)); a
leaked preview key can only sign a fake *preview*. Losing it costs testers one reinstall of Zapper
Preview, nothing more, but keep it in your password manager next to the release key anyway.

1. Create the keystore:

   ```bash
   keytool -genkeypair -v -storetype PKCS12 -keystore zapper-preview.jks \
     -alias zapper-preview -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Zapper Preview"
   ```

2. Add four repository secrets, as for the release key:

   | Secret | Value |
   | --- | --- |
   | `ZAPPER_PREVIEW_KEYSTORE_BASE64` | The keystore file, base64-encoded on one line |
   | `ZAPPER_PREVIEW_KEYSTORE_PASSWORD` | The keystore password |
   | `ZAPPER_PREVIEW_KEY_ALIAS` | `zapper-preview` |
   | `ZAPPER_PREVIEW_KEY_PASSWORD` | The same password (PKCS12 keystores use one password) |

   ```bash
   # macOS: use `base64 -i zapper-preview.jks` instead of `base64 -w0 zapper-preview.jks`.
   base64 -w0 zapper-preview.jks | gh secret set ZAPPER_PREVIEW_KEYSTORE_BASE64 --repo ThroughTheWind/zapper
   gh secret set ZAPPER_PREVIEW_KEYSTORE_PASSWORD --repo ThroughTheWind/zapper   # prompts for the value
   gh secret set ZAPPER_PREVIEW_KEY_ALIAS --repo ThroughTheWind/zapper --body zapper-preview
   gh secret set ZAPPER_PREVIEW_KEY_PASSWORD --repo ThroughTheWind/zapper        # prompts for the value
   ```

If a secret is missing, the run says which one after the build, before anything is published.

## Publishing a preview

*Actions → Preview → Run workflow*, leave "Use workflow from" on `main`, and fill in:

- **ref**: a pull request number (`42`) or a branch name. Pull requests from forks work too.
- **issues** (optional): issues to announce the build on, such as the brand's voting issue
  (`24,33`). When **ref** is a pull request, the pull request is always announced on as well.
- **notes** (optional): what testers should try, in a sentence or two. It goes on the release page
  and in the announcement.

Or from a terminal:

```bash
gh workflow run preview.yml --repo ThroughTheWind/zapper -f ref=42 -f issues=24 \
  -f notes="Pair with an Android TV or Google TV and try the D-pad, volume and text input."
```

The run tests and builds the branch, checks the preview does not contain the simulated TV, signs
the APK, checks it is the non-debuggable `…zapper.preview` app with the expected version, then
publishes the pre-release and comments on the pull request and issues. Everyone subscribed to them
is notified, which includes everyone who commented. A reaction alone does not subscribe anyone, so
when a voting issue asks for votes as reactions, also ask voters to subscribe.

The branch's code runs in a job that has no secrets, cannot write to the repository and cannot
write the Gradle cache that `main` uses. Signing and publishing happen in a second job that never
checks the branch out. Still, the APK is the branch's code: only preview a fork's pull request after
reading it, because testers install what it builds.

Pre-releases are never marked "latest", so the release download and Obtainium users following
stable releases are unaffected. Old previews can be deleted from the Releases page when they are no
longer useful; delete the tag with them (`gh release delete <tag> --cleanup-tag`).

## What testers do

The release page and the announcement say this; it is repeated here to link to.

1. On the phone, open the release page and download `zapper-preview-….apk`. Open it and allow the
   browser to install apps if Android asks. It appears as **Zapper Preview**, next to Zapper.
2. To get every new preview automatically, use [Obtainium](https://obtainium.imranr.dev/): add
   `https://github.com/ThroughTheWind/zapper`, turn on *Include prereleases*, and set *Filter release
   titles by regular expression* to `^Zapper Preview`. To follow one brand's previews only, include
   its pull request in the filter, for example `^Zapper Preview .*#42`.
3. Comment on the issue or pull request with the TV's brand, model and year, and what worked and
   what did not.

A preview has its own settings and paired TVs: pairing it with a TV is separate from pairing
Zapper. Uninstalling Zapper Preview removes only the preview.
