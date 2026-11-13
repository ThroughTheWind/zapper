# Smart TV remote control protocols: research report

*Android TV Remote project · research thread · 2026-11-13*

How an Android phone app can control smart TVs over the local network, Samsung first, then the other major platforms. For each one the report covers discovery, pairing/auth, command transport, capabilities, quirks, and reusable open-source code with licenses. It ends with a proposed Kotlin adapter interface.

> **Confidence note.** Protocol details come from widely used open-source implementations (Home Assistant, openHAB, Connect SDK and others) and vendor docs where they exist. Samsung, LG, Vizio, Philips and Hisense protocols are **unofficial and reverse-engineered**, so they can change with firmware. Items marked ⚠️ need checking on real hardware before we rely on them.

---

## 0. TL;DR and recommendations

| Platform | Transport | Pairing | Power on | Official? | Priority |
|---|---|---|---|---|---|
| **Samsung Tizen 2016+** | WebSocket JSON, `wss://:8002` (`ws://:8001` on 2016–2017) | "Allow" prompt on TV, token | Wake-on-LAN | No (reverse-engineered) | **P0 (MVP)** |
| **Android TV / Google TV** (Sony, TCL, Hisense GTV, Philips, Sharp, Xiaomi, Chromecast GTV, Shield, Google TV Streamer) | TLS + protobuf, ports 6466/6467 ("Remote v2") | 6-char hex code on TV, client cert | Network key or WoL | No (reverse-engineered from the Google TV app) | **P1**, one adapter covers many brands |
| **LG webOS** | WebSocket JSON (SSAP), `wss://:3001` | Prompt on TV, `client-key` | Wake-on-LAN | Semi (LG Connect SDK legacy) | **P1** |
| **Roku / Roku TV** | HTTP REST (ECP), `:8060` | None | `PowerOn` key / WoL | **Yes, documented** | **P1**, easiest |
| Vizio SmartCast | HTTPS REST, `:7345` (`:9000` old) | PIN on TV, auth token | Power key (network standby) | No | P2 |
| Sony Bravia (extra) | JSON-RPC REST + IRCC SOAP | PSK or PIN | `setPowerStatus` | **Yes** (pro.bravia) | P2 (Android TV adapter covers basics) |
| Philips (Android, 2016+) | HTTPS REST JointSpace v6, `:1926` | PIN + digest auth | WoL | No | P3 (Android TV adapter covers basics) |
| Hisense VIDAA | MQTT over TLS, `:36669` | 4-digit PIN | WoL | No | P3 |
| Fire TV | ADB (`:5555`) | ADB key prompt | Network key | Partly (ADB is standard) | P3 |
| Panasonic Viera | SOAP, `:55000` | PIN + AES (2019+) | WoL (some) | No | P4 |
| **IR blaster** (phone hardware) | `ConsumerIrManager` | None | Yes | Yes (Android API) | Optional "universal" fallback |

**Recommendations**

1. **MVP = Samsung Tizen WebSocket adapter + SSDP discovery + Wake-on-LAN.** It covers every Samsung TV from 2016 onwards with a single code path (2016–2017 on plain `ws://8001`, 2018+ on `wss://8002` with a token).
2. **Next, the Android TV Remote v2 adapter.** It is the highest-leverage second adapter because it covers Sony, TCL, Hisense (Google TV models), Philips, Sharp, Xiaomi and all Google TV dongles at once.
3. Then **LG webOS** and **Roku ECP**. Roku is the simplest (plain HTTP, no auth) and makes a good second-adapter test of the abstraction.
4. Design the adapter interface around **capabilities**, not a lowest-common-denominator key list. Brands differ a lot in what they can do (absolute volume, app list, text input, pointer, power state).
5. User-customizable buttons should map to a **brand-neutral `RemoteAction`** (key, launch app, text, macro). The adapter translates it; layouts stay portable across TVs.
6. Do **not** depend on cloud APIs (SmartThings, LG ThinQ) for the MVP. They need developer registration, OAuth and internet access, and the product is "free, local, open source". Keep SmartThings as an optional later adapter for power-on when WoL fails.

---

## 1. Cross-cutting Android concerns

These apply to every adapter and belong in a shared `core-network` module.

- **Discovery.**
  - *SSDP* (UDP multicast `239.255.255.250:1900`, `M-SEARCH`) finds Samsung, LG, Roku, Vizio, Sony and anything DIAL-capable. It needs a `WifiManager.MulticastLock` while scanning, plus `CHANGE_WIFI_MULTICAST_STATE`.
  - *mDNS/DNS-SD* finds Android TV (`_androidtvremote2._tcp`), Vizio (`_viziocast._tcp`), AirPlay-capable TVs (`_airplay._tcp`, useful for Samsung/LG/Sony/Vizio fingerprinting) and Google Cast (`_googlecast._tcp`). Use Android's `NsdManager` (API 34+ improved it) or jmDNS (Apache-2.0).
  - *Manual IP entry* and a *subnet probe* (try known ports such as `:8001/api/v2/`, `:8060/query/device-info`, `:3001`) are needed as fallback. SSDP is often blocked by mesh routers, guest networks or "AP isolation".
  - Deduplicate by MAC address or UUID. A single TV answers to several protocols (for example Samsung via SSDP, AirPlay and DIAL), and a Sony/Philips TV is both Android TV and its brand API.
- **Local network permission.** ⚠️ Android 16 introduced opt-in "local network protection", and Google has said it will be enforced for apps targeting a future SDK (Android 17 / API 37). Plan for a runtime permission (`NEARBY_WIFI_DEVICES` / the new local-network permission) and a clear onboarding screen. Check the exact permission name at implementation time.
- **Cleartext traffic.** Roku (`http://:8060`), Samsung 2016–17 (`ws://:8001`), Samsung REST info and DIAL are plain HTTP. Since Android 9 cleartext is blocked by default. Allow it in `network_security_config.xml`; Android has no "private IPs only" rule, so allow cleartext globally but only ever use it toward LAN addresses in code.
- **Self-signed TLS.** Samsung `:8002`, LG `:3001`, Android TV `:6466/6467`, Vizio `:7345`, Philips `:1926` and Hisense all use self-signed certs. Use a per-device **trust-on-first-use (TOFU) pinned** `X509TrustManager`: store the cert fingerprint at pairing and reject a different cert later (or prompt the user to re-pair). Never ship a blanket "trust all".
- **Wake-on-LAN.** Send a UDP magic packet (6×`0xFF` + 16×MAC) to `255.255.255.255:9` and to the subnet broadcast address. Get the MAC from the TV while it is on (Samsung `/api/v2/` gives `wifiMac`; LG, Roku and Vizio give it in device info; Android TV needs ARP / manual entry). Many TVs only wake over Ethernet or need a setting enabled. Show this in onboarding.
- **Connection lifecycle.** Use one long-lived socket per TV (OkHttp WebSocket or Ktor), with reconnect and exponential backoff. Drop it on `ON_STOP` (after a grace period) to save battery. Keep a small key queue with per-brand throttling: several TVs drop keys sent faster than about 50–100 ms apart.
- **Credentials storage.** Tokens, client keys and client certificates are sensitive (anyone holding them can control the TV). Store them encrypted with an Android Keystore-backed key (Tink + DataStore). `EncryptedSharedPreferences` is deprecated.
- **Phone hardware volume keys.** Intercept `KEYCODE_VOLUME_UP/DOWN` in the remote activity and route them to the TV. This is a very common user expectation.
- **Haptics.** A short vibration on each key press is expected in remote apps.

---

## 2. Samsung (Tizen), the first target

### 2.1 Model-year map

Samsung model codes encode the year: e.g. `UE55`**`AU`**`7100`, `QE65`**`Q`**`80`**`T`**, `UA55`**`DU`**`8300`.

| Years | Series letters | OS | Remote protocol | Notes |
|---|---|---|---|---|
| 2008–2013 | A, B, C, D, E, F | Orsay / pre-Tizen | Legacy TCP `:55000` ("iapp.samsung", base64 framed) | No auth beyond an on-TV allow prompt. Out of scope for MVP. |
| 2014–2015 | H, J (some) | Orsay / Tizen 2.3 | Encrypted pairing (`:8080` PIN + AES "SmartView 1" handshake), then WebSocket | Complex crypto. Only openHAB implements it fully. Skip for MVP. |
| 2016–2017 | K, M | Tizen 2.4–3.0 | **WebSocket `ws://IP:8001`**, no token | Allow prompt on first connect. |
| 2018–2025+ | N/NU, R, T, AU, BU, CU, DU, **QE/UE…F** (2025) | Tizen 4.0–9 | **WebSocket `wss://IP:8002` + token** | Same API. `TokenAuthSupport: "true"` in device info. |

**Antoine's TV:** whatever the year, if it is 2016 or newer the MVP path works. To check, read the model code from *Settings → Support → About This TV*, or once the app is running from `GET http://IP:8001/api/v2/`.

### 2.2 Discovery

- SSDP `ST: urn:samsung.com:device:RemoteControlReceiver:1` (also answers `urn:dial-multiscreen-org:service:dial:1` and `urn:schemas-upnp-org:device:MediaRenderer:1`). The `LOCATION` header gives the IP; the description XML includes `manufacturer=Samsung` and the model.
- Confirm and fingerprint with **`GET http://IP:8001/api/v2/`** (works without pairing). It returns JSON with `device.name`, `modelName`, `model` (e.g. `"22_PONTUSM_FTV"`, where the first 2 digits are the year), `OS: "Tizen"`, `PowerState` (`"on"` / `"standby"`, 2019+ only), `TokenAuthSupport`, `FrameTVSupport`, `wifiMac`, `networkType`, `id` (UUID).

### 2.3 Pairing

1. Open `wss://IP:8002/api/v2/channels/samsung.remote.control?name=<base64(appName)>`. On 2016–17 models use `ws://IP:8001/...` instead.
2. The TV shows an **"Allow / Deny"** popup naming the device.
3. On *Allow* the TV sends `{"event":"ms.channel.connect","data":{"id":"…","token":"12345678", …}}`. **Persist the token.**
4. On later connects append `&token=<token>`, and the popup no longer appears.
5. On *Deny* or a timeout you get `ms.channel.unauthorized` / `ms.channel.timeOut`. Show a retry.

Quirks:
- The token is tied to the `name` parameter. Changing the app's display name causes a new prompt. Use a stable name, e.g. `base64("<AppName> (Pixel 8)")`.
- The TV's device list lives at *Settings → General → External Device Manager → Device Connection Manager*. Users can deny the app there or delete it, and "Access Notification" can be set to "First time only" / "Always" / "Off". Document this in troubleshooting.
- The TV can "forget" tokens after firmware updates or a factory reset. Treat `unauthorized` with a stored token as "re-pair needed".
- Certificate: self-signed (`CN=SmartViewSDK`). TOFU-pin it.

### 2.4 Commands (WebSocket messages)

**Key press**
```json
{"method":"ms.remote.control","params":{"Cmd":"Click","DataOfCmd":"KEY_VOLUP","Option":"false","TypeOfRemote":"SendRemoteKey"}}
```
- `Cmd`: `Click` | `Press` | `Release`. Use Press/Release for long-press and hold-to-repeat (e.g. holding `KEY_RETURN` exits to home, holding `KEY_VOLUP` repeats).

**Text input** (when a TV keyboard is on screen; the TV sends `ms.remote.imeStart` / `imeEnd` events)
```json
{"method":"ms.remote.control","params":{"Cmd":"<base64 text>","DataOfCmd":"base64","TypeOfRemote":"SendInputString"}}
{"method":"ms.remote.control","params":{"TypeOfRemote":"SendInputEnd"}}
```

**Pointer / touchpad**
```json
{"method":"ms.remote.control","params":{"Cmd":"Move","Position":{"x":10,"y":-5,"Time":"1700000000"},"TypeOfRemote":"ProcessMouseDevice"}}
{"method":"ms.remote.control","params":{"Cmd":"LeftClick","TypeOfRemote":"ProcessMouseDevice"}}
```
The pointer only works in apps that show a cursor (e.g. the browser).

**Apps**
- List: `{"method":"ms.channel.emit","params":{"event":"ed.installedApp.get","to":"host"}}`. The reply comes as an `ed.installedApp.get` event with `data.data[] = {appId, name, app_type, icon}`. ⚠️ It returns empty or nothing on some 2022+ firmware. Fall back to a bundled list of well-known app IDs (below).
- Launch: `{"method":"ms.channel.emit","params":{"event":"ed.apps.launch","to":"host","data":{"appId":"3201907018807","action_type":"DEEP_LINK","metaTag":""}}}` (`action_type` is `NATIVE_LAUNCH` for native apps, `DEEP_LINK` for web apps).
- REST alternative (`http://IP:8001/api/v2/applications/<appId>`): `GET` = status, `POST` = launch, `DELETE` = close. It works without a token on many models but is unreliable on recent firmware.
- Well-known app IDs (vary by region/year, so keep the table data-driven): Netflix `3201907018807` (older: `11101200001`), YouTube `111299001912`, Prime Video `3201910019365` (older: `3201512006785`), Disney+ `3201901017640`, Spotify `3201606009684`, Apple TV `3201807016597`, Plex `3201512006963`.

**Common keys** (`DataOfCmd`)
`KEY_POWER` (toggle), `KEY_POWEROFF`, `KEY_HOME`, `KEY_MENU`, `KEY_SOURCE`, `KEY_HDMI`, `KEY_UP/DOWN/LEFT/RIGHT`, `KEY_ENTER`, `KEY_RETURN`, `KEY_EXIT`, `KEY_VOLUP/VOLDOWN/MUTE`, `KEY_CHUP/CHDOWN`, `KEY_PRECH`, `KEY_0`–`KEY_9`, `KEY_PLAY`, `KEY_PAUSE`, `KEY_STOP`, `KEY_FF`, `KEY_REWIND`, `KEY_REC`, `KEY_INFO`, `KEY_GUIDE`, `KEY_CH_LIST`, `KEY_TOOLS`, `KEY_RED/GREEN/YELLOW/CYAN`, `KEY_CAPTION`/`KEY_SUBTITLE`, `KEY_AD`, `KEY_PICTURE_SIZE`, `KEY_AMBIENT` (Ambient Mode, 2018+), `KEY_MULTI_VIEW`, `KEY_HDMI1`–`KEY_HDMI4` (⚠️ not on all models).

### 2.5 Power, volume and state

- **Power on:** Wake-on-LAN to `wifiMac` (or the Ethernet MAC). The user must enable *Settings → General → Network → Expert Settings → **Power On with Mobile*** (name varies: "Power on with mobile" / "IP Remote"). Wi-Fi WoL is unreliable on some models; Ethernet is reliable. On some models in "instant on" network standby, `KEY_POWER` over the socket also works.
- **Power off:** `KEY_POWER` (toggles) or `KEY_POWEROFF`. Frame TVs: a short `KEY_POWER` goes to Art Mode; hold it (Press, then Release after ~3 s) for a real off.
- **Power state:** `PowerState` from `/api/v2/` on 2019+; otherwise "REST answers → on, no answer → off".
- **Absolute volume / mute state:** not in the WebSocket API. Use **UPnP RenderingControl** SOAP on `http://IP:9197/upnp/control/RenderingControl1` (`GetVolume`, `SetVolume`, `GetMute`). Only works when the TV speakers are the output (not with ARC soundbars).
- **No events** for volume, source or current app over the remote channel. The UI must not promise live state for Samsung.

### 2.6 Frame TV / Art Mode (optional)

Separate channel `com.samsung.art-app` on `:8002`, with art-mode status/toggle and image upload. Nice-to-have, post-MVP.

### 2.7 SmartThings cloud API (optional adapter)

- REST `https://api.smartthings.com/v1/devices/{deviceId}/commands` with capabilities `switch`, `audioVolume`, `audioMute`, `mediaInputSource`, `tvChannel`, `mediaPlayback`, and Samsung-specific `samsungvd.remoteControl` (sends key names), `samsungvd.mediaInputSource`, `custom.launchapp`.
- **Pros:** power-on that works without WoL, works off-LAN, and gives real state (volume, input, power).
- **Cons:** requires a Samsung account and OAuth. Personal Access Tokens created since late Dec 2024 **expire after 24 h**, so a real integration needs a registered SmartApp / OAuth client (client secret problem for an open-source app). Latency is 0.5–2 s per command, and it adds a dependency on Samsung's cloud.
- **Verdict:** not for MVP. Possible later "Samsung account" add-on for power-on and state.

### 2.8 Samsung libraries

| Library | Lang | License | Notes |
|---|---|---|---|
| [xchwarze/samsung-tv-ws-api](https://github.com/xchwarze/samsung-tv-ws-api) | Python | MIT | Reference implementation (HA uses it). Remote, apps, art mode, async. |
| [NickWaterton/samsung-tv-ws-api](https://github.com/NickWaterton/samsung-tv-ws-api) | Python | MIT | Fork with much more Frame/Art support. |
| [openHAB samsungtv binding](https://github.com/openhab/openhab-addons/tree/main/bundles/org.openhab.binding.samsungtv) | **Java** | **EPL-2.0** | Most complete: legacy `:55000`, encrypted 2014–15, WebSocket, SmartThings, art. Closest to Kotlin. EPL-2.0 is weak copyleft: copying files means those files stay EPL. Read it for reference rather than copying, or isolate it in its own EPL module. |
| [Toxblh/samsung-tv-control](https://github.com/Toxblh/samsung-tv-control) | TS/Node | MIT | Simple, readable. |
| [tavicu/homebridge-samsung-tizen](https://github.com/tavicu/homebridge-samsung-tizen) | JS | MIT ⚠️ | Good real-world quirk handling (power, WoL, art mode). |
| [Ape/samsungctl](https://github.com/Ape/samsungctl) | Python | MIT | Legacy `:55000` + early WebSocket. Mostly historical. |
| [Home Assistant samsungtv](https://github.com/home-assistant/core/tree/dev/homeassistant/components/samsungtv) | Python | Apache-2.0 | Discovery, UPnP volume, power-state handling. |

---

## 3. Android TV / Google TV (Remote protocol v2)

Covers: **Sony Bravia (2015+), TCL & Hisense Google TV models, Philips Android TV, Sharp, Xiaomi, Nokia/Toshiba Android models, Chromecast with Google TV, Google TV Streamer, Nvidia Shield, many projectors/boxes**. This is the protocol behind the official Google TV phone app.

- **Service:** "Android TV Remote Service" (`com.google.android.tv.remote.service`), preinstalled on certified devices.
- **Discovery:** mDNS `_androidtvremote2._tcp` (TXT includes `bt` = Bluetooth MAC). Also `_googlecast._tcp` for fingerprinting.
- **Pairing (port 6467, TLS, "Polo" protocol):**
  1. The app generates a **self-signed RSA client certificate** (keep it forever, since it is the credential) and connects with TLS using it.
  2. Exchange protobuf messages (varint length-prefixed): `PairingRequest(service_name, client_name)` → `PairingRequestAck` → `Options(input: HEXADECIMAL, 6 symbols, role INPUT)` → `Configuration` → the TV shows a **6-char hex code**.
  3. The user types the code. The app computes `SHA-256(client_modulus ‖ client_exponent ‖ server_modulus ‖ server_exponent ‖ code[2..6] as bytes)`. The first byte of the hash must equal `code[0..2]` (this lets the app spot typos locally). Send `Secret(hash)` → `SecretAck`.
- **Control (port 6466, TLS with the same client cert):** protobuf `RemoteMessage` stream.
  - Handshake: `RemoteConfigure` (exchange device info / feature bitmask) → `RemoteSetActive`.
  - **Must answer `RemotePingRequest` with `RemotePingResponse`**, otherwise the TV drops the connection after a few seconds.
  - Keys: `RemoteKeyInject(key_code, direction)` with **Android `KeyEvent` codes** (`KEYCODE_DPAD_UP`=19, `KEYCODE_HOME`=3, `KEYCODE_BACK`=4, `KEYCODE_VOLUME_UP`=24, `KEYCODE_POWER`=26, `KEYCODE_MEDIA_PLAY_PAUSE`=85, `KEYCODE_TV_INPUT`=178, `KEYCODE_SETTINGS`=176, …). `direction` = `SHORT` | `START_LONG` | `END_LONG`.
  - Text: `RemoteImeBatchEdit` (replace the focused field's text) when the TV sends `RemoteImeShowRequest`; this gives real live text entry.
  - Apps: `RemoteAppLinkLaunchRequest(app_link)` with a URI. ⚠️ **Since Aug 2026, `market://launch?id=<package>` links stopped launching apps** on Shield, Chromecast 4K and Google TV Streamer after a Play Store update ([HA thread](https://community.home-assistant.io/t/android-tv-remote-no-longer-launching-apps/1021224)). Prefer **app deep links** (`https://www.netflix.com/title`, `https://www.youtube.com`, `vnd.youtube://`, …), which still go through the TV's intent resolver, and keep a curated per-app table.
  - State from TV: `RemoteSetVolumeLevel` (current/max volume, mute), `RemoteStart(started)` (screen on/off), `RemoteImeKeyInject` (current app package). This gives **live power, volume and current app**, which is better than Samsung.
  - **No app list** in the protocol. Use a curated list, or ADB.
- **Power on:** `KEYCODE_POWER` / `KEYCODE_WAKEUP` works while the TV is in network standby (most Google TVs keep the service alive). Otherwise use WoL.
- **Quirks:** some TVs need the "Google TV" or "Android TV Remote Service" app updated. The pairing cert is per-phone, so re-installing the app means re-pairing. The client name shows in TV settings → Remote & accessories.

**ADB over network (optional power-user adapter):** port 5555 / Android 11+ wireless debugging with a pairing code. It allows listing installed packages, launching any app (`am start`), screenshots and arbitrary key events. It requires enabling Developer options, which is not end-user friendly. Library: **[mobile-dev-inc/dadb](https://github.com/mobile-dev-inc/dadb)** (Kotlin, Apache-2.0).

**Legacy:** Google TV 2010–2013 "Anymote" (v1, ports 9551/9552). Ignore it.

| Library | Lang | License | Notes |
|---|---|---|---|
| [tronikos/androidtvremote2](https://github.com/tronikos/androidtvremote2) | Python | Apache-2.0 | Reference implementation (HA). Contains the `.proto` files we need. |
| [louis49/androidtv-remote](https://github.com/louis49/androidtv-remote) | Node | MIT | Readable pairing/crypto. |
| [drosocode/atvremote](https://github.com/drosocode/atvremote) | Go | ⚠️ check | Alternate implementation. |
| Kotlin side | n/a | n/a | No well-maintained Kotlin library found. Write our own with **Wire** or `protobuf-lite` (Apache-2.0) + **Bouncy Castle** (MIT-style) for cert generation. About 600–900 LOC. |

---

## 4. LG webOS (2014+)

- **Discovery:** SSDP `ST: urn:lge-com:service:webos-second-screen:1`. Also DIAL and AirPlay (2018+).
- **Transport:** WebSocket JSON "SSAP" (Simple Service Access Protocol). `ws://IP:3000` on older sets; **`wss://IP:3001` is required on 2022+ firmware** (and recommended everywhere). Self-signed cert.
- **Pairing:** send `{"type":"register","payload":{"pairingType":"PROMPT","manifest":{…,"permissions":[…]}}}`. The TV shows an accept prompt and replies with `{"type":"registered","payload":{"client-key":"…"}}`. **Persist `client-key`** and include it in later registers. A `PIN` pairing type also exists (code shown on TV). The manifest permission list decides which APIs are allowed. Copy the standard list from aiowebostv / Connect SDK.
- **Commands:** `{"type":"request","id":"n","uri":"ssap://…","payload":{…}}`
  - Volume: `ssap://audio/volumeUp|volumeDown|setVolume|setMute|getVolume` (subscribe for live volume).
  - Power: `ssap://system/turnOff`. Screen off: `ssap://com.webos.service.tvpower/power/turnOffScreen`.
  - Apps: `ssap://com.webos.applicationManager/listLaunchPoints` (**full app list with icons**), `ssap://system.launcher/launch {id, contentId, params}`, `ssap://com.webos.applicationManager/getForegroundAppInfo` (subscribable).
  - Inputs/channels: `ssap://tv/getExternalInputList`, `ssap://tv/switchInput`, `ssap://tv/channelUp|channelDown|openChannel|getCurrentChannel`.
  - Media: `ssap://media.controls/play|pause|stop|rewind|fastForward`.
  - Text: `ssap://com.webos.service.ime/insertText`, `deleteCharacters`, `sendEnterKey`.
  - Toast: `ssap://system.notifications/createToast` (handy for "paired!" feedback).
  - **Buttons & pointer:** `ssap://com.webos.service.networkinput/getPointerInputSocket` returns a `socketPath`. Open a **second WebSocket** to it and send line-based frames: `type:button\nname:UP\n\n`, `type:move\ndx:5\ndy:-3\ndown:0\n\n`, `type:click\n\n`, `type:scroll\ndx:0\ndy:5\n\n`. Button names: `UP DOWN LEFT RIGHT ENTER BACK HOME MENU EXIT INFO RED GREEN YELLOW BLUE VOLUMEUP VOLUMEDOWN MUTE CHANNELUP CHANNELDOWN PLAY PAUSE 0-9 ASTERISK CC DASH QMENU …`.
- **Power on:** WoL. The user enables *"Turn on via Wi-Fi"* / *"Mobile TV On"* (and on some years "Quick Start+").
- **Quirks:** the TV re-prompts if the manifest changes. Pointer-socket buttons are the only way to send some keys. Some 2023+ firmware rate-limits rapid connections.
- **Security note:** in Oct 2026 SSD Disclosure published a pre-auth chain via the **`:3001` pairing service** (a manifest permission made it skip the PIN and issue a privileged key; tested on webOS 7.5.x) ([The Hacker News, 2026-10-13](https://thehackernews.com/2026/10/lg-smart-tvs-vulnerable-to-pre-auth.html)). LG shipped a fix. ⚠️ We don't yet know whether the patch tightened manifest permissions for third-party clients. Test pairing on a patched TV before building on it, and only request the permissions we need.
- **Legacy LG NetCast (2011–2013):** HTTP `:8080/roap/api/`, pairing key shown on screen, XML commands. Low priority.

| Library | Lang | License | Notes |
|---|---|---|---|
| [home-assistant-libs/aiowebostv](https://github.com/home-assistant-libs/aiowebostv) | Python | Apache-2.0 | Maintained reference, current manifest. |
| [hobbyquaker/lgtv2](https://github.com/hobbyquaker/lgtv2) | Node | MIT | Classic, readable. |
| [klattimer/LGWebOSRemote](https://github.com/klattimer/LGWebOSRemote) | Python | MIT | CLI with a full command list. |
| [jareksedy/WebOSClient](https://github.com/jareksedy/WebOSClient) | Swift | MIT | Clean mobile-oriented design worth mirroring. |
| [ConnectSDK/Connect-SDK-Android](https://github.com/ConnectSDK/Connect-SDK-Android) | **Java/Android** | **Apache-2.0** | Archived (~2017), but includes webOS, NetCast, Roku, DIAL, Fire TV, and a capability-based architecture. **Best architectural reference for our adapter layer.** |

---

## 5. Roku (Roku players and Roku TVs: TCL, Hisense, Sharp, etc.)

- **Officially documented:** [External Control Protocol (ECP)](https://developer.roku.com/docs/developer-program/dev-tools/external-control-api.md).
- **Discovery:** SSDP `ST: roku:ecp`. The `LOCATION` header gives `http://IP:8060/`.
- **Auth:** none. Since Roku OS 10 there is a user setting *Settings → System → Advanced → Control by mobile apps → Network access* (Default / Permissive / Limited / Disabled). "Limited" blocks most commands, so detect the 403 and explain it.
- **Commands (HTTP POST, empty body):** `/keypress/<Key>`, `/keydown/<Key>`, `/keyup/<Key>`, `/launch/<appId>?contentId=…&mediaType=…`.
  - Keys: `Home Rev Fwd Play Select Left Right Down Up Back InstantReplay Info Backspace Search Enter VolumeUp VolumeDown VolumeMute PowerOff PowerOn ChannelUp ChannelDown InputTuner InputHDMI1..4 InputAV1 FindRemote`. Text: `Lit_<urlencoded char>` (one request per character).
- **Queries (GET, XML):** `/query/device-info` (model, MAC, power-mode, is-tv), `/query/apps` (full app list), `/query/icon/<appId>` (PNG), `/query/active-app`, `/query/media-player`, `/query/tv-active-channel`.
- **Power on:** `PowerOn` keypress works on Roku TVs with "Fast TV start" enabled; otherwise WoL. Roku sticks have no power concept.
- **No live events** (poll `active-app` if needed).

| Library | Lang | License |
|---|---|---|
| [ctalkington/python-rokuecp](https://github.com/ctalkington/python-rokuecp) | Python | MIT |
| Connect SDK (Roku service) | Java | Apache-2.0 |

---

## 6. Sony Bravia

- **2015+ Bravias are Android TV / Google TV**, so the Android TV adapter covers them. Sony also exposes an **official** IP control API ([pro-bravia.sony.net](https://pro-bravia.sony.net/develop/integrate/ip-control/)), useful for extras like picture settings, accurate input switching and power.
  - **REST/JSON-RPC:** `POST http://IP/sony/<service>` (`system`, `audio`, `avContent`, `appControl`). E.g. `system/setPowerStatus`, `audio/setAudioVolume`, `avContent/setPlayContent` (inputs), `appControl/getApplicationList` (**app list**), `appControl/setActiveApp`.
  - **IRCC-IP:** SOAP `POST /sony/IRCC` with base64 IR codes obtained from `system/getRemoteControllerInfo`.
  - **Auth:** Pre-Shared Key (`X-Auth-PSK` header; the user sets it on the TV under *Network → Home network → IP control → Authentication → Normal and Pre-Shared Key*) **or** PIN pairing via `accessControl/actRegister` (4-digit PIN on screen, then a cookie). Requires "Remote start" for power-on.
  - **Discovery:** SSDP `urn:schemas-sony-com:service:ScalarWebAPI:1`.
- Libraries: [Drafteed/pybravia](https://github.com/Drafteed/pybravia) (Python, MIT). **Verdict:** use Android TV v2 by default; add a Sony "enhancer" later if users want inputs, app list and power state.

## 7. Philips

- **2016+ Philips Android TVs:** use the Android TV adapter. Philips also has the unofficial **JointSpace API v6**: HTTPS `:1926`, pairing `POST /6/pair/request` (PIN on screen) then `/6/pair/grant` with an HMAC-SHA1 signature using a shared secret known from Philips' app, giving **digest-auth** credentials. Then `POST /6/input/key {"key":"VolumeUp"}`, `/6/audio/volume`, `/6/activities/launch` (app list via `/6/applications`), `/6/ambilight/*` (**Ambilight control**, a nice brand-specific extra).
- **Non-Android Philips (Saphi, 2016+) and older:** JointSpace v1/v5 over plain HTTP `:1925`, no auth.
- Libraries: [eslavnov/pylips](https://github.com/eslavnov/pylips) (Python, MIT), [danielperna84/ha-philipsjs](https://github.com/danielperna84/ha-philipsjs) (Python, MIT).

## 8. Vizio SmartCast (mostly US)

- **Discovery:** SSDP `urn:dial-multiscreen-org:device:dial:1` (manufacturer VIZIO) or mDNS `_viziocast._tcp`.
- **Transport:** HTTPS REST on **`:7345`** (older firmware `:9000`), self-signed cert.
- **Pairing:** `PUT /pairing/start {"DEVICE_NAME","DEVICE_ID"}` → PIN on TV → `PUT /pairing/pair {"DEVICE_ID","CHALLENGE_TYPE":1,"RESPONSE_VALUE":"<pin>","PAIRING_REQ_TOKEN":n}` → `AUTH_TOKEN`. Send it as the `AUTH: <token>` header afterwards.
- **Commands:** `PUT /key_command/ {"KEYLIST":[{"CODESET":n,"CODE":n,"ACTION":"KEYPRESS"}]}` (codeset/code table in pyvizio). `GET /state/device/power_mode`, `/menu_native/dynamic/tv_settings/audio/volume`, inputs `/menu_native/dynamic/tv_settings/devices/current_input`, app launch `PUT /app/launch` (namespace/app ID/message from pyvizio's app list).
- **Quirks:** Walmart acquired Vizio (closed Dec 2024). ⚠️ Newer Vizio OS updates may change the API. Some models drop the API in deep standby.
- Library: [raman325/pyvizio](https://github.com/raman325/pyvizio) (Python, MIT).

## 9. Others (lower priority)

| Platform | Protocol summary | Library (license) |
|---|---|---|
| **Hisense VIDAA** (non-Google Hisense, some Toshiba) | MQTT over TLS on `:36669`, credentials and client certificates extracted from Hisense's app, 4-digit PIN auth on newer firmware. Keys via `/remoteapp/tv/remote_service/<id>/actions/sendkey`, app list and launch topics. Fragile and credential-dependent. | [Krazy998/mqtt-hisensetv](https://github.com/Krazy998/mqtt-hisensetv), [newAM/hisensetv](https://github.com/newAM/hisensetv) (MIT) ⚠️ |
| **Fire TV / Fire TV Edition TVs** (Toshiba, Insignia…) | Fire OS = Android: ADB `:5555` (needs dev options). Amazon's own phone-remote protocol is private. Fire OS 8 is moving to Vega OS (Linux) on new devices ⚠️, and ADB may not exist there. | dadb (Apache-2.0) |
| **Panasonic Viera** | SOAP `:55000` (`/nrc/control_0`), 2019+ needs PIN + AES-encrypted session. | [florianholzapfel/panasonic-viera](https://github.com/florianholzapfel/panasonic-viera) (MIT) |
| **Tizen-on-other-brands / Titan OS (Philips/JVC non-Android 2024+)** | ⚠️ Unknown; no public control protocol found. Fallback: DIAL + IR. | n/a |
| **DIAL (generic)** | SSDP `urn:dial-multiscreen-org:service:dial:1` → `Application-URL` header → `POST <url>/YouTube` / `Netflix` to launch. Works on most smart TVs; launch only. | Connect SDK (Apache-2.0) |
| **Google Cast** | Cast v2 (protobuf over TLS `:8009`): launch receiver apps, set volume. Not a remote, but useful for volume on Cast-only devices. | n/a |
| **IR blaster** | Android `ConsumerIrManager` (Xiaomi/Redmi/POCO, some Huawei/Honor phones). Universal, needs no network and covers power-on for any TV. Use IR code databases such as [IRDB](https://github.com/probonopd/irdb) (check per-file licenses) or the Flipper IRDB. | Built into Android |

---

## 10. Capability matrix (what the UI can rely on)

| Capability | Samsung | Android TV | LG | Roku | Vizio | Sony REST | Philips v6 |
|---|---|---|---|---|---|---|---|
| D-pad / nav keys | ✅ | ✅ | ✅ (pointer socket) | ✅ | ✅ | ✅ (IRCC) | ✅ |
| Long press / hold | ✅ Press/Release | ✅ START/END_LONG | ⚠️ partial | ✅ keydown/up | ⚠️ | ❌ | ❌ |
| Relative volume | ✅ | ✅ | ✅ | ✅ (Roku TV) | ✅ | ✅ | ✅ |
| Absolute volume + live level | ⚠️ UPnP, no events | ✅ events | ✅ subscribe | ❌ | ✅ poll | ✅ | ✅ |
| Power off | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Power on | WoL | key / WoL | WoL | key / WoL | key | ✅ API | WoL |
| Power state | ✅ (2019+) | ✅ events | ✅ | ✅ | ✅ | ✅ | ✅ |
| App list | ⚠️ flaky | ❌ (curated) | ✅ + icons | ✅ + icons | ⚠️ curated | ✅ | ✅ |
| Launch app | ✅ | ⚠️ deep links | ✅ | ✅ | ✅ | ✅ | ✅ |
| Current app | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Text input | ✅ | ✅ (live field) | ✅ | ⚠️ per-char | ❌ | ✅ | ✅ |
| Pointer / touchpad | ✅ | ❌ (d-pad swipe) | ✅ | ❌ | ❌ | ❌ | ❌ |
| Input source select | ⚠️ keys only | ⚠️ key | ✅ list | ✅ keys | ✅ | ✅ list | ✅ |
| Toast/notification | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |

The trackpad on Android TV and Roku should be emulated: swipe gestures become d-pad keys. That is what the official Google TV app does.

---

## 11. Proposed adapter architecture (Kotlin)

### 11.1 Module layout

```
:core:remote-api        // interfaces below, no Android deps (pure Kotlin, testable on JVM)
:core:network           // SSDP, mDNS, WoL, TOFU TLS, WebSocket helpers
:core:credentials       // encrypted credential store
:adapter:samsung-tizen  // P0
:adapter:androidtv      // P1 (protobuf)
:adapter:lg-webos       // P1
:adapter:roku           // P1
:adapter:vizio, :adapter:sony, :adapter:philips, :adapter:ir ...
:app                    // UI only talks to :core:remote-api
```

Each adapter registers a `TvPlatform` through a simple registry (manual list or Hilt multibinding). Adding a brand means adding one module and not touching the UI.

### 11.2 Core interfaces

```kotlin
/** One per protocol family. Stateless factory + discovery. */
interface TvPlatform {
    val id: PlatformId                          // "samsung-tizen", "androidtv-v2", "lg-webos", "roku-ecp"…
    val displayName: String

    /** Emits candidates while scanning; core merges duplicates by MAC/UUID. */
    fun discover(scope: DiscoveryScope): Flow<DiscoveredTv>

    /** Cheap probe for manual IP entry: "is this host one of mine?" */
    suspend fun probe(host: String): DiscoveredTv?

    fun createSession(device: TvDevice, credentials: CredentialStore): TvSession
}

data class DiscoveredTv(
    val platformId: PlatformId,
    val host: String,
    val port: Int?,
    val name: String,
    val manufacturer: String?,
    val model: String?,
    val macAddress: String?,       // for WoL + dedupe
    val uniqueId: String?,         // UUID/serial
    val extra: Map<String, String> = emptyMap(),
)

/** A live connection to one TV. */
interface TvSession {
    val device: TvDevice
    val connectionState: StateFlow<ConnectionState>   // Disconnected, Connecting, PairingRequired(PairingRequest), Connected, Error(TvError)
    val capabilities: StateFlow<Set<Capability>>      // may grow after connect (e.g. model-year detection)

    suspend fun connect()
    suspend fun disconnect()

    /** Universal entry point used by every button, including user-defined ones. */
    suspend fun perform(action: RemoteAction): Result<Unit>

    /** Optional feature facets; null when unsupported. */
    val keys: KeyControl
    val volume: VolumeControl?
    val power: PowerControl?
    val apps: AppControl?
    val text: TextInputControl?
    val pointer: PointerControl?
    val inputs: InputSourceControl?
    val media: MediaControl?
    val state: TvStateObserver?   // live power/volume/foreground app where the protocol supports it
}
```

### 11.3 Pairing as a sealed flow

```kotlin
sealed interface PairingRequest {
    /** Samsung, LG PROMPT, ADB: user accepts on TV; just wait. */
    data class ConfirmOnTv(val timeout: Duration) : PairingRequest
    /** Android TV (6 hex), Vizio/Sony/Philips/Hisense (4 digits): user types code shown on TV. */
    data class EnterCodeShownOnTv(
        val length: Int,
        val alphabet: CodeAlphabet,                 // DIGITS, HEX
        val submit: suspend (String) -> Result<Unit>,
    ) : PairingRequest
    /** Sony PSK: user sets a key on the TV and enters it in the app. */
    data class EnterPreSharedKey(val submit: suspend (String) -> Result<Unit>) : PairingRequest
    /** Prerequisite the user must toggle on the TV (Roku "Limited" mode, LG "Mobile TV On"…). */
    data class EnableSettingOnTv(val instructions: StringRes, val retry: suspend () -> Unit) : PairingRequest
}
```

Credentials are an opaque `ByteArray` per `(platformId, deviceId)`, stored encrypted by `:core:credentials`. Samsung stores a token + TLS pin, Android TV a client cert/key + server pin, LG a `client-key`. The UI never sees them.

### 11.4 Keys and actions (portable customizable buttons)

```kotlin
/** Brand-neutral key vocabulary. Adapters map what they can; unmapped keys are hidden or disabled in the UI. */
enum class RemoteKey {
    POWER, POWER_ON, POWER_OFF,
    UP, DOWN, LEFT, RIGHT, OK, BACK, HOME, MENU, EXIT, INFO, GUIDE, SETTINGS, SOURCE,
    VOLUME_UP, VOLUME_DOWN, MUTE, CHANNEL_UP, CHANNEL_DOWN, PREVIOUS_CHANNEL,
    DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
    PLAY, PAUSE, PLAY_PAUSE, STOP, FAST_FORWARD, REWIND, NEXT, PREVIOUS, RECORD,
    RED, GREEN, YELLOW, BLUE, SUBTITLES, AUDIO_TRACK, SEARCH, VOICE, ASSISTANT,
    HDMI_1, HDMI_2, HDMI_3, HDMI_4,
}

enum class KeyAction { CLICK, PRESS, RELEASE }   // PRESS/RELEASE only if Capability.LONG_PRESS

/** What a user-placed button does. Serializable -> stored in layout JSON. */
@Serializable sealed interface RemoteAction {
    @Serializable data class Key(val key: RemoteKey, val action: KeyAction = KeyAction.CLICK) : RemoteAction
    @Serializable data class LaunchApp(val app: AppRef) : RemoteAction        // AppRef = per-platform id map + display name/icon
    @Serializable data class SendText(val text: String) : RemoteAction
    @Serializable data class SetVolume(val level: Int) : RemoteAction
    @Serializable data class SelectInput(val inputId: String) : RemoteAction
    @Serializable data class Macro(val steps: List<RemoteAction>, val delayMs: Long = 300) : RemoteAction
    /** Escape hatch for brand-specific keys (KEY_AMBIENT, Ambilight, LG toast…). Only offered for that platform. */
    @Serializable data class Raw(val platformId: PlatformId, val command: String, val args: Map<String, String> = emptyMap()) : RemoteAction
}
```

- `AppRef` holds a **cross-platform catalog entry** ("Netflix" → Samsung `3201907018807`, LG `netflix`, Roku `12`, Android TV deep link `https://www.netflix.com/title`). A "Netflix" shortcut then keeps working when the user switches TVs. Ship the catalog as a JSON asset and merge it with the live app list where the platform provides one.
- The layout editor queries `session.capabilities` and the adapter's key map to grey out or hide buttons the current TV can't do.

### 11.5 Capability facets (examples)

```kotlin
enum class Capability { KEYS, LONG_PRESS, VOLUME_RELATIVE, VOLUME_ABSOLUTE, VOLUME_STATE,
    POWER_OFF, POWER_ON, POWER_STATE, APP_LIST, APP_LAUNCH, CURRENT_APP, TEXT_INPUT, TEXT_LIVE_FIELD,
    POINTER, INPUT_LIST, INPUT_SELECT, MEDIA_CONTROL, TOAST }

interface VolumeControl { suspend fun up(); suspend fun down(); suspend fun setMuted(muted: Boolean)
    suspend fun set(level: Int) /* if VOLUME_ABSOLUTE */ }
interface PowerControl { suspend fun off(); suspend fun on() /* adapter picks WoL/key/API */ }
interface AppControl { suspend fun list(): List<TvApp>; suspend fun launch(app: AppRef); suspend fun current(): TvApp? }
interface TextInputControl { val keyboardVisible: StateFlow<Boolean>; suspend fun send(text: String); suspend fun submit() }
interface PointerControl { suspend fun move(dx: Int, dy: Int); suspend fun click(); suspend fun scroll(dy: Int) }
interface TvStateObserver { val power: StateFlow<PowerState?>; val volume: StateFlow<VolumeState?>; val foregroundApp: StateFlow<TvApp?> }
```

`PowerControl.on()` is special: it must work while disconnected. Implement it in a shared `WakeStrategy` (WoL → wait → probe), which adapters can override (Roku `PowerOn` key, Sony API, SmartThings later).

### 11.6 Errors

```kotlin
sealed interface TvError {
    data object Unreachable : TvError                 // offline / wrong network / AP isolation
    data object PairingRejected : TvError             // user pressed Deny
    data object CredentialsInvalid : TvError          // token revoked -> re-pair
    data object CertificateChanged : TvError          // TOFU mismatch -> confirm re-pair
    data class DisabledOnTv(val setting: StringRes) : TvError   // Roku Limited, LG mobile-on off…
    data class Unsupported(val capability: Capability) : TvError
    data class Protocol(val message: String, val cause: Throwable? = null) : TvError
}
```

### 11.7 Testing strategy

- Each adapter gets a **fake TV server** in tests (OkHttp `MockWebServer` for WS/HTTP, a small TLS protobuf server for Android TV) that replays recorded real-TV transcripts. Record Antoine's Samsung session early to seed them.
- A shared **contract test suite** runs against every adapter: connect → pair → keys → volume → disconnect/reconnect → re-pair on revoked credentials.

---

## 12. Licensing summary for an open-source app

- **Safe to port/adapt with attribution:** MIT and Apache-2.0 projects (samsung-tv-ws-api, androidtvremote2 incl. `.proto` files, aiowebostv, lgtv2, Connect SDK, python-rokuecp, pyvizio, pylips, dadb). Keep a `THIRD_PARTY_NOTICES` file. Apache-2.0 also requires keeping NOTICE files and stating changes.
- **Reference only (don't copy code unless the project license matches):** openHAB (EPL-2.0), any GPL projects. Reading them to understand a protocol is fine; protocols themselves are not copyrightable.
- **Suggested app license:** Apache-2.0 (patent grant, compatible with everything above) or GPL-3.0 if Antoine wants to prevent closed forks. That decision belongs to the repository-setup thread.
- **Trademarks:** brand names may appear descriptively ("works with Samsung TVs"), but don't use brand logos as the app icon or imply endorsement. Play Store policy is strict about this.
- **Shared-secret caveat:** Philips v6 and Hisense VIDAA rely on secrets/certs extracted from vendor apps. Shipping them in an open-source repo is legally grey. Put those adapters behind a flag, or skip them.

---

## 13. Open questions / next steps

1. **Antoine's Samsung (confirmed):** `UE75RU7020KXXU`, a 2019 "R" series (RU7020, 75", UK/EU variant, not a Frame), firmware `T-MSLDEUC-1510.0` (Tizen 5.0 platform). It falls in the **2018+ `wss://IP:8002` + token** group, so the MVP path applies as written. Expected `model` in `/api/v2/` starts with `19_`. Still to verify on the set: whether `PowerState` is reported (⚠️ uncertain on 2019 entry/mid-range models), whether `ed.installedApp.get` returns the app list, and whether WoL works over Wi-Fi or only Ethernet ("Power On with Mobile" is already enabled on this set). Antoine confirmed third-party remote apps from the Play Store already control this TV over the network, which is strong evidence the WebSocket path works on this firmware. Those apps also powered the TV on from off with the phone on Wi-Fi, so **WoL over Wi-Fi works on this set**. Next step: a 10-minute spike against it (SSDP → `/api/v2/` → `wss://8002` pairing → `KEY_VOLUP`).
2. Validate on real hardware: Samsung app list (`ed.installedApp.get`) on his firmware; WoL over Wi-Fi vs Ethernet.
3. Confirm the Android 17 local-network permission requirements before choosing `targetSdk`.
4. Decide whether the MVP includes an IR-blaster fallback (cheap to add, useful for power-on).
5. Pick the app license (repo setup thread).

---

## Sources

Verified/consulted for this report (2026-11-13), in addition to the repositories linked inline:

- Samsung WS API: [samsung-tv-ws-api (NickWaterton)](https://github.com/NickWaterton/samsung-tv-ws-api), [samsungtvws on PyPI](https://pypi.org/p/samsungtvws), [platypush Samsung WS docs](https://docs.platypush.tech/platypush/plugins/tv.samsung.ws.html), [openHAB Samsung TV binding thread](https://community.openhab.org/t/samsung-tv-binding-beta/130551), [Samsung dev forum: WebSocket on DU8300 (2024)](https://forum.developer.samsung.com/t/issue-with-websocket-remote-control-on-samsung-tv-model-ua55du8300ulxl/40467), [Samsung dev forum: launching apps via WebSocket](https://forum.developer.samsung.com/t/remote-control-launch-tv-apps-via-websocket-interface/36812), [Controlling Samsung TVs with Swift WebSockets](https://wdesimini.github.io/controlling-samsung-tvs-using-swift-based-websockets)
- Android TV Remote v2: [androidtvremote2 (PyPI)](https://pypi.org/project/androidtvremote2/), [tronikos/androidtvremote2](https://github.com/tronikos/androidtvremote2), [kud/androidtv-remote](https://github.com/kud/androidtv-remote), [protocol write-up (Aymkdn wiki)](https://github.colasdn.workers.dev/Aymkdn/assistant-freebox-cloud/wiki/Google-TV-(aka-Android-TV)-Remote-Control-(v2)), [HA: Android TV Remote no longer launching apps (Aug–Oct 2026)](https://community.home-assistant.io/t/android-tv-remote-no-longer-launching-apps/1021224)
- LG webOS: [LGWebOSRemote](https://www.github.com/klattimer/LGWebOSRemote), [WebOSClient (Swift)](https://swiftpackageregistry.com/jareksedy/WebOSClient), [lgtv-ts](https://npmjs.com/package/lgtv-ts), [go-lgtv](https://github.com/sweharris/go-lgtv), [LG dev forum: connecting mobile apps](https://forum.webostv.developer.lge.com/t/connecting-mobile-apps-to-lg-webos-tv/3082), [The Hacker News: LG pre-auth via :3001 (2026-10-13)](https://thehackernews.com/2026/10/lg-smart-tvs-vulnerable-to-pre-auth.html), [SSD Disclosure advisory](https://ssd-disclosure.com/lg-webos-tv-path-traversal-authentication-bypass-and-full-device-takeover/)
- Vizio / Philips: [Home Assistant Vizio](https://home-assistant.io/components/vizio), [openHAB Vizio binding](https://www.openhab.org/addons/bindings/vizio/), [pyvizio](https://pypi.tcis.de/data/web/json/pyvizio), [Pylips wiki](https://github.com/eslavnov/pylips/wiki), [suborb/philips_android_tv](https://github.com/suborb/philips_android_tv)
- Roku: [Roku ECP documentation](https://developer.roku.com/docs/developer-program/dev-tools/external-control-api.md)
- Sony: [Sony BRAVIA Professional IP control](https://pro-bravia.sony.net/develop/integrate/ip-control/)

⚠️ Items marked with this symbol were written from prior knowledge of these projects and not re-checked against live sources during this session.
