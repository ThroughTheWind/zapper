# Changelog

All notable changes to Zapper are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

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
- A simulated TV in debug builds, reachable by IP at `192.0.2.1`, for trying the app without a TV, and a
  second one at `192.0.2.2` that pairs with the code `1234`.
- Project scaffold: Kotlin and Jetpack Compose app, brand adapter contract, and a first Samsung
  Tizen adapter that pairs and sends keys.
