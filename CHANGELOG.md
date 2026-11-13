# Changelog

All notable changes to Zapper are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Finds Samsung TVs on the Wi-Fi automatically, or by IP address, with tips when nothing is found.
- Guided pairing: the app shows what to accept on the TV, explains a refusal or a timeout, and
  pairs again by itself when the TV forgets the phone.
- Pairing secrets are encrypted with a key kept in the Android Keystore, and the TV's certificate
  is pinned on first pairing, so another device cannot pretend to be the TV.
- A simulated TV in debug builds, reachable by IP at `192.0.2.1`, for trying the app without a TV, and a
  second one at `192.0.2.2` that pairs with the code `1234`.
- Project scaffold: Kotlin and Jetpack Compose app, brand adapter contract, and a first Samsung
  Tizen adapter that pairs and sends keys.
