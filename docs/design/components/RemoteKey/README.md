# RemoteKey

The base 1x1 (or 2x1) remote button that every other key is built from; it sends one TV action on tap, or repeats while held.

**Compose:** `RemoteKey(label, onPress, emphasis, shape = keyShape, enabled, repeatOnHold, onLongPress) { Icon(...) }` in `compose/components/RemoteKey.kt`.

## Emphasis

| Emphasis | Container / content | Use for |
|---|---|---|
| Standard | `surface-container-high` / `on-surface` | Most keys: mute, guide, info, settings |
| Primary | `primary` / `on-primary` | One per cluster at most: OK, play/pause |
| Secondary | `secondary-container` / `on-secondary-container` | Navigation: Home, Back |
| Tertiary | `tertiary-container` / `on-tertiary-container` | Source, input, user highlights |
| Power | `power-container` / `on-power-container` | Power only. Fixed red family, not seed-derived |
| Ghost | transparent, 1px `outline` (3:1 boundary) | Low-value keys, spacer-like extras |

Users can override emphasis per key in edit mode (except Power, which may only be Power or Standard).

## States

- **Pressed:** scale to 0.92 with the `fastSpatial` spring and a `state-pressed` (10%) layer of the content colour. Haptic `KeyPress` on down, `KeyRelease` on up.
- **Focused** (hardware keyboard / switch access): 3px `secondary` outline, 2px offset.
- **Repeating** (hold, `repeatOnHold = true`): sends once, waits 400ms, then every 90ms, every 45ms after 1.5s. A `primary` ring shows while repeating; each send ticks `RepeatTick` haptics.
- **Disabled:** when no TV is connected every key goes to 38% content / 12% container. Keys stay tappable for a "Not connected" snackbar with a Reconnect action, so the user learns why nothing happens.

## Key style

Settings > Appearance > Key style: **Rounded** (`key-rounded`, 20) default, **Squircle** (30% continuous corners), **Circle** (`radius-full`), **Square** (`key-square`, 8). Multi-cell widgets keep their own shapes.

## Rules

- Hit area never below `touch-min` (56). Icons at `icon-key` (28), Material Symbols Rounded, filled.
- Text keys use `key-label` (13/16, 700, uppercase, +0.8 tracking). Max 6 characters; otherwise use an icon.
- Every key has a content description naming the action ("Volume up"), never the icon.
