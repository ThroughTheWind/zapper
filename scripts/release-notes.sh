#!/usr/bin/env bash
# Writes the GitHub Release body for one version to stdout:
#   docs/releases/<version>.md, then the CHANGELOG.md section for that version, then how to verify
#   the download. Fails loudly when either source is missing, so a release never ships without notes.
#
# Usage: scripts/release-notes.sh <version> <apk-sha256> <signing-cert-sha256>
set -euo pipefail

version="${1:?usage: release-notes.sh <version> <apk-sha256> <signing-cert-sha256>}"
apk_sha256="${2:?missing the APK SHA-256}"
cert_sha256="${3:?missing the signing certificate SHA-256}"

root="$(cd "$(dirname "$0")/.." && pwd)"
notes="$root/docs/releases/$version.md"
changelog="$root/CHANGELOG.md"

if [[ ! -f "$notes" ]]; then
  echo "error: $notes does not exist; write the release notes first (docs/releasing.md)" >&2
  exit 1
fi

# The changelog section runs from "## [<version>]" to the next "## [" heading, or to the link
# definitions ("[0.1.0]: https://...") at the end of the file.
section="$(awk -v heading="## [$version]" '
  index($0, heading) == 1 { inside = 1; next }
  inside && (/^## \[/ || /^\[[^]]+\]: /) { exit }
  inside { print }
' "$changelog")"

if [[ -z "${section//[[:space:]]/}" ]]; then
  echo "error: CHANGELOG.md has no \"## [$version]\" section with content; cut it from [Unreleased] first" >&2
  exit 1
fi

# Drop the notes file's "# " title line: the GitHub Release has its own title.
sed '1{/^# /d}' "$notes"
printf '\n## Changes\n%s\n' "$section"
cat <<EOF

## Verify your download

- APK SHA-256: \`$apk_sha256\`
- Signing certificate SHA-256: \`$cert_sha256\`

Every Zapper release is signed with the same certificate. Android refuses an update signed with a
different one, and apps such as Obtainium or AppVerifier can check the fingerprint above.
EOF
