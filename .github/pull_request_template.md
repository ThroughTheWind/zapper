## What changed and why

## How it was tested

Which TVs (brand, model, year) and which phone or emulator did you try it on?

## Checklist

- [ ] `./gradlew spotlessCheck test lint` and `node .ai/rungs.mjs check` pass
- [ ] Docs and the changelog are updated if users would notice
- [ ] No new dependency is proprietary or GPL-3.0-incompatible, and nothing talks to the internet (ADR-0003)
- [ ] Brand-specific code stays inside its `adapters/<brand>` module (ADR-0002)
