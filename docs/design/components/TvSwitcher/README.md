# TvSwitcher

The bottom sheet opened by tapping the TV name in the top bar, listing saved TVs for one-tap switching.

- `surface-container-low` modal sheet, `radius-xl` top corners, drag handle.
- One list item per saved TV with live status ("Connected", "Standby", "Not found"); the active TV uses the selected style (`secondary-container`, check). Tapping another TV disconnects, connects to it and loads its layout.
- Last row: "Add a TV" in `primary` → discovery.
