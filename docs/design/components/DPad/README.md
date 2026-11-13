# DPad

A circular 3x3 directional pad with a central OK key, the primary navigation control on the default layout.

- **Body:** circle, `surface-container-high`, 1px inner `outline-variant` ring. Four invisible 90° wedges are the hit areas: the whole quadrant, not just the chevron, so thumbs can't miss.
- **Chevrons:** `on-surface-variant`, `icon-key` size, 18px from the edge.
- **OK:** 40% of the pad diameter, `primary` / `on-primary`, `key-label` text "OK" (or a check icon), separated from the ring by an 8px gap in the body colour.
- **Pressed wedge:** `state-pressed` layer of `on-surface` over that quadrant only; OK scales to 0.92.
- **Hold:** repeats like a RemoteKey (400ms delay, 90ms, then 45ms).
- **Swipe mode:** a drag that starts on the pad sends one step per 24px of travel along the dominant axis, `DpadSwipeStep` haptic each step. Lets users scroll long TV lists without lifting the thumb.
- **Spans:** 3x3 (default) or 4x4. Always square.
- **Accessibility:** expose five buttons (Up, Down, Left, Right, OK) to TalkBack, not one custom view.
