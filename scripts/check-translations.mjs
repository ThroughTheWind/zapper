// Keeps the app translatable and translated (docs/translations.md). Two checks:
//
//   1. No user-facing text typed into Compose code in app/ or core/designsystem/: Text("…"),
//      contentDescription = "…", showSnackbar("…") and the other places copy goes must read a
//      string resource. A literal with no letters ("$span", "·") is not copy and passes.
//   2. Every language in values-<lang>/strings.xml has exactly the English keys, the same
//      placeholders, and the plural quantities that language needs.
//
// Android lint also reports missing translations, but only in a build with the Android SDK; this
// runs anywhere with Node, in `node .ai/rungs.mjs check`.
//
//   node scripts/check-translations.mjs              check the repository
//   node scripts/check-translations.mjs --self-test  prove both checks fail on bad input and pass on good
//
// A line that must keep a literal says why with `// l10n-ok: <reason>`. A marker with no reason is ignored.
import { readFileSync, readdirSync, existsSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const MODULES = ["app/src/main", "core/designsystem/src/main"];
export const LANGUAGES = ["fr", "es", "de", "pl"];
// CLDR plural categories Android resolves for each language.
const QUANTITIES = { en: ["one", "other"], de: ["one", "other"], fr: ["one", "many", "other"], es: ["one", "many", "other"], pl: ["one", "few", "many", "other"] };

// Where copy goes in this codebase. Each pattern captures the literal that follows it.
const COPY_SITES = [
  /\bText\(\s*"((?:[^"\\]|\\.)*)"/g,
  /\bcontentDescription\s*=\s*"((?:[^"\\]|\\.)*)"/g,
  /\bonClickLabel\s*=\s*"((?:[^"\\]|\\.)*)"/g,
  /\b(?:title|body|confirm|subtitle|status|prompt|placeholder)\s*=\s*"((?:[^"\\]|\\.)*)"/g,
  /\bshowSnackbar\(\s*"((?:[^"\\]|\\.)*)"/g,
  /\bCustomAccessibilityAction\(\s*"((?:[^"\\]|\\.)*)"/g,
  /\b(?:Heading|SectionHeader|InfoItem|SwitchItem|PreviewKey|InfoLine)\(\s*"((?:[^"\\]|\\.)*)"/g,
  /\bNavItem\([^,()]*,\s*"((?:[^"\\]|\\.)*)"/g,
  /\b(?:tryEmit|emit)\(\s*"((?:[^"\\]|\\.)*)"/g,
];

/** Lines of Kotlin [source] that put a literal with letters where copy goes. */
export function hardcodedText(source) {
  const found = [];
  source.split("\n").forEach((line, index) => {
    const marker = line.match(/\/\/\s*l10n-ok:\s*(\S.*)?$/);
    if (marker && marker[1]) return;
    const code = line.replace(/\/\/.*$/, "");
    if (/^\s*(\*|\/\*)/.test(code)) return;
    for (const pattern of COPY_SITES) {
      for (const match of code.matchAll(pattern)) {
        const literal = match[1].replace(/\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_.]*/g, "");
        if (/\p{L}/u.test(literal)) found.push({ line: index + 1, text: match[0] });
      }
    }
  });
  return found;
}

/** Keys, placeholders and plural quantities of a strings.xml. */
export function parseStrings(xml) {
  const body = xml.replace(/<!--[\s\S]*?-->/g, "");
  const strings = new Map();
  const plurals = new Map();
  for (const m of body.matchAll(/<string\s+name="([^"]+)"([^>]*)>([\s\S]*?)<\/string>/g)) {
    if (/translatable="false"/.test(m[2])) continue;
    strings.set(m[1], placeholders(m[3]));
  }
  for (const m of body.matchAll(/<plurals\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/plurals>/g)) {
    const items = [...m[2].matchAll(/<item\s+quantity="([a-z]+)">([\s\S]*?)<\/item>/g)];
    plurals.set(m[1], { quantities: items.map((i) => i[1]), placeholders: items.map((i) => placeholders(i[2])) });
  }
  return { strings, plurals };
}

function placeholders(text) {
  return [...text.matchAll(/%(\d+\$)?[sd]/g)].map((p) => p[0]).sort().join(" ");
}

/** What is wrong with [translated] (language [lang]) against [english]. */
export function translationProblems(english, translated, lang) {
  const problems = [];
  for (const [key, ph] of english.strings) {
    if (!translated.strings.has(key)) problems.push(`missing string ${key}`);
    else if (translated.strings.get(key) !== ph) problems.push(`${key}: placeholders "${translated.strings.get(key)}", English has "${ph}"`);
  }
  for (const key of translated.strings.keys()) if (!english.strings.has(key)) problems.push(`string ${key} is not in English`);
  for (const [key, en] of english.plurals) {
    const t = translated.plurals.get(key);
    if (!t) {
      problems.push(`missing plurals ${key}`);
      continue;
    }
    const want = QUANTITIES[lang];
    if (t.quantities.join(",") !== want.join(",")) problems.push(`${key}: quantities ${t.quantities.join(",")}, ${lang} needs ${want.join(",")}`);
    const enPh = en.placeholders[0];
    t.placeholders.forEach((ph, i) => {
      if (ph !== enPh) problems.push(`${key} (${t.quantities[i]}): placeholders "${ph}", English has "${enPh}"`);
    });
  }
  for (const key of translated.plurals.keys()) if (!english.plurals.has(key)) problems.push(`plurals ${key} is not in English`);
  return problems;
}

function kotlinFiles(dir) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? kotlinFiles(path) : name.endsWith(".kt") ? [path] : [];
  });
}

function checkRepository() {
  const errors = [];
  for (const module of MODULES) {
    for (const file of kotlinFiles(join(root, module, "kotlin"))) {
      for (const hit of hardcodedText(readFileSync(file, "utf8"))) {
        errors.push(`${relative(root, file)}:${hit.line}: text typed into code, use a string resource: ${hit.text}`);
      }
    }
    const res = join(root, module, "res");
    const english = parseStrings(readFileSync(join(res, "values", "strings.xml"), "utf8"));
    for (const lang of LANGUAGES) {
      const path = join(res, `values-${lang}`, "strings.xml");
      if (!existsSync(path)) {
        errors.push(`${relative(root, path)} is missing`);
        continue;
      }
      for (const p of translationProblems(english, parseStrings(readFileSync(path, "utf8")), lang)) errors.push(`${relative(root, path)}: ${p}`);
    }
  }
  if (errors.length) {
    console.error(errors.join("\n"));
    console.error(`\n${errors.length} problem(s). See docs/translations.md.`);
    process.exit(1);
  }
  console.log(`No hardcoded UI text in ${MODULES.join(", ")}; ${LANGUAGES.join(", ")} translate every English string.`);
}

function selfTest() {
  const assert = (cond, what) => {
    if (!cond) {
      console.error(`self-test failed: ${what}`);
      process.exit(1);
    }
  };
  // Fails on copy typed into code.
  assert(hardcodedText('Text("Add a TV")').length === 1, "Text with a literal is refused");
  assert(hardcodedText('Icon(x, contentDescription = "Back")').length === 1, "contentDescription literal is refused");
  assert(hardcodedText('snackbar.showSnackbar("Not connected")').length === 1, "snackbar literal is refused");
  assert(hardcodedText('Text("Add") // l10n-ok:').length === 1, "a marker without a reason is ignored");
  // Passes on resources and on literals that are not copy.
  assert(hardcodedText("Text(stringResource(R.string.add_a_tv))").length === 0, "a resource passes");
  assert(hardcodedText('label = { Text("$span") },').length === 0, "a template with no letters passes");
  assert(hardcodedText('Text("·")').length === 0, "punctuation passes");
  assert(hardcodedText('Text("Zapper") // l10n-ok: brand name').length === 0, "a marker with a reason passes");
  // Translations: fail on a missing key, wrong placeholders, or missing Polish quantities; pass when complete.
  const en = parseStrings('<resources><string name="a">Hi %1$s</string><plurals name="p"><item quantity="one">%d x</item><item quantity="other">%d xs</item></plurals></resources>');
  const good = parseStrings('<resources><string name="a">Cześć %1$s</string><plurals name="p"><item quantity="one">%d a</item><item quantity="few">%d b</item><item quantity="many">%d c</item><item quantity="other">%d d</item></plurals></resources>');
  const missing = parseStrings('<resources><plurals name="p"><item quantity="one">%d a</item><item quantity="few">%d b</item><item quantity="many">%d c</item><item quantity="other">%d d</item></plurals></resources>');
  const badPh = parseStrings('<resources><string name="a">Cześć</string><plurals name="p"><item quantity="one">%d a</item><item quantity="few">%d b</item><item quantity="many">%d c</item><item quantity="other">%d d</item></plurals></resources>');
  const fewMissing = parseStrings('<resources><string name="a">Cześć %1$s</string><plurals name="p"><item quantity="one">%d a</item><item quantity="other">%d d</item></plurals></resources>');
  assert(translationProblems(en, good, "pl").length === 0, "a complete Polish translation passes");
  assert(translationProblems(en, missing, "pl").length === 1, "a missing string is refused");
  assert(translationProblems(en, badPh, "pl").length === 1, "a dropped placeholder is refused");
  assert(translationProblems(en, fewMissing, "pl").length === 1, "Polish without few and many is refused");
  console.log("self-test passed: both checks refuse bad input and accept good input.");
}

if (process.argv.includes("--self-test")) selfTest();
else checkRepository();
