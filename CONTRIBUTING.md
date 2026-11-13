# Contributing

Thanks for helping out. Issues and pull requests are welcome.

## Before you start

- Read the decisions in [`docs/decisions/`](docs/decisions/README.md), especially the
  [adapter contract](docs/decisions/ADR-0002-brand-adapters.md) and the
  [dependency rule](docs/decisions/ADR-0003-gpl-no-proprietary-dependencies.md).
- For anything larger than a small fix, open an issue first so we can agree on the approach.

## Making a change

1. Set up your machine as described in the [README](README.md).
2. Create a branch off `main`: `feature/WI-###-slug` for tracked work, or `fix/`, `chore/` or
   `docs/` otherwise. How work is tracked is explained in the [backlog guide](docs/backlog/README.md).
3. Keep brand-specific code inside its `adapters/<brand>` module. The app talks only to
   `TvAdapter` and `TvSession`.
4. Run the checks:

   ```bash
   ./gradlew spotlessCheck test lint
   node .ai/rungs.mjs check
   ```

5. Use conventional commit prefixes (`feat:`, `fix:`, `docs:`, `chore:`) and add a line to the
   [changelog](CHANGELOG.md) when users would notice the change.

## Adding a TV brand

1. Create `adapters/<brand>` as a plain Kotlin/JVM module depending on `:core:remote` only.
2. Implement `TvAdapter` and `TvSession`, declaring only the capabilities the TV really has.
3. Test it against a fake TV (see `SamsungSessionTest` for one built on MockWebServer).
4. Register it in `AppContainer` and add it to `settings.gradle.kts`.

## License of contributions

By contributing you agree that your contribution is licensed under the
[GPL-3.0](LICENSE), the project's license.
