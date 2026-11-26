**Authoritative for:** which languages Zapper ships, where its copy lives, and how to change or add a translation.
**Not authoritative for:** the voice the copy is written in (that is [`design/README.md`](design/README.md)), or what each screen says (that is the screen's component spec).

# Translations

Zapper ships in **English, French, Spanish, German and Polish**. English is the source: every
string is written in English first, then translated.

## How the app picks a language

- It follows the phone's language. A phone set to a language Zapper does not ship gets English.
- On Android 13 and later, Settings › Language in Zapper opens the system's per-app language
  page, where any of the five can be chosen for Zapper alone. The list comes from
  `generateLocaleConfig` in `app/build.gradle.kts`, which reads the `values-<lang>` folders; the
  source language is declared in `app/src/main/res/resources.properties`.
- `localeFilters` in the same file keeps only these five languages in the APK, so library text
  (Material's own labels, for instance) never shows in a sixth language beside Zapper's English.

## Where the copy lives

| What | English | Translations |
| --- | --- | --- |
| The app's screens | `app/src/main/res/values/strings.xml` | `app/src/main/res/values-{fr,es,de,pl}/strings.xml` |
| Design system components | `core/designsystem/src/main/res/values/strings.xml` (keys start `ds_`) | `core/designsystem/src/main/res/values-{fr,es,de,pl}/strings.xml` |
| A brand's own help text (`TvAdapter.powerOnHelp`) | the adapter, as `LocalizedText.english` | the adapter, in `LocalizedText.translations` |

Adapters are plain JVM modules without Android resources, which is why their few sentences carry
their own translations. `SamsungPowerTest` fails if one of the four is missing.

Some text is not the app's to translate: TV names the user typed, app names the TV reports, and
brand names. Pages of the default layout are stored with English names; while the user has not
renamed one, it shows in the app's language (`RemotePage.displayName()`).

## Rules for code

- No copy in Kotlin. `Text("…")`, `contentDescription = "…"`, snackbar messages and the like read a
  string resource. In a composable use `stringResource`; in a ViewModel, emit a `UiText` and let the
  screen resolve it, so it follows the language the screen is shown in.
- Counts use `<plurals>` and `pluralStringResource`, never `if (n == 1)`. Polish needs `one`, `few`
  and `many`; French and Spanish need `one` and `many`.
- Placeholders are numbered (`%1$s`, `%2$d`) so a translation can reorder them.
- A user-typed name inside a sentence is inserted where no grammatical case depends on it; Polish
  puts it after a colon ("Łączenie: %1$s…").

## What keeps this true

`node scripts/check-translations.mjs` (the `translations` gate in `node .ai/rungs.mjs check`):

- refuses user-facing text typed into Compose code in `app/` and `core/designsystem/`. A line that
  must keep a literal says why with `// l10n-ok: <reason>`;
- refuses a language that lacks an English key, has a key English does not, drops or changes a
  placeholder, or lacks a plural quantity it needs.

Android lint (`MissingTranslation`, `StringFormatMatches`) checks the same in CI's `./gradlew lint`.

## Changing English copy

Change `values/strings.xml`, then the same key in each `values-<lang>/strings.xml`. If you cannot
translate, change the key's name too (so every language fails the gate until someone translates it),
or open an issue that lists the key.

## Adding a language

1. Copy `values/strings.xml` to `values-<lang>/strings.xml` in `app` and `core/designsystem`, and
   translate every string. Drop the `translatable="false"` entries.
2. Add the plural quantities the language needs (see the CLDR plural rules) and add the language
   to `QUANTITIES` and `LANGUAGES` in `scripts/check-translations.mjs`.
3. Add it to `localeFilters` in `app/build.gradle.kts`.
4. Translate every adapter's `powerOnHelp`, and add the language to the adapter tests.
5. On a phone set to that language, walk through discovery, pairing, the remote, edit mode and
   every Settings page, looking for text that is cut off.

## Translation notes

- Address the user as *vous* in French, *tú* in Spanish, *du* in German, and with the informal
  imperative in Polish.
- "TV" stays "TV" in French and Spanish (feminine: *la TV*); German says *Fernseher*, Polish
  *telewizor*.
- Menu paths on the TV ("Settings › General › …") are Samsung's own menus. Their translations were
  made from the English names and have not been checked against a TV set to each language yet; a TV
  whose menus read differently should be reported as a finding.
