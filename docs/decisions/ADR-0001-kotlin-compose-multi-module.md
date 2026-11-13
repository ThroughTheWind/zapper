---
id: ADR-0001
title: Build the app natively in Kotlin and Jetpack Compose, as a multi-module Gradle project
status: accepted
date: 2026-11-13
supersedes:
superseded_by:
---

# ADR-0001 — Build the app natively in Kotlin and Jetpack Compose, as a multi-module Gradle project

## Context

The app is a free, open-source TV remote for Android phones only. It has to talk to TVs over the
local network with brand-specific protocols (WebSockets with self-signed TLS, raw TLS with protobuf,
HTTP, UDP discovery, Wake-on-LAN), stay responsive while a socket reconnects in the background,
feel instant on a key press, and let people rearrange buttons and shortcuts freely. Samsung is the
first target; more brands must be addable without touching the app or each other (ADR-0002). There
is no iOS, web or desktop target, so a cross-platform framework buys nothing it does not also cost.

## Decision

- The app is **Kotlin**, with **Jetpack Compose and Material 3** for all UI. No XML views.
- The build is a **multi-module Gradle project** using Kotlin DSL and one version catalog
  (`gradle/libs.versions.toml`) for every version and SDK level:
  - `:app` — the Android application: screens, navigation, wiring.
  - `:core:model` — pure Kotlin: keys, devices, capabilities, the layout model.
  - `:core:remote` — pure Kotlin: the `TvAdapter` contract and the adapter registry.
  - `:core:data` — Android library: persistence of layouts, paired TVs and pairing tokens.
  - `:core:designsystem` — Android library: the Compose theme and shared components.
  - `:adapters:<brand>` — one module per brand, starting with `:adapters:samsung`.
- Modules that do not need Android (`core:model`, `core:remote`, brand adapters) are **plain
  Kotlin/JVM modules**, so protocol code is unit-tested on the JVM without an emulator.
- Async work is **Kotlin coroutines and Flow**. Networking is **OkHttp** (HTTP and WebSocket).
  JSON is **kotlinx.serialization**. Persistence is **Jetpack DataStore**.
- Dependency injection is **manual constructor injection** through one app-level container.
- Minimum SDK is **26** (Android 8.0).

## Consequences

**Good**

- First-class access to everything the protocols need: `NsdManager` for mDNS, multicast locks for
  SSDP, custom `TrustManager`s for TVs with self-signed certificates, `ConsumerIrManager` for an IR
  fallback, and haptics for a remote that feels physical.
- A brand adapter is a JVM library with no Android dependency, testable against a fake TV server
  in milliseconds, and it cannot reach into the UI.
- Compose makes a user-editable grid of buttons (drag, resize, re-skin) a data problem: the layout
  is a serialisable model and the screen is a function of it.
- Module boundaries make the adapter rule (ADR-0002) structural: an adapter that imports `:app`
  fails to compile.

**Costs and risks**

- Multi-module Gradle has a setup cost and slower clean builds than a single module. Module build
  files repeat a few lines of Android setup; move them into convention plugins in `build-logic/`
  once there are more than three Android modules or the duplication causes a mismatch.
- No DI framework means wiring by hand. Fine at this size; it becomes noise past a few dozen
  bindings.
- minSdk 26 excludes the small share of devices still on Android 7 and older.
- Contributors need Android Studio and a JDK; CI needs the Android SDK.

## Alternatives considered

**Flutter** lost because its only advantage is iOS, which is out of scope, while every protocol
and discovery feature would sit behind platform channels written in Kotlin anyway.

**React Native** lost for the same reason, plus a JS bridge between a key press and the socket, and
a weaker fit for a native-feeling, haptic, Material You UI.

**Kotlin Multiplatform with Compose Multiplatform** lost for now: it adds build complexity for a
second platform nobody asked for. The pure-Kotlin core and adapter modules keep that door open.

**XML views** lost because a free-form editable layout is far simpler as declarative UI, and
Google's tooling and Material 3 work now land in Compose first.

**Hilt** lost against manual DI for now: it adds an annotation processor and build time to wire
fewer than twenty objects. **Koin** lost for the same reason, and for resolving failures at
runtime instead of compile time.

**Room** lost against DataStore: layouts and paired TVs are a few small documents, not relational
data that needs queries.

**Ktor client** lost against OkHttp by a small margin: OkHttp's WebSocket and TLS hooks are the
better-trodden path for TVs with self-signed certificates, and MockWebServer gives adapters a fake
TV for tests.

## Revisit triggers

1. The project takes on iOS or another platform: reopen Kotlin Multiplatform.
2. The manual container passes about thirty bindings, or wiring bugs appear: adopt Hilt.
3. A brand protocol needs something OkHttp cannot do cleanly.
4. Usage data or user reports show meaningful demand from Android 7 devices.

## Admission check

Against [the rule](README.md): (1) constrains future work ✅ · (2) a real alternative was rejected
with a stated reason ✅ · (3) reversing later costs meaningfully more ✅ · (4) not owned by another
document ✅ · (5) not an implementation detail ✅.
