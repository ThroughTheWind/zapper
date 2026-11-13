# DeviceCard

A tappable card for one TV, used in discovery results, the TV switcher and Saved TVs.

- `surface-container-high`, `radius-lg`, 16 padding. Leading 48px brand tile (`secondary-container`, brand initial; Zapper ships no brand logos), title `title-medium` (600), supporting line `label`-sized `on-surface-variant` with IP and brand, or a status.
- **States:** found (chevron), connecting (2px `primary` inset outline + spinner), saved (`Default` badge on the default TV, overflow menu: Rename, Set default, Info, Forget), unreachable (60% opacity, `wifi_off`).
- Deduplicated by MAC/UUID upstream; the card never shows the same TV twice.
- Content description: "Samsung QE55Q80, 192.168.1.42, connect".
