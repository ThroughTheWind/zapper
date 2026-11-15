# Zapper

A free, open-source TV remote for Android phones, with a modern design and buttons and shortcuts
you can arrange the way you like. Samsung smart TVs come first; other brands plug in through
adapters.

> **Status:** 0.1.0, the basic Samsung remote. It finds a Samsung TV (2016 or later) on the
> Wi-Fi, pairs once and controls it with a fixed layout. Text input, app shortcuts and power on
> are next, then layout editing ([roadmap](docs/ROADMAP.md)).

## Install

Download the latest `zapper-X.Y.Z.apk` from [Releases](https://github.com/ThroughTheWind/zapper/releases/latest)
on your phone and open it. Needs Android 8.0 or later. Each release lists the APK's SHA-256 and
the signing certificate's fingerprint.

## Principles

- **Free and open source**, under [GPL-3.0](LICENSE).
- **Local only.** Zapper talks to your TV over your home network and nothing else: no account,
  no ads, no analytics, no crash reporting ([ADR-0003](docs/decisions/ADR-0003-gpl-no-proprietary-dependencies.md)).
- **Any TV.** Every brand is an adapter behind one contract, and the UI only knows what a TV can
  do, never which brand it is ([ADR-0002](docs/decisions/ADR-0002-brand-adapters.md)).

## Documentation

- [MVP spec](docs/SPEC.md) and [roadmap](docs/ROADMAP.md)
- [Design system](docs/design/README.md)
- [TV protocol research](docs/research/tv-remote-protocols.md)
- [Decisions](docs/decisions/README.md) and [backlog](docs/backlog/BACKLOG.md)

## Supported TVs

| Brand | Status |
| --- | --- |
| Samsung (Tizen, 2016+) | Supported since 0.1.0 |
| Others | Planned. Open a [TV support request](.github/ISSUE_TEMPLATE/tv_support.md) |

## Building

You need JDK 17 or newer and the Android SDK (Android Studio installs both).

```bash
./gradlew :app:assembleDebug     # debug APK in app/build/outputs/apk/debug/
./gradlew test                   # unit tests
./gradlew spotlessApply          # format
```

Releases are built, signed and published by CI from a tag; see [releasing](docs/releasing.md).
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
