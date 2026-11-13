# TextInputBar

A bar that sits above the phone keyboard and sends typed text to the TV's text field.

- Opened by a Keyboard key, or automatically when the TV reports its on-screen keyboard opened (Samsung `imeStart`); closes on `imeEnd` or Back.
- `surface-container-high` sheet top with `radius-xl` corners; pill input (`surface-container-highest`), round `primary` send button.
- Status line: "TV keyboard open · typing live" when the adapter sends live, "Sends when you tap Send" otherwise, "Open a search field on your TV first" when the TV isn't ready.
- Hidden entirely for adapters without text input (spec FR-5.2).
