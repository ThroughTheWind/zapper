# Changelog

All notable changes to Zapper are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Power turns the TV on when it is off, with Wake-on-LAN. The status chip says when the TV is off
  or in standby, and a one-time tip explains the TV setting to enable when it does not wake.
- App shortcuts: Netflix and YouTube on the main page, and an Apps page with Prime Video,
  Disney+, Apple TV, Spotify and Plex. The app opens under whichever id the TV lists, and a
  shortcut to an app the TV lacks says so.
- A Keyboard key opens the phone keyboard to type into the TV's search fields. It also opens by
  itself when the TV shows its own keyboard.

## [0.1.0] - 2026-11-13

### Added

- An app icon, with a monochrome version for themed icons.
- Discovery also sweeps the phone's Wi-Fi subnet, so the TV is found on routers that block
  multicast.
- Finds Samsung TVs on the Wi-Fi automatically, or by IP address, with tips when nothing is found.
- The phone's volume keys change the TV volume while a TV is connected; a switch in the TV sheet
  turns this off.
- The default remote: power, source, menu, a round D-pad you can hold or swipe, volume and
  channel rockers, media controls, and a number pad on its own page. Keys a TV does not support
  are greyed out.
- Reconnects by itself when the app opens, when Wi-Fi returns or when the TV comes back, and
  lets go of the TV when the app is in the background. A status chip shows the connection.
- Saved TVs: switch between them, rename, forget, see their details, and pick the one the app
  opens on.
- Guided pairing: the app shows what to accept on the TV, explains a refusal or a timeout, and
  pairs again by itself when the TV forgets the phone.
- Pairing secrets are encrypted with a key kept in the Android Keystore, and the TV's certificate
  is pinned on first pairing, so another device cannot pretend to be the TV.
- Project scaffold: Kotlin and Jetpack Compose app, brand adapter contract, and a first Samsung
  Tizen adapter that pairs and sends keys.

[Unreleased]: https://github.com/ThroughTheWind/zapper/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/ThroughTheWind/zapper/releases/tag/v0.1.0
