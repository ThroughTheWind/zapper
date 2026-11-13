# Touchpad

A swipe surface that turns gestures into D-pad steps (or pointer moves on TVs that support a cursor).

- **Surface:** `surface-container-low`, `radius-lg`, a dot grid in `outline-variant` at 24px pitch so it reads as a touch area, not empty space.
- **Gestures:** swipe = one D-pad step per 24px (`DpadSwipeStep` haptic each); fling continues stepping with decay; tap = OK; long-press = configurable (default Back); two-finger vertical drag = scroll / channel on TVs that support it.
- **Feedback:** a `primary` dot follows the finger and leaves a short fading trail; nothing persists after release.
- **Hint** in `body-small`, `on-surface-variant`, shown until the user's third successful gesture, then hidden.
- Spans: 4x3 (default), 4x4, 3x3. A layout normally uses either the DPad or the Touchpad; the default layout offers a toggle between them.
