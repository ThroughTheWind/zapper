# Zapper

The design system for Zapper, a free, open-source Android remote for smart TVs (Samsung first, any brand through adapters). Material 3 underneath, Jetpack Compose first, with a remote-specific layer on top: big forgiving keys, a grid users rearrange themselves, and haptics that let you keep your eyes on the TV.

> Package `io.github.throughthewind.zapper`. English only. Product scope: the [MVP spec](../SPEC.md). The Compose implementation lives in `core/designsystem/` (package `io.github.throughthewind.zapper.core.designsystem`); the `compose/…` paths below refer to it.

## Principles

1. **Eyes on the TV.** Every key must be findable by thumb and confirmed by touch. Hit areas are at least `touch-min` (56), shapes differ by function (circle D-pad, pill rockers, rounded keys), and every press has a haptic.
2. **Dark first.** People use a remote in a dim room. `dark` is the primary theme; `oled` gives true black for OLED phones; `light` is fully supported.
3. **Yours to arrange.** The remote is a 4-column grid of widgets. Users move, resize, recolour and add widgets, across several pages (Main, Apps, Numbers), with layouts stored per TV. The design system defines the grid, the widgets and the edit mode; it never assumes a fixed layout.
4. **One accent per cluster.** `primary` marks the single most important key in a group (OK, play/pause). Power has its own fixed red family. Everything else is calm `surface-container-high`.
5. **Brand-agnostic.** Widgets send logical actions (`volume_up`, `launch_app:netflix`); a TV adapter maps them. Nothing in the UI names a TV brand except the top bar's model line.
6. **Open and native.** Material Symbols Rounded, Roboto Flex, Material 3 colour roles and dynamic colour. No proprietary assets; app logos come from the TV at runtime, never bundled.

## Colour

Material 3 roles generated with material-color-utilities from the seed `seed-indigo` (#5B6CFF) using the *Vibrant* scheme. Six presets ship (`seed-indigo`, `seed-teal`, `seed-amber`, `seed-rose`, `seed-green`, `seed-graphite`); full light/dark/OLED schemes for each are in `compose/Color.kt`. On Android 12+ the default is **dynamic colour** from the wallpaper; presets override it.

How roles map to the remote:

| Surface | Token |
|---|---|
| Canvas | `surface` |
| Standard key | `surface-container-high` with `on-surface` icon |
| Rocker body | `surface-container-highest` |
| D-pad body | `surface-container-high`, OK = `primary` |
| Touchpad | `surface-container-low` |
| Home / Back | `secondary-container` |
| App shortcuts | `tertiary-container` (user can pick primary/secondary) |
| Power | `power-container` / `on-power-container` (fixed red, not seed-derived) |
| Focus ring | `secondary`, 3px |
| Offline / errors | `error`, `error-container` |
| Captions, hints | `on-surface-variant` |

All text and icon pairs above pass 4.5:1 in dark, light and OLED. State layers use the `opacity` tokens (`state-pressed` 10%, `disabled-content` 38%, `disabled-container` 12%).

## Typography

Roboto Flex (open source, Google Fonts; downloadable font on Android, falls back to the system sans). The M3 type scale plus three remote styles:

- `key-numeral`: 28/32, 500, tabular figures, for the number pad.
- `key-label`: 13/16, 700, uppercase, +0.8 tracking, for text keys (HOME, MENU). Six characters at most, otherwise use an icon.
- `key-label-small`: 11/14, 700, uppercase, for rocker captions (VOL, CH).

The top bar uses `title-large` for the TV's name. Body copy lives only in settings and onboarding.

## Layout: the remote grid

- 4 columns on phones, 6 at 600dp and wider (foldables, tablets).
- `grid-margin` 16, `grid-gutter` 12. Cell size = (width − 2×margin − (cols−1)×gutter) / cols, clamped between `cell-min` (64) and `cell-max` (96). On a 360dp phone that's 72dp cells.
- Every widget occupies whole cells: key 1x1/2x1, rocker 1x2/1x3, D-pad 3x3/4x4, touchpad 4x3, number pad 3x4, media bar 4x1, app shortcut 1x1/2x1/2x2.
- A layout is a set of pages swiped horizontally (default: Main, Apps, Numbers). A page scrolls vertically if it is taller than the screen.

## Shape

Keys use `key-rounded` (20) by default; Settings > Appearance > Key style offers Squircle, Circle and Square. Rockers, play/pause and chips are pills (`radius-full`); the D-pad is a circle; tiles use `radius-lg`; sheets use `radius-xl`.

## Wordmark

No logo yet. Until one exists, the wordmark is "Zapper" set in Roboto Flex 700 with -2% tracking, in `on-surface`.

## Iconography

Material Symbols **Rounded**, filled, weight 500, at `icon-key` (28) in keys and `icon-sm` (20) in chips. Use one glyph per action and keep it consistent everywhere (volume = `add`/`remove`, channel = chevrons). Icons are never the only label: every key has a content description.

## Motion and haptics

See the *Motion & haptics* section. In short: press = scale 0.92 on a bouncy spring plus a `KeyPress` haptic; hold = repeat with ticks; edit mode = gentle wiggle, lift on drag, snap ticks. Everything respects reduced-motion and the user's haptics setting (Off / Light / Strong).

## Accessibility

- 56dp minimum targets; 4.5:1 text contrast and 3:1 for icons, borders and focus rings in every theme.
- Keys are real buttons with action names ("Volume up"), grouped widgets expose their parts (the D-pad is five buttons to TalkBack).
- Every drag in edit mode has a non-drag alternative (Move / Size in the properties sheet, TalkBack custom actions).
- Hardware keyboard and switch access: visible 3px `secondary` focus ring.
- Font scaling: key labels cap at 1.3× so they fit; settings text scales freely.

## Components and screens

**Remote widgets:** RemoteKey, DPad, Rocker, NumberPad, MediaControls, Touchpad, AppShortcut, LayoutExtras (unsupported placeholder, spacer, label).
**Connection:** ConnectionStatus, DeviceCard, DiscoveryScan, PairingProgress, TvSwitcher.
**Inputs and feedback:** TextField, TextInputBar, Banner (+ snackbar), Dialog, SettingsControls.
**Customisation:** EditMode, ButtonCatalog, ButtonConfig, PageManager.
**Screens** (one card per MVP flow): OnboardingFlow, PairingFlow, RemotePages, CustomiseFlow, SettingsScreens, ErrorsAndHelp, plus RemoteScreen (the default Main page at full size).

## Voice

English only. Short, plain sentences in second person ("Look at your TV"). Name the TV the way the user named it ("Living room"), never by protocol or IP unless they're adding one. Errors say what happened and what to do next; destructive buttons use the verb ("Reset", "Forget"), never "OK".

## Files

- `tokens.json`: these tokens (this page).
- Jetpack Compose theme (in the project files under `design-system/compose/`, package `io.github.throughthewind.zapper.designsystem`): `Color.kt` (all presets, generated), `Theme.kt` (`ZapperTheme`, dynamic colour, OLED black), `Type.kt`, `Shape.kt`, `Dimens.kt` (spacing, grid), `Motion.kt`, `Haptics.kt`, `components/RemoteKey.kt`, `components/RemoteLayout.kt`.
- `tokens.dtcg.json`: the same tokens in W3C DTCG format with every preset scheme, for code generation.
