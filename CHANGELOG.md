# Changelog

All notable changes to Zapper are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Fixed

- The remote fits under the top bar instead of scrolling.
- TV names show characters such as `&` instead of `&amp;`.

### Added

- Source and HDMI 1 to 4 keys in the edit mode catalog (System group). HDMI keys switch
  straight to a port on Samsung TVs that support it; some models ignore them.
- Edit mode, from the pencil on the remote: drag keys to move them, resize them with a handle,
  remove them with the badge or the trash zone, and add keys, rockers, the D-pad, app shortcuts,
  spacers and labels from a catalog. Each key can get a label, an icon, a colour and repeat while
  held. Undo reverts edits one at a time; Done saves, Back asks before discarding.
- Pages: add, rename, reorder and delete them in edit mode.
- Each TV keeps its own layout, which can be reset to the default or copied from another TV.
- Export a layout as a `.json` file through the share sheet, and import one. Buttons this version
  cannot run are kept as placeholders.
- Settings: theme (System, Light, Dark, OLED black), colour (Dynamic or six presets), key shape,
  haptics (Off, Light, Strong), click sound, repeat speed, saved TVs, layout and About. Changes
  apply at once.
- Power turns the TV on when it is off, with Wake-on-LAN. The status chip says when the TV is off
  or in standby, and a one-time tip explains the TV setting to enable when it does not wake.
- App shortcuts: Netflix and YouTube on the main page, and an Apps page with Prime Video,
  Disney+, Apple TV, Spotify and Plex. The app opens under whichever id the TV lists, and a
  shortcut to an app the TV lacks says so.
- A Keyboard key opens the phone keyboard to type into the TV's search fields. It also opens by
  itself when the TV shows its own keyboard.
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
- A simulated TV in debug builds, reachable by IP at `192.0.2.1`, for trying the app without a TV, and a
  second one at `192.0.2.2` that pairs with the code `1234`.
- Project scaffold: Kotlin and Jetpack Compose app, brand adapter contract, and a first Samsung
  Tizen adapter that pairs and sends keys.

### Changed

- The phone volume keys switch moved from the TV sheet to Settings > Controls; the sheet now
  opens Settings.
- Volume and channel keys placed on their own repeat while held, like the rockers.
