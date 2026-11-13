---
description: >-
  Rules for TV brand adapters and the adapter contract. Load when editing anything under
  adapters/ or core/remote/.
paths:
  - "adapters/**"
  - "core/remote/**"
enforcement: review-only
---

# Brand adapters

- An adapter module depends on `:core:model` and `:core:remote` only. Never on `:app`, `:core:data`,
  Android, or another adapter (ADR-0002). Gradle enforces the module graph; review enforces the
  dependency list.
- Adapters are plain Kotlin/JVM modules so their tests run without an emulator. Anything Android
  (NsdManager, multicast locks, Wi-Fi state) is passed in from `:app`, not imported.
- Declare only capabilities the TV really has. A key you cannot confirm maps to `null` and is not
  sent: a wrong code fails silently on the TV.
- Every adapter's session test extends `TvSessionContract` (`core/remote/src/testFixtures`) and adds
  its own cases for pairing, a refused pairing and a rejected token.
- Throw `TvException` with a `TvError`, never a raw exception: the UI explains errors by type.
- No adapter talks to anything but the TV on the local network (ADR-0003).
