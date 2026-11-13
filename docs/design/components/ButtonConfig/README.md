# ButtonConfig

The sheet that opens when a button is tapped in edit mode, holding its options (spec FR-8.4).

- Header: live preview of the key + its name and action.
- **Label:** optional caption (TextField), 6 characters max on 1x1 keys.
- **Icon:** current icon + "Change" → icon picker (Material Symbols, searchable).
- **Colour:** swatches for Standard, Primary, Secondary, Tertiary, Ghost (theme roles only, so every theme stays legible). Selected swatch has a 3px `primary` ring.
- **Repeat while held:** switch, only for repeatable actions (volume, channel, navigation, seek).
- Footer: Remove (error text button) and Done. Changes apply live; Undo in the top bar reverts.
