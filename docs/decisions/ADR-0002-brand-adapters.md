---
id: ADR-0002
title: Reach every TV brand through a capability-based adapter, one module per brand
status: accepted
date: 2026-11-13
supersedes:
superseded_by:
---

# ADR-0002 — Reach every TV brand through a capability-based adapter, one module per brand

## Context

Samsung is the first target, but the app must support every smart TV it reasonably can. The
protocols have little in common: Samsung Tizen uses a WebSocket with a pairing token, LG webOS a
WebSocket with a different handshake, Google and Android TV a TLS socket with protobuf and a PIN,
Roku plain HTTP, Sony and Philips REST APIs, and phones with an IR blaster can drive almost
anything. Features differ too: one TV can launch apps or take text input, another cannot. If the UI
knows about brands, every new brand means touching every screen.

## Decision

- The UI talks only to the `TvAdapter` contract in `:core:remote`. No code outside an adapter
  module knows which brand it is talking to.
- An adapter declares what it **can do** as a set of capabilities (keys, text input, app launch,
  power on, volume, pointer…). The UI shows, hides or disables controls from those capabilities,
  never from the brand.
- Each brand lives in its own module, `:adapters:<brand>`, depending on `:core:model` and
  `:core:remote` only. Adapters never depend on each other or on `:app`.
- Adapters register with an `AdapterRegistry`. Discovery yields candidate TVs, and the registry asks
  each adapter whether it recognises one.
- A remote key is a brand-neutral `RemoteKey`. Translating it to a protocol code is the adapter's
  job.

## Consequences

**Good**

- Adding a brand is a new module plus one registration line, with no change to the UI.
- A customised layout keeps working on another brand's TV: unsupported buttons show as unavailable
  instead of breaking the layout.
- Each adapter can be tested alone against a fake TV, and contributors can own one brand.

**Costs and risks**

- The contract has to be general enough for very different protocols. The first version is shaped
  by one brand, so expect it to change when the second arrives.
- Brand-specific extras need a capability to surface them; a TV feature with no capability is
  invisible.

## Alternatives considered

**One remote implementation per brand, each with its own screens** lost because layouts and
shortcuts could not be shared, and the UI work grows with every brand.

**Building on an existing all-brands library** lost because no maintained, Android-friendly,
GPL-compatible library covers the brands needed. Existing ones are mostly Python or Node, or cover
a single brand.

**Brand adapters as runtime plugins (separate APKs)** lost because it adds install friction and
IPC for no benefit while every adapter ships in the same open-source app.

## Revisit triggers

1. A brand's protocol cannot be expressed with keys plus capabilities without bending the contract.
2. The app needs third parties to ship adapters outside this repository.

## Admission check

Against [the rule](README.md): (1) constrains future work ✅ · (2) a real alternative was rejected
with a stated reason ✅ · (3) reversing later costs meaningfully more ✅ · (4) not owned by another
document ✅ · (5) not an implementation detail ✅.
