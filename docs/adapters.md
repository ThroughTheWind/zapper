**Authoritative for:** how to add a TV brand to Zapper, from an empty module to a merged pull request.
**Not authoritative for:** why brands are adapters ([ADR-0002](decisions/ADR-0002-brand-adapters.md)), what each brand's protocol is ([research](research/tv-remote-protocols.md)), or the review rules for adapter code ([`.ai/rules/adapters.md`](../.ai/rules/adapters.md)).

# Adding a TV brand

A brand is one Gradle module, `adapters/<brand>`, that implements two interfaces from
`core/remote`: a `TvAdapter` that finds the brand's TVs, and a `TvSession` that talks to one of
them. The app never names a brand. It reads the capabilities a session declares and shows, hides or
disables controls from them, so a new brand needs **no UI change**.

You need to know the brand's local protocol (how to pair, and the key codes), and to own one of its
TVs for the last step. You do not need to know Android or Compose: adapters are plain Kotlin/JVM
and their tests run without an emulator.

**Start from the example.** [`ExampleAdapter.kt`](../core/remote/src/test/kotlin/io/github/throughthewind/zapper/core/remote/example/ExampleAdapter.kt)
is the smallest adapter that passes the shared contract, and
[`ExampleAdapterTest.kt`](../core/remote/src/test/kotlin/io/github/throughthewind/zapper/core/remote/example/ExampleAdapterTest.kt)
tests it. Both are compiled and run in CI, so they always match the contract. The Samsung adapter
([`adapters/samsung`](../adapters/samsung/)) is the full reference, and the Android TV adapter
([`adapters/androidtv`](../adapters/androidtv/)) shows pairing by a code the TV displays; the simulated TV
([`adapters/fake`](../adapters/fake/)) shows every optional feature without a network.

## 1. Create the module

1. Create `adapters/<brand>/build.gradle.kts`. Copy `adapters/fake/build.gradle.kts`, which depends
   on `:core:remote` only, and add what your protocol needs. OkHttp (WebSocket, HTTP, TLS) and
   kotlinx.serialization are already in `gradle/libs.versions.toml`. Any new library must be open
   source, GPL-3.0 compatible and free of tracking ([ADR-0003](decisions/ADR-0003-gpl-no-proprietary-dependencies.md)).
2. Add `include(":adapters:<brand>")` to `settings.gradle.kts`.
3. Put the code under the package `io.github.throughthewind.zapper.adapters.<brand>`.

The module may depend on `:core:model` and `:core:remote` and nothing else of Zapper's: never on
`:app`, `:core:data`, Android or another adapter. Anything Android (multicast locks, Wi-Fi state)
is held by the app around your calls.

## 2. Implement `TvAdapter`

Copy `ExampleAdapter` from the example and rename it. The members, from
[`TvAdapter.kt`](../core/remote/src/main/kotlin/io/github/throughthewind/zapper/core/remote/TvAdapter.kt):

| Member | Required | What it does |
| --- | --- | --- |
| `id` | yes | Stable id stored with every saved TV, such as `samsung`. **Never change it once shipped.** |
| `displayName` | yes | The brand name users see. |
| `createSession(device, credentials)` | yes | A session for one TV. It must not connect yet. |
| `probe(host)` | yes, in practice | Asks one address whether it is your brand's TV. "Add by IP address" calls it on every adapter; return `null` fast when it is not yours. |
| `discover()` | recommended | Emits your TVs as they answer (SSDP, mDNS). Without it, users add the TV by IP. Fill `uniqueId` (a UPnP UUID) or `macAddress` so the same TV seen twice is merged. |
| `canPowerOn`, `powerOn`, `powerOnHelp` | optional | Wake-on-LAN or the brand's own wake call. `powerOnHelp` is the one-line TV setting to enable, shown once when waking fails. |
| `powerState(device)` | optional | On or standby without pairing, so the chip can say "TV off". |

## 3. Implement `TvSession`

Copy `ExampleSession`. A session moves through `ConnectionState`:

```text
Disconnected ── connect() ──▶ Connecting ──▶ Connected
                                  │  ▲            │
                                  ▼  │            ▼
                         AwaitingPairing       Failed(TvError)
```

- **Pairing.** Set `AwaitingPairing` with the `PairingRequest` your TV needs: `ConfirmOnTv` when the
  user accepts a prompt on the TV, `EnterCode` when they type a code the TV shows. The pairing screen
  draws one layout per kind. Save the token with `credentials.saveToken(device.id, token)` so the
  next connection skips the prompt; other secrets (a certificate pin) go through `credentials.write`.
  The app encrypts them at rest.
- **Keys.** Map `RemoteKey` to your codes in one `when`, as `exampleCode()` and Samsung's
  [`SamsungKeys.kt`](../adapters/samsung/src/main/kotlin/io/github/throughthewind/zapper/adapters/samsung/SamsungKeys.kt)
  do. **A key you have not confirmed on a real TV maps to `null`.** `supports(key)` returns false for
  it, the remote disables it and edit mode hides it. A wrong code fails silently on the TV, which is
  worse than a disabled key.
- **Capabilities.** Declare only what the TV really does. `capabilities` is a `StateFlow`, so it can
  grow after connecting once the model is known.
- **Errors.** Throw `TvException(TvError.…)`, never a raw exception. Map a timeout or refused socket
  to `Unreachable`, a dropped connection to `ConnectionLost`, a "no" on the TV to `PairingRefused`,
  and a saved token the TV no longer accepts to `TokenRejected`. The app explains each one by type
  and reopens pairing for `TokenRejected` and `CertificateChanged`.
- **Local network only.** Talk to `device.host` and nothing else: no cloud API, no analytics, no
  update check (ADR-0003).

Optional features each pair a capability with the members that provide it:

| Capability | Members | Reference |
| --- | --- | --- |
| `KEYS` (every adapter) | `supports`, `send` | `ExampleSession` |
| `LONG_PRESS` | `send(key, KeyAction.PRESS)` then `RELEASE` | Samsung |
| `TEXT_INPUT` | `sendText`, and `tvKeyboard` when the TV says its keyboard is open | Samsung |
| `APP_LAUNCH` | `launchApp`, `installedApps`; app ids are `KnownApps` ids or the TV's own | Samsung, `SamsungApps.kt` |
| Turning the TV on | `TvAdapter.canPowerOn` and `powerOn`, which work while disconnected, so no session capability is needed | Samsung, `core/remote/WakeOnLan.kt` |

## 4. Test it

Every adapter's session test extends
[`TvSessionContract`](../core/remote/src/testFixtures/kotlin/io/github/throughthewind/zapper/core/remote/TvSessionContract.kt),
which checks what the UI relies on whatever the brand: connecting reaches `Connected`, keys are
supported, sending before connecting fails with `NotConnected`, and so on. Add your own cases for
**pairing once then reusing the token**, **a refused pairing** and **a rejected token**, as
`ExampleAdapterTest` does.

Test against your real protocol, not a mock of your own classes: start a fake TV on localhost and
let the session connect to it. `SamsungSessionTest` does this with OkHttp's MockWebServer for
WebSockets and TLS. Add the test dependencies the Samsung module uses to your build file.

```bash
./gradlew :adapters:<brand>:test
```

## 5. Register it

1. Add `implementation(project(":adapters:<brand>"))` to `app/build.gradle.kts`.
2. Add your adapter to the `adapters` list in `AppContainer`
   ([`ZapperApplication.kt`](../app/src/main/kotlin/io/github/throughthewind/zapper/ZapperApplication.kt)).
   That file and `BuildAdapters.kt` are the only app code allowed to name an adapter;
   `./gradlew checkAdapterBoundaries` fails if any other file imports one.

## 6. Check and open the pull request

```bash
./gradlew spotlessApply :adapters:<brand>:test checkAdapterBoundaries :app:assembleDebug lint
node .ai/rungs.mjs check
```

Then try it on a real TV with the debug APK from CI: discovery or add by IP, pairing, every key you
mapped, and a reconnect after turning the TV off and on. In the pull request, list the TV's brand,
model and year, which keys you confirmed and which you left `null`, and update the
[research](research/tv-remote-protocols.md) if you learned something it does not say. Mention
anything the TV needs enabled in its settings, so it can go into the brand's `powerOnHelp` or the
"No TV found" tips.

To build the UI without any TV, the debug build has a simulated TV: add it by IP with `192.0.2.1`.
