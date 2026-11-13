# AppShortcut

A tile that launches an app on the TV in one tap; the main way users personalise the remote.

- **2x1 (default):** 40px icon well (`radius-md`) + app name in `title-small`, `radius-lg` tile. **1x1:** icon only, name in the content description. **2x2:** large icon, name below.
- **Colour:** tertiary-container by default; user can pick Primary, Secondary or Tertiary. Tiles stay inside the theme's roles so a page of shortcuts stays calm and readable in every theme.
- **Icon:** when the TV adapter provides an app icon (Samsung exposes installed apps), show it in the well, clipped to `radius-md`. Otherwise a monogram: first letter, `on-*-container` well with the container colour as ink.
- **Press:** scale 0.94 (larger tiles scale less), `KeyPress` haptic, then a `Success` haptic when the TV confirms launch, or `Failure` + snackbar if it fails.
- Third-party app names and icons are the TV's data. The app ships no brand logos.
