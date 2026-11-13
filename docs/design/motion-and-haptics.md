# Motion & haptics

A remote is used by feel. Motion confirms a press for the eye in the corner of your vision; haptics confirm it for the thumb.

## Springs (Compose `spring(dampingRatio, stiffness)`)

| Token | Damping | Stiffness | Use |
|---|---|---|---|
| fastSpatial | 0.6 | 800 | Key press scale, OK press |
| defaultSpatial | 0.8 | 380 | Widget lift and snap in edit mode, sheet open |
| slowSpatial | 0.8 | 200 | Layout page change |
| fastEffects | 1.0 | 3800 | State-layer fades |
| defaultEffects | 1.0 | 1600 | Colour changes (disabled ↔ enabled) |
| slowEffects | 1.0 | 800 | Theme switch cross-fade |

Spatial springs may overshoot; effects springs never do. Fallback durations for non-spring animation: short 100ms, medium 250ms, long 400ms.

## Key behaviour

- **Press:** scale 0.92 (`pressScale`), large tiles 0.94.
- **Hold to repeat:** first send immediately, then after 400ms every 90ms, accelerating to every 45ms after 1.5s. Used by rockers, D-pad, rewind and fast-forward.
- **Edit wiggle:** ±1.5°, 260ms alternate, phase-shifted between neighbours. Disabled under reduced motion.

## Haptics

Semantic events, mapped to `HapticFeedbackConstants` in `compose/Haptics.kt` with fallbacks for older Android versions:

| Event | Constant | When |
|---|---|---|
| KeyPress | VIRTUAL_KEY | Finger down on any key |
| KeyRelease | VIRTUAL_KEY_RELEASE (27+) | Finger up |
| RepeatTick | CLOCK_TICK | Each repeated send while held |
| DpadSwipeStep | SEGMENT_FREQUENT_TICK (34+) | Each step of a swipe on D-pad or touchpad |
| LongPress | LONG_PRESS | Optional long-press actions on keys (never enters edit mode) |
| DragStart / DragSnap / DragDrop | GESTURE_START / SEGMENT_TICK / GESTURE_END | Edit mode drag |
| Success | CONFIRM (30+) | Paired, app launched |
| Failure | REJECT (30+) | Connection lost, command failed |

Settings > Feedback > Haptics (default Light): **Off**, **Light** (system feedback, follows the phone's touch-feedback setting), **Strong** (adds crisp `VibrationEffect.Composition` primitives on Android 12+ where the device supports them).
