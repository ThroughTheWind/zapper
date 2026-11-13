# Zapper Web

The design system for Zapper's landing page on GitHub Pages: one page that shows the app, says
honestly where the project is, invites contributors to add their TV, and sends people to the
download on GitHub Releases. It extends the [app design system](../README.md): same colour roles,
same font, and the app's own remote components rendered in a phone mockup.

The page itself is [`site/`](../../../site/README.md), published to
<https://throughthewind.github.io/zapper/>.

## Principles

1. **Show the remote, don't describe it.** The hero and the "Try it" section use the app's remote
   components laid out like the app's real default remote, and the demo drives a mock TV.
2. **Only what ships is shown as working.** The phone mockup, the demo and the Features section
   show what the current release does. Anything planned lives in the Roadmap section, labelled with
   its phase. A slot the app keeps empty (app shortcuts until Phase 2) is drawn as a dashed
   placeholder that says when it fills, never as a working key.
3. **One action per section.** Download is the filled button in the nav, hero and download band.
   Everything else is outlined, tonal or a text link.
4. **Honest and plain.** Say what works today (Samsung, tested on a 2019 RU7020) and what doesn't
   yet. No hype words, no fake numbers, no testimonials.
5. **Contributors are a second audience.** The Supported TVs and Contribute sections speak to
   developers directly, with a code sample that matches the real `TvAdapter` and three steps to a
   pull request.
6. **Static and private.** Plain HTML, CSS and a little JS. Fonts and icons are bundled; the only
   outside request is the GitHub API call for the release label. No trackers, no cookies.

## Page structure

Nav → Hero → Try it → Features → Roadmap → Supported TVs → Contribute → Download → Footer.

The order follows the visitor's questions: what is it, does it work, what can it do today, what is
coming, will it work with my TV, can I help, where do I get it.

### Keeping it current

The page states the project's phase in three places: the Roadmap section, the Supported TVs cards
and the download band. When a phase or a brand ships, update all three in the same change, and
move features from the Roadmap into Features only once they are in a release.

| Status chip | Token | Means |
| --- | --- | --- |
| Done / Supported | `status--supported` (`primary`) | In the current release |
| Next | `status--progress` (`tertiary-container`) | The phase being built now |
| Planned | `status--planned` (`secondary-container`) | On the roadmap |
| Help wanted | `status--wanted` (outline) | Waiting for a contributor |

## Colour

All colour tokens come from the app system (indigo preset) with the same names, so app components
drop in unchanged. Two additions: `tv-screen` (the mock TV panel, near-black in both themes) and
`code-bg` (alias of `surface-container-highest`). The site follows `prefers-color-scheme`; dark was
designed first, light is fully supported. Alternate sections use `surface-container-low` to
separate bands without borders. The download band is the only large `primary` surface.

## Typography

Roboto Flex for everything, Roboto Mono for code, both self-hosted as latin woff2 in `site/fonts/`.

- `web-hero`: clamp(44px, 6vw, 72px), weight 750, -2px tracking, line-height 1.05. The second half
  of the headline is `primary`.
- `web-h2`: clamp(32px, 4vw, 44px), 700, at most 20 characters wide (`max-width: 20ch`).
- `web-h3` 24/32 600 for cards and steps (20/28 inside roadmap phases). `web-lead` 20/30,
  `web-body` 17/28, `web-small` 14/22.
- `web-eyebrow`: 13px uppercase, `primary`, above every section title.

## Layout

- Container 1200px max with 24px side padding (16px on phones). Prose capped at 680px.
- Breakpoints: `bp-md` 600 (two columns), `bp-lg` 905 (side-by-side hero, full nav), `bp-xl` 1240
  (three-column features, four-column roadmap).
- Section padding 112px desktop, 64px mobile.

## Shape, depth and icons

Pill buttons and chips (`radius-full`), `radius-xl` (28) feature cards, roadmap phases and download
band, `radius-lg` (16) code and TV cards. Depth is tonal; real shadows only on the phone and TV
mockups (`shadow-phone`). The finished roadmap phase gets a 2px `primary` ring.

Icons are Material Symbols Rounded, filled, weight 500, served as an SVG sprite (`site/icons.svg`)
instead of the icon font, which would be several megabytes. `site/tools/icons.mjs` builds the sprite
from the icons `index.html` names.

## Voice

English. Second person, short sentences, benefits before mechanics: "Finds your TV", not "SSDP
discovery". Name TV brands only as plain text to say what works with what; the mock TV uses generic
tiles (Movies, Music, HDMI 1), not streaming services. Contributor copy can be technical but stays
friendly: "If you can talk to your TV from code, you can add it to Zapper."

## Wordmark and brands

No logo yet: the wordmark is the `settings_remote` symbol on a `primary` rounded square plus
"Zapper" in Roboto Flex 750; `site/favicon.svg` is the same mark. No third-party logos anywhere,
GitHub's included (use the `code` symbol and the word "GitHub"). The footer carries the
non-affiliation notice.

## GitHub Pages

- Project site: `https://throughthewind.github.io/zapper/`, so every asset path is relative.
- Downloads link to `https://github.com/ThroughTheWind/zapper/releases/latest`. The version label is
  read from the GitHub releases API at load time, with the static value as fallback.
- Accessibility: 48px minimum targets, 4.5:1 text contrast in both themes, visible focus rings,
  keyboard-operable demo (focus the TV, then arrows, Enter, Escape, + and −). The demo listens to
  the keyboard only while it has focus, so the arrow keys still scroll the page.
- Respect `prefers-reduced-motion`.

## Files

- [`tokens.json`](tokens.json): these tokens. `site/tokens.css` is generated from it by
  `node site/tools/tokens.mjs`; the `site-tokens` gate refuses a stale or hand-edited copy.
- `site/styles.css`: section 1 is the app's remote components, section 2 the site, section 3 the
  additions for the current phase (placeholder slot, roadmap, mobile menu).
