# SettingsControls

The M3 controls used on settings screens: list items, switches, segmented buttons, slider and colour swatches.

- **List item:** 56–72 tall, 24px leading icon in a 40px slot, `body-large` title, `body-medium` supporting text, trailing switch / value / chevron. Section headers in `title-small` weight, `primary`.
- **Switch:** M3 switch, `primary` track when on.
- **Segmented button:** for 2–4 exclusive choices (Theme, Haptics). Selected segment `secondary-container` with a check.
- **Slider:** repeat speed, 5 steps (slow … fast) mapping to repeat interval 150 → 45ms.
- **Colour swatches:** 48px discs; Dynamic (a quadrant of the current wallpaper roles, only on Android 12+) then the six preset seeds (`seed-indigo` … `seed-graphite`). Selected = 3px `primary` ring 5px out. Choosing applies instantly (spec AC-17).
