# ConnectionStatus

The top bar showing which TV the remote controls and whether it's reachable; tapping the name opens the TV picker.

- **Top bar:** `top-bar-height` (64). Brand and model in `label-medium` `on-surface-variant`, the user's name for the TV in `title-large`. Tap → TV picker bottom sheet. Trailing: status chip, then edit-layout icon button.
- **Chip states:** Connected (`tertiary` dot, auto-hides after 3s to a dot only), Connecting (spinner, `on-surface-variant`), Offline (`error-container`, label "Offline · Retry", tap retries; `Failure` haptic once when the connection drops).
- **Pairing success** plays a `Success` haptic and briefly shows "Connected".
- When offline, all keys render Disabled (see RemoteKey).
- Wake-on-LAN: when the TV is off but known, Power stays enabled and the chip reads "TV off · Power on".
