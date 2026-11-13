# LayoutExtras

Small layout widgets: the placeholder for buttons the current TV can't run, and the Spacer and Label widgets from the catalog's Spacer/Label group.

- **Unsupported placeholder:** keeps the button's icon on a hatched, outlined key with a small `help` badge. Tapping explains "Not supported on this TV" (snackbar). Used for imported layouts and when switching TVs, so nothing is silently dropped (FR-4.5, FR-12.2).
- **Spacer:** invisible on the remote, dashed outline in edit mode. Any span up to 4x1.
- **Label:** a section caption (`key-label-small`, uppercase, `on-surface-variant`) with a hairline under it, to group keys ("Streaming", "Sound").
