# Zapper — MVP Product Spec

| | |
|---|---|
| **Status** | v1.0 — all open questions decided |
| **App name** | Zapper |
| **Package id** | `io.github.throughthewind.zapper` |
| **Date** | 2026-11-13 |
| **Platform** | Android only (phones) |
| **License** | GPL-3.0 (free, open source) |
| **Repository** | https://github.com/ThroughTheWind/zapper |
| **First TV target** | Samsung Smart TV (Tizen) |
| **Reference test TV** | Samsung UE75RU7020KXXU (2019 RU7020, Tizen, EU), software T-MSLDEUC-1510.0 |
| **Repo tooling** | `rungs` (Antoine's AI agent workflow CLI, npm) |

Protocol details: see [research/tv-remote-protocols.md](research/tv-remote-protocols.md). Visual design: see the [design system](design/README.md). Delivery order: see the [roadmap](ROADMAP.md).

Related work running in parallel (this spec stays at product level and links to them for the details):
- **Design system** — visual language, components, theming.
- **TV protocol research** — Samsung first, then other brands.
- **Stack study and repository setup** — language, UI toolkit, module layout, CI, `rungs` integration.

Statements that depend on those threads are tagged **[DEP: design]**, **[DEP: protocol]** or **[DEP: stack]**.

---

## 1. Vision

A free, open-source, ad-free Android remote that controls any smart TV on the local network, starting with Samsung. It should feel faster and nicer than the vendor apps (Samsung SmartThings, etc.), and it lets users arrange the buttons and shortcuts they actually use.

### Goals for the MVP
1. Control Antoine's Samsung TV from an Android phone for everyday use, replacing the physical remote for common actions.
2. Prove the **TV adapter architecture**: the Samsung implementation is one plug-in behind a brand-agnostic interface, so a second brand can be added without touching the UI or core.
3. Ship a **customizable layout**: users can add, remove, reorder and resize buttons and shortcuts.
4. Set a modern, consistent visual baseline from the design system.

### Non-goals for the MVP
- iOS, tablets-optimized layouts, Wear OS, Android TV/Google TV as the *controlling* device.
- Cloud accounts, sign-in, sync between phones.
- Monetization of any kind (no ads, no IAP, no tracking).
- Screen mirroring, media casting, file sharing.

---

## 2. Users and personas

| Persona | Description | Primary need |
|---|---|---|
| **Couch user** (primary) | Owns a Samsung smart TV, uses phone as remote when the physical one is lost or for typing. | Instant, reliable basic control. |
| **Tinkerer** | Wants their own layout: big volume buttons, one-tap Netflix, no clutter. | Customization. |
| **Contributor** | Developer who wants to add support for their TV brand. | Clear adapter contract, docs, test harness. |

---

## 3. Scope

### 3.1 In scope (MVP)

| # | Area | Summary |
|---|---|---|
| F1 | Discovery | Auto-find compatible TVs on the same Wi-Fi; manual add by IP as fallback. |
| F2 | Pairing | Guided first-time pairing (accept prompt on TV), credentials stored on device. |
| F3 | Connection management | Auto-reconnect to last TV, clear connection status, multiple saved TVs. |
| F4 | Core remote | D-pad/OK, back, home, volume, mute, channel, power, source, menu, number pad, media keys. |
| F5 | Text input | Send text to TV input fields via phone keyboard. **[DEP: protocol]** |
| F6 | App shortcuts | Launch installed TV apps (Netflix, YouTube, …) from shortcut buttons. **[DEP: protocol]** |
| F7 | Power on | Turn the TV on from standby (Wake-on-LAN or brand mechanism). **[DEP: protocol]** |
| F8 | Layout customization | Edit mode: add/remove/move/resize buttons, choose from a button catalog, multiple layouts/pages. |
| F9 | Theming | Light/dark/system theme, Material You dynamic color option. **[DEP: design]** |
| F10 | Haptics | Configurable haptic feedback on button press. |
| F11 | Adapter architecture | Brand-agnostic core with a `TvAdapter` contract; Samsung adapter is the only one shipped. |
| F12 | Layout import/export | Export/import a layout as a JSON file (share via Android share sheet). |

### 3.2 Out of scope (MVP), candidates for later

| Item | Target |
|---|---|
| Other brands: LG webOS, Sony/Philips/TCL Android TV & Google TV, Roku, Fire TV, Vizio, Hisense VIDAA | v1.x, one adapter at a time |
| Touchpad / swipe gesture mode | v1.1 |
| Home screen widget, Quick Settings tile, lock-screen/notification remote | v1.1 |
| Macros (sequence of keys, e.g. "Input HDMI2 + volume 20") | v1.2 |
| Voice control / voice search | Later |
| IR blaster support (phones with IR) | Later, as an adapter |
| HDMI-CEC device control (soundbar, console) | Later |
| Wear OS companion | Later |
| Cloud sync of layouts | Not planned (export/import covers it) |
| Localization beyond English | Later (community) |

---

## 4. User stories

Format: *As a [persona], I want [action], so that [outcome].* IDs map to features.

### Discovery and pairing
- **US-1 (F1)** As a couch user, I want the app to find my TV automatically on first launch, so that I don't need to know its IP address.
- **US-2 (F1)** As a couch user, I want to add a TV by IP address when it isn't found, so that I'm not blocked by network quirks.
- **US-3 (F2)** As a couch user, I want clear on-screen guidance ("Accept the request on your TV"), so that pairing succeeds the first time.
- **US-4 (F2/F3)** As a couch user, I want the app to remember my TV and pairing, so that I never have to pair again.
- **US-5 (F3)** As a couch user with two TVs, I want to switch between saved TVs in one tap, so that one app controls both.

### Remote control
- **US-6 (F4)** As a couch user, I want to navigate menus with a D-pad and OK, so that I can use the TV UI.
- **US-7 (F4)** As a couch user, I want volume buttons that repeat while held, so that I can change volume quickly.
- **US-8 (F4)** As a couch user, I want the phone's hardware volume keys to control the TV volume while the app is open (toggle in settings), so that I don't need to look at the screen.
- **US-9 (F5)** As a couch user, I want to type with my phone keyboard into the TV's search field, so that searching is not painful.
- **US-10 (F6)** As a couch user, I want one-tap buttons for my streaming apps, so that I can jump straight to them.
- **US-11 (F7)** As a couch user, I want to turn the TV on from the app, so that I don't need the physical remote at all.

### Customization
- **US-12 (F8)** As a tinkerer, I want to enter an edit mode where I drag buttons to new positions, so that the layout matches how I hold the phone.
- **US-13 (F8)** As a tinkerer, I want to remove buttons I never use and add ones I do from a catalog, so that the remote is uncluttered.
- **US-14 (F8)** As a tinkerer, I want to resize buttons (e.g. a large volume rocker), so that frequent actions are easy to hit.
- **US-15 (F8)** As a tinkerer, I want multiple pages/layouts (e.g. "Main", "Apps", "Numbers"), so that I can swipe between them.
- **US-16 (F8)** As a tinkerer, I want to reset a layout to the default, so that I can recover from a bad edit.
- **US-17 (F12)** As a tinkerer, I want to export my layout and import it on another phone, so that I can reuse or share it.
- **US-18 (F9/F10)** As a tinkerer, I want to pick theme and haptic strength, so that the app feels like mine.

### Contributor
- **US-19 (F11)** As a contributor, I want a documented adapter interface and a fake/mock TV for tests, so that I can add a new brand without a full understanding of the app.
- **US-20 (F11)** As a contributor, I want the UI to hide or disable buttons my adapter doesn't support, so that I only implement what my TV can do.

---

## 5. Functional requirements

### F1 — Discovery
- FR-1.1 On launch with no saved TV, scan the local network for supported TVs and list them with name, model (when available) and brand icon.
- FR-1.2 Discovery is delegated to each adapter (each brand advertises differently), aggregated by core. **[DEP: protocol]** Samsung: SSDP `M-SEARCH` for `urn:samsung.com:device:RemoteControlReceiver:1`, then confirm with `GET http://IP:8001/api/v2/` (no pairing needed; gives name, model, `wifiMac`, `PowerState`). Deduplicate devices by MAC/UUID.
- FR-1.3 Scan times out after ~10 s with a "Not found? Add by IP" path and troubleshooting tips (same Wi-Fi, TV on, no guest network / AP isolation, app not denied in the TV's *Device Connection Manager*).
- FR-1.4 Manual add: user enters IP; app probes it with every adapter and picks the one that answers.

### F2 — Pairing
- FR-2.1 Pairing flow is adapter-defined but rendered by a shared UI with standard states: *connecting → awaiting TV approval → paired / denied / timed out*.
- FR-2.2 Adapters may require a PIN entry step (Android TV, Philips, Hisense); the shared UI supports it. Samsung uses an Allow/Deny prompt on the TV over `wss://IP:8002` and returns a token; no PIN. **[DEP: protocol]**
- FR-2.3 Credentials/tokens are stored locally, encrypted at rest (Android Keystore-backed). **[DEP: stack]**
- FR-2.4 If a stored token is rejected, the app re-runs pairing automatically with an explanation.

### F3 — Connection management
- FR-3.1 On app open, reconnect to the last-used TV automatically.
- FR-3.2 Persistent, unobtrusive status indicator: connected / connecting / offline / TV off.
- FR-3.3 Saved TV list: rename, forget, set default, view info (IP, MAC, model, adapter).
- FR-3.4 Keep the connection alive while the app is in foreground; drop it in background (no background service in MVP).
- FR-3.5 Pressing a button while disconnected triggers reconnect and queues nothing (commands are not replayed later, to avoid surprises).

### F4 — Core remote
- FR-4.1 Default layout includes: Power, Source/Input, Home, Back, Menu/Settings, D-pad + OK, Vol +/−, Mute, Ch +/−, Play/Pause, Rewind, Fast-forward, Number pad (on its own page), Info/Guide.
- FR-4.2 Buttons are mapped to **abstract commands** (`VOLUME_UP`, `NAV_LEFT`, `LAUNCH_APP(id)`…); the adapter maps them to brand-specific codes.
- FR-4.3 Repeatable buttons (volume, channel, D-pad) auto-repeat on long press with configurable rate.
- FR-4.4 Optional hardware volume key capture while app is foreground (default on).
- FR-4.5 Buttons the active adapter does not support are hidden in the catalog and shown as disabled if already placed.

### F5 — Text input
- FR-5.1 A keyboard button opens the phone IME; text is sent to the TV (live or on submit, depending on adapter capability). Samsung: `SendInputString` works only while a TV keyboard is on screen; the app listens for the TV's `imeStart`/`imeEnd` events and can open the phone keyboard automatically.
- FR-5.2 Fallback when the TV does not expose text input: hide the feature for that adapter.

### F6 — App shortcuts
- FR-6.1 Fetch the list of installed apps from the TV when the adapter supports it (Samsung: `ed.installedApp.get`, may return empty on some firmware); otherwise, or in addition, offer a data-driven curated list of known app IDs per brand/region.
- FR-6.2 App shortcut buttons display the app's icon (from TV if available, otherwise a bundled icon or initials).
- FR-6.3 Users can place app shortcuts anywhere in the layout like any other button.

### F7 — Power on
- FR-7.1 Power button turns TV off when connected and attempts power-on (e.g. Wake-on-LAN to stored MAC) when the TV is unreachable. **[DEP: protocol]**
- FR-7.2 If power-on is not possible, show a one-time explanation of how to enable it on the TV. Samsung: *Settings → General → Network → Expert Settings → Power On with Mobile*. Wi-Fi WoL is unreliable on some models; Ethernet is reliable.
- FR-7.3 Show TV power state (on / standby) using Samsung `PowerState` (available on 2019+, so on the reference TV).

### F8 — Layout customization
- FR-8.1 The remote is a grid-based canvas. Each item has position, size (in grid cells) and an action. **[DEP: design]** for grid dimensions and density.
- FR-8.2 Edit mode (entered via an explicit "Edit" control, not long-press, to avoid accidental edits): drag to move, handles to resize, tap to configure, trash zone to remove.
- FR-8.3 Button catalog groups: Navigation, Volume & Channel, Media, Numbers, Apps, System, Spacer/Label.
- FR-8.4 Per-button options: label, icon, optional color accent, repeat on hold (where applicable).
- FR-8.5 Multiple pages per layout, swipe horizontally between them; add/rename/reorder/delete pages.
- FR-8.6 Undo of the last edit, and "Reset to default" per layout.
- FR-8.7 Layouts are stored per TV, with an option to copy a layout from another TV.
- FR-8.8 Composite controls (D-pad, volume rocker, number pad) are single catalog items so they can be placed as one block.

### F9 — Theming
- FR-9.1 Light, dark, follow system. Material You dynamic color as an option. **[DEP: design]**
- FR-9.2 Optional "OLED black" variant (useful in a dark room).

### F10 — Haptics and feedback
- FR-10.1 Haptic on press: off / light / strong. Default light.
- FR-10.2 Visual press feedback always on; optional click sound off by default.

### F11 — Adapter architecture (product-level contract)
- FR-11.1 Core and UI depend only on a brand-agnostic adapter contract. Indicative shape (final form owned by **[DEP: stack]**):
  - `discover()` → stream of found devices
  - `probe(ip)` → device info or not-supported
  - `pair(device)` → pairing state stream (incl. optional PIN step)
  - `connect(device, credentials)` / `disconnect()`
  - `capabilities()` → set of supported abstract commands and features (text input, app list, power-on, …)
  - `send(command)`
  - `listApps()`, `launchApp(id)`, `sendText(text)` (optional, gated by capabilities)
  - `connectionState` → observable
- FR-11.2 Each adapter lives in its own module/package and registers itself with a registry; adding a brand requires no UI change.
- FR-11.3 A **fake adapter** ships in debug builds for UI development and automated tests without a TV.
- FR-11.4 `docs/adapters.md` explains how to add a brand, with the Samsung adapter as reference.

### F12 — Layout import/export
- FR-12.1 Export a layout as a versioned JSON file through the Android share sheet.
- FR-12.2 Import validates schema version; unknown or unsupported buttons are kept as disabled placeholders, not dropped.

### Settings screen (summary)
TVs (manage saved TVs), Layout (edit, reset, import/export), Appearance (theme, dynamic color, OLED black), Feedback (haptics, sound), Controls (hardware volume keys, repeat rate), About (version, license, source link, open source licenses).

---

## 6. Non-functional requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-1 | Performance | Button press to command sent on the wire: < 50 ms (p95) on a mid-range phone. Perceived press-to-TV-reaction depends on TV, target < 200 ms on Samsung. |
| NFR-2 | Startup | Cold start to usable remote < 1.5 s; reconnect to known TV < 2 s when TV is on. |
| NFR-3 | Reliability | Auto-reconnect after Wi-Fi blips or TV sleep without user action; no crash in a 30-min continuous use session. |
| NFR-4 | Privacy | No analytics, no tracking, no ads, no account. No crash reporting in the MVP. No network traffic outside the LAN. |
| NFR-5 | Permissions | Minimum: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE` (for discovery), `VIBRATE`. No location permission. Plan for Android 16+ local network protection (runtime permission, enforced for a future target SDK) with a clear onboarding screen; check the exact permission name at implementation. Cleartext HTTP is allowed in `network_security_config.xml` but used only toward LAN addresses (Samsung `:8001` info endpoint). **[DEP: stack]** |
| NFR-6 | Security | Pairing tokens encrypted at rest; TLS used whenever the TV supports it (Samsung `wss://:8002`), accepting the TV's self-signed cert only for the paired device (pinning on first pair). |
| NFR-7 | Compatibility | Android 8.0 (API 26)+ as a proposed minimum **[DEP: stack]**. Phones in portrait; landscape not required for MVP. |
| NFR-8 | Samsung coverage | Tizen-based Samsung TVs, model year 2016+ (2016–17 on `ws://:8001`, 2018+ on `wss://:8002` with token). Older (Orsay, pre-2016) out of scope. Reference test device: 2019 RU7020. |
| NFR-9 | Accessibility | All buttons have content descriptions; touch targets ≥ 48 dp; supports font scaling; WCAG AA contrast in all themes; TalkBack usable for the default layout. |
| NFR-10 | Localization | English only at launch; all strings externalized so community translations can be added later. |
| NFR-11 | App size | APK/AAB download < 15 MB. |
| NFR-12 | Battery | No background work when app is not in foreground. |
| NFR-13 | Code quality | Adapter contract and core covered by unit tests; Samsung adapter tested against a recorded/mocked protocol; UI smoke tests on the fake adapter. CI runs on every PR. **[DEP: stack]** |
| NFR-14 | Open source | Public repo (github.com/ThroughTheWind/zapper), GPL-3.0 license, CONTRIBUTING.md, adapter guide, issue templates (bug, new TV brand request). |
| NFR-15 | Distribution | GitHub Releases (signed APK) only for MVP (decided); F-Droid compatibility (no proprietary dependencies) as a design constraint; Google Play optional later. |

---

## 7. Acceptance criteria (MVP definition of done)

Tested on Antoine's Samsung TV and at least one Android phone.

**Discovery and pairing**
- [ ] AC-1 With phone and TV on the same Wi-Fi, the TV appears in the discovery list within 10 s.
- [ ] AC-2 Adding the TV by IP works when discovery is blocked.
- [ ] AC-3 First connection shows the TV approval prompt; after accepting, the app reaches "Connected" and the token is saved.
- [ ] AC-4 Killing and reopening the app reconnects without any TV prompt.
- [ ] AC-5 Denying the prompt on the TV shows a clear error with a retry action.

**Control**
- [ ] AC-6 Every button in the default layout produces the expected effect on the TV.
- [ ] AC-7 Holding Vol+ increases volume continuously until release.
- [ ] AC-8 Phone hardware volume keys change TV volume while the app is open (when enabled).
- [ ] AC-9 With a TV search field focused (e.g. YouTube), typing "stranger" on the phone keyboard enters it into that field.
- [ ] AC-10 A Netflix (or other installed app) shortcut opens that app on the TV, whether the app list comes from the TV or the bundled list.
- [ ] AC-11 With the TV in standby and *Power On with Mobile* enabled, the Power button turns it on within 10 s.
- [ ] AC-12 Turning Wi-Fi off and on on the phone results in automatic reconnection within 5 s of network return.

**Customization**
- [ ] AC-13 In edit mode, a user can move, resize, add and remove a button; changes persist after app restart.
- [ ] AC-14 A user can add a second page and swipe between pages.
- [ ] AC-15 "Reset to default" restores the factory layout.
- [ ] AC-16 Exporting a layout and importing it on a fresh install reproduces it exactly.
- [ ] AC-17 Theme switch (light/dark/system/dynamic) applies instantly without restart.

**Architecture and project**
- [ ] AC-18 The app runs fully against the fake adapter with no TV present (debug build).
- [ ] AC-19 No UI or core module references Samsung-specific types (enforced by module dependencies or a lint/architecture test).
- [ ] AC-20 `docs/adapters.md` exists and a reviewer can follow it to stub a new brand adapter.
- [ ] AC-21 CI builds, runs tests and produces a signed release APK on tag.
- [ ] AC-22 App requests no permission beyond the NFR-5 list and sends no traffic outside the LAN.

---

## 8. Proposed milestones

| Milestone | Content | Exit criteria |
|---|---|---|
| **M0 — Foundations** | Repo set up with `rungs`, CI, module skeleton, adapter contract, fake adapter, design tokens. | AC-18, AC-19, AC-21 (build part) |
| **M1 — Samsung basic remote** | Discovery, pairing, fixed default layout, core keys, reconnect. | AC-1 → AC-8, AC-12 |
| **M2 — Samsung extras** | Text input, app list and shortcuts, power-on. | AC-9 → AC-11 |
| **M3 — Customization** | Edit mode, catalog, pages, reset, import/export, theming, haptics. | AC-13 → AC-17 |
| **M4 — Release 1.0** | Polish, accessibility pass, English strings, docs, signed release. | All ACs |

---

## 9. Assumptions and dependencies on sibling threads

Status after the protocol research ([research/tv-remote-protocols.md](research/tv-remote-protocols.md)):

| # | Assumption | Owner thread | Status |
|---|---|---|---|
| A1 | Samsung Tizen TVs (2016+) expose a local WebSocket remote API with an on-TV approval prompt and a reusable token. | Protocol | ✅ Confirmed: `wss://:8002` with token on 2018+ (reference TV is 2019). |
| A2 | Samsung exposes installed apps and app launch over the local network. | Protocol | ⚠️ Partly: launch works; app list may be empty on some firmware, so a bundled app ID list is required (FR-6.1). Verify on the reference TV. |
| A3 | Text input to the TV is possible on Samsung via the local API. | Protocol | ✅ Confirmed, only while a TV keyboard is on screen (FR-5.1). |
| A4 | Power-on works via Wake-on-LAN when *Power On with Mobile* is enabled. | Protocol | ✅ Confirmed with caveats: Wi-Fi WoL unreliable on some models. Verify on the reference TV. |
| A5 | Discovery works without location permission. | Protocol + Stack | ✅ SSDP + `/api/v2/` probe, with manual IP fallback. ⚠️ Android 16+ local network permission must be planned for (NFR-5). |
| A6 | Stack is native Android (Kotlin + Jetpack Compose). | Stack | ✅ Decided: [ADR-0001](decisions/ADR-0001-kotlin-compose-multi-module.md). |
| A7 | Design system provides tokens and components for a grid-based remote canvas, edit mode, and Material You support. | Design | ✅ Delivered: [design/](design/README.md), Compose theme in `core/designsystem`. |
| A8 | `rungs` drives repo workflow; spec items map to `rungs`-trackable work units. | Stack/repo setup | ✅ Work items in [backlog/](backlog/BACKLOG.md), phases in the [roadmap](ROADMAP.md). |

## 10. Decisions log

1. ~~**License**~~ — **Decided: GPL-3.0.**
2. ~~**App name**~~ — **Decided:** Zapper, package `io.github.throughthewind.zapper`.
3. ~~**Your TV's model/year**~~ — **Answered:** UE75RU7020KXXU (2019, Tizen, EU).
4. ~~**Languages**~~ — **Decided:** English only at launch.
5. ~~**Crash reporting**~~ — **Decided:** omitted for MVP.
6. ~~**Distribution**~~ — **Decided:** GitHub Releases for MVP; F-Droid / Play Store later.

---

## 11. Glossary

- **Adapter** — brand-specific implementation of the TV contract (discovery, pairing, commands).
- **Abstract command** — brand-neutral action like `VOLUME_UP`; mapped by each adapter.
- **Capabilities** — features an adapter declares it supports; drives what the UI shows.
- **Layout** — set of pages of placed buttons, stored per TV.
- **Fake adapter** — in-app simulated TV used for development and tests.
