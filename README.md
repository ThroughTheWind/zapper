# Zapper

A free, open-source TV remote for Android phones, with a modern design and buttons and shortcuts
you can arrange the way you like. Samsung smart TVs come first; other brands plug in through
adapters.

> **Status:** early development. The scaffold can pair with a Samsung TV (2016 or later) by IP
> address and send keys. Discovery, layout editing and the real design are next.

## Principles

- **Free and open source**, under [GPL-3.0](LICENSE).
- **Local only.** Zapper talks to your TV over your home network and nothing else: no account,
  no ads, no analytics, no crash reporting ([ADR-0003](docs/decisions/ADR-0003-gpl-no-proprietary-dependencies.md)).
- **Any TV.** Every brand is an adapter behind one contract, and the UI only knows what a TV can
  do, never which brand it is ([ADR-0002](docs/decisions/ADR-0002-brand-adapters.md)).

## Supported TVs

| Brand | Status |
| --- | --- |
| Samsung (Tizen, 2016+) | In progress |
| Others | Planned. Open a [TV support request](.github/ISSUE_TEMPLATE/tv_support.md) |

## Building

You need JDK 17 or newer and the Android SDK (Android Studio installs both).

```bash
./gradlew :app:assembleDebug     # debug APK in app/build/outputs/apk/debug/
./gradlew test                   # unit tests
./gradlew spotlessApply          # format
```

The stack and the reasons for it are in [ADR-0001](docs/decisions/ADR-0001-kotlin-compose-multi-module.md).

## Project layout

| Module | What it holds |
| --- | --- |
| `app` | The Android app: screens and wiring |
| `core/model` | Keys, TVs, capabilities and the layout model (pure Kotlin) |
| `core/remote` | The `TvAdapter` contract and the adapter registry (pure Kotlin) |
| `core/data` | Saved TVs, layouts and pairing tokens |
| `core/designsystem` | The Compose theme and shared components |
| `adapters/samsung` | Samsung Tizen adapter (pure Kotlin) |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Adding a TV brand is the most useful thing you can do if
you own one.

## License

Zapper is free software under the [GNU General Public License v3.0](LICENSE).
