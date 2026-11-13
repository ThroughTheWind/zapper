# PairingProgress

Shared pairing UI that every TV adapter drives through standard states: connecting, awaiting TV approval, PIN (optional), paired, denied, timed out.

- **Hero:** a simple TV silhouette (`surface-container-highest` with a `surface-container-high` bezel) showing what the user should look for on the TV: an Allow/Deny prompt with Allow highlighted in `primary`. Adapters supply the prompt wording; the drawing is generic, not a copy of any brand's UI.
- **Steps:** three rows: done (`primary` disc + check), current (`primary-container` + spinner, 600 weight), upcoming (`surface-container-highest` + number).
- **PIN step** (Android TV, Philips, Hisense adapters): 4–6 boxes, `key-numeral` style, active box 2px `primary`; auto-advances and submits on the last digit; countdown in supporting text.
- **Outcomes:** see the Pairing flow screens: success (`primary-container` check, `Success` haptic, auto-continue after 1s), denied and timed out (`error-container` icon, explanation, "Try again").
- Token rejected later: the same flow reopens with a one-line reason at the top ("Your TV asked to approve Zapper again").
