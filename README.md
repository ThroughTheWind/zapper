# Zapper

A free, open-source TV remote for Android phones, with a modern design and buttons and shortcuts
you can arrange the way you like. Samsung smart TVs come first; other brands plug in through
adapters.

> **Status:** 1.0.0. It finds a Samsung TV (2016 or later) on the Wi-Fi, pairs once, types into
> it, opens apps, turns it on, and lets you rearrange every key, page and colour. It works with
> TalkBack and large text, and other TV brands can be added with the
> [adapter guide](docs/adapters.md). More brands come next ([roadmap](docs/ROADMAP.md)).

## Install

Download the latest `zapper-X.Y.Z.apk` from [Releases](https://github.com/ThroughTheWind/zapper/releases/latest)
on your phone and open it. Needs Android 8.0 or later. Each release lists the APK's SHA-256 and
the signing certificate's fingerprint.

To get updates automatically, add `https://github.com/ThroughTheWind/zapper` to
[Obtainium](https://github.com/ImranR98/Obtainium), which installs straight from these releases.

## Principles

- **Free and open source**, under [GPL-3.0](LICENSE).
- **Local only.** Zapper talks to your TV over your home network and nothing else: no account,
  no ads, no analytics, no crash reporting ([ADR-0003](docs/decisions/ADR-0003-gpl-no-proprietary-dependencies.md)).
- **Any TV.** Every brand is an adapter behind one contract, and the UI only knows what a TV can
  do, never which brand it is ([ADR-0002](docs/decisions/ADR-0002-brand-adapters.md)).

## Documentation

- Website: <https://throughthewind.github.io/zapper/> ([source](site/README.md))
- [MVP spec](docs/SPEC.md) and [roadmap](docs/ROADMAP.md)
- [Design system](docs/design/README.md) and the [landing page design system](docs/design/web/README.md)
- [TV protocol research](docs/research/tv-remote-protocols.md)
- [Decisions](docs/decisions/README.md) and [backlog](docs/backlog/BACKLOG.md)

## Supported TVs

| Brand | Status |
| --- | --- |
| Samsung (Tizen, 2016+) | Supported since 0.1.0 |
| Others | Planned. [Vote for the next brand](https://github.com/ThroughTheWind/zapper/issues/36) with a 👍, or open a [TV support request](https://github.com/ThroughTheWind/zapper/issues/new?template=tv_support.md) |

## Building

You need JDK 17 or newer and the Android SDK (Android Studio installs both).

```bash
./gradlew :app:assembleDebug     # debug APK in app/build/outputs/apk/debug/
./gradlew test                   # unit tests
./gradlew spotlessApply          # format
```

Releases are built, signed and published by CI from a tag; see [releasing](docs/releasing.md). Unmerged changes can be shipped to testers as [preview builds](docs/previews.md).
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
you own one: the [guide for adding a TV brand](docs/adapters.md) walks through it.

## License

Zapper is free software under the [GNU General Public License v3.0](LICENSE).
