# Landing page

The static site published to <https://throughthewind.github.io/zapper/> by
[`.github/workflows/pages.yml`](../.github/workflows/pages.yml) on every push to `main` that touches
it. Its design system is [`docs/design/web/`](../docs/design/web/README.md).

Plain HTML, CSS and one script, no build step. To preview it, serve this folder:

```sh
python3 -m http.server --directory site 8000
```

| File | What it is |
| --- | --- |
| `index.html` | The page. Update the Roadmap, Supported TVs and download band when a phase or brand ships. |
| `styles.css` | Site styles. |
| `tokens.css` | Generated from `docs/design/web/tokens.json` by `node site/tools/tokens.mjs`. Do not edit. |
| `icons.svg` | Material Symbols sprite, built by `node site/tools/icons.mjs --from <package>`. |
| `main.js` | Mobile menu, release label, copy button and the demo. |
| `fonts/` | Roboto Flex and Roboto Mono (SIL Open Font License, texts alongside). |

To use a new icon, reference it as `icons.svg#name` in `index.html`, then rebuild the sprite from
the `@material-symbols/svg-500` npm package (`npm pack @material-symbols/svg-500`, unpack it, and
pass the `package` folder to `--from`). `node .ai/rungs.mjs check` fails while an icon is missing.

The first deployment needs one setting: **Settings → Pages → Build and deployment → Source: GitHub
Actions**.
