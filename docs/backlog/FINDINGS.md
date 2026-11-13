**Authoritative for:** what has been noticed and not yet decided, and the disposition of everything closed.
**Not authoritative for:** what to do about any of it — a finding becomes a work item before it becomes work.

# Findings

Things noticed while doing something else. **A finding is the observation; a work item is the
decision.** Recording one must cost almost nothing, or it will not happen — so a finding is a
**row**, not a file. Items are files; findings are rows. The asymmetry is deliberate.

<!-- NEXT-ID: F-004 -->

## Open

| Id | Sev | Pri | What | Evidence | When to act | How to fix |
| --- | --- | --- | --- | --- | --- | --- |
| F-002 | medium | someday | Android 16+ local network protection will require a runtime permission once the app targets SDK 37; discovery and every TV connection would fail without it. | `gradle/libs.versions.toml` targetSdk = 36; docs/research/tv-remote-protocols.md §1 | Before raising targetSdk to 37 | Check the permission's final name, request it in context on the discovery screen, and explain it as in OnboardingFlow. |
| F-003 | medium | next | The app module has no UI test setup, so Compose widgets (D-pad, rockers, number pad, media bar) are only checked by compiling; WI-006's "each widget sends the right action" criterion cannot be shown. | `app/build.gradle.kts` has no test dependencies; CI builds but cannot run Android here | Before Phase 3 edit mode changes the widgets | Add Robolectric with `compose-ui-test-junit4` to `app` unit tests (runs in CI without an emulator), then test each widget against the fake adapter. |

## Closed

| Id | What | Disposition | Reason |
| --- | --- | --- | --- |
| F-001 | The Samsung adapter accepted any TLS certificate from the TV's host. | fixed | WI-004: `TvTls` now trusts the TV's certificate on first use, stores its SHA-256 as an encrypted secret, and refuses a different one with `TvError.CertificateChanged`; re-trusting needs the user to choose "Pair again". |

---

## Recording one

Use `/record-finding`, or add a row directly. Required:

- **Sev** — `high` (wrong output, data loss, security) · `medium` (wrong behaviour, contained) ·
  `low` (cost, clarity, tidiness)
- **Pri** — `now` · `next` · `someday`. Severity is about the problem; priority is about us.
- **Evidence** — a path, a command, a count. **A finding with no evidence is a hunch**, and the
  next reader cannot tell the difference. If you cannot produce evidence, say so in the row.
- **When to act** — the trigger, not a date. *"Before the next release"*, *"if this recurs"*.
- **How to fix** — enough that someone else could, or an explicit "unknown".

## Closing one

Every finding leaves the Open table by one of three dispositions, and **each carries a written
reason**:

| Disposition | Means | Reason must say |
| --- | --- | --- |
| **promoted** | It became a work item | Which item, and what scope it took |
| **fixed** | It was resolved directly | What changed, and where |
| **dismissed** | It is not a problem, or not one worth solving | *Why not* — this is the one people skip, and it is the one that stops the same observation being recorded again next month |

`node .ai/rungs.mjs check` refuses a closed finding with no reason.

## What this register does not do

- It does not prioritise. A `now` priority is a claim by whoever typed it.
- It does not prove anything is fixed. `fixed` means someone said so.
- It counts what was **recorded**, not what was noticed. A quiet register and an unobserved repo
  look identical from here.
