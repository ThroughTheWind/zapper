# TextField

M3 outlined text field, used for the manual IP entry, renaming a TV or a page, and button labels.

- 56 tall, `radius-sm`, 1px `outline`; focused 2px `primary`; error 2px `error` with an `error` icon and supporting text in `error`.
- Floating label in `body-small`. Supporting text below, 16px inset.
- **IP entry:** numeric keyboard with dots, validates format on the fly, then probes on submit with a trailing spinner and "Checking …" support text. Failure message names what happened ("No supported TV answered at this address"), never an exception.
