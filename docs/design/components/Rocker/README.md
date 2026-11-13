# Rocker

A vertical pill holding an up/down pair (volume or channel) with a caption between them.

- **Body:** `radius-full` pill, `surface-container-highest`, 4px inner padding. Two pill-shaped hit areas fill the ends; the caption (`key-label-small`, `on-surface-variant`) sits between.
- **Icons:** volume uses `add` / `remove`; channel uses `keyboard_arrow_up` / `keyboard_arrow_down`, so the two rockers read differently at a glance.
- **Hold to repeat** always on: 400ms, then 90ms, then 45ms after 1.5s. `RepeatTick` haptic per send.
- **Feedback:** if the TV adapter reports a level, show an `inverse-surface` snackbar "Volume 24" for 1.5s; otherwise nothing.
- **Spans:** 1x3 (default) or 1x2.
- Mute is a separate RemoteKey, not part of the rocker.
