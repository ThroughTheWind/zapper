# Banner

An inline card that explains a state or a one-time setup step, with optional text actions; plus the snackbar for transient messages.

- **Neutral** (`surface-container-high`): tips, notices. **Info** (`secondary-container`): one-time guidance such as the power-on setup (spec FR-7.2). **Error** (`error-container`): offline, pairing failed.
- Layout: leading 24px icon, bold first line (`title-small` weight), body in `body-medium`, text actions below.
- Banners sit at the top of the remote canvas, pushing keys down rather than covering them, and dismiss once resolved.
- **Snackbar** (`inverse-surface`, one line, optional `inverse-primary` action): short-lived messages such as "Not connected · Reconnect" when a key is pressed offline, or "Volume 24".
