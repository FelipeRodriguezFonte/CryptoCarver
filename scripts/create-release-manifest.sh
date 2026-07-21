#!/usr/bin/env bash
set -euo pipefail

# Creates checksums and a small traceability manifest for the artifacts passed on
# the command line. Files are copied to a versioned release directory so the
# checksum file refers only to the deliverables, never to a mutable build tree.

if [ "$#" -lt 3 ]; then
  echo "Usage: $0 <version> <channel> <artifact> [artifact ...]" >&2
  exit 2
fi

VERSION="$1"
CHANNEL="$2"
shift 2
RELEASE_OUTPUT_ROOT="${RELEASE_OUTPUT_ROOT:-dist}"
RELEASE_DIR="${RELEASE_OUTPUT_ROOT}/release-${VERSION}"
mkdir -p "$RELEASE_DIR"

sha256() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    sha256sum "$1" | awk '{print $1}'
  fi
}

for artifact in "$@"; do
  if [ ! -f "$artifact" ]; then
    echo "Artifact not found: $artifact" >&2
    exit 3
  fi
  name="$(basename "$artifact")"
  cp "$artifact" "$RELEASE_DIR/$name"
done

CHECKSUMS="$RELEASE_DIR/SHA256SUMS"
: > "$CHECKSUMS"
while IFS= read -r artifact; do
  name="$(basename "$artifact")"
  printf '%s  %s\n' "$(sha256 "$artifact")" "$name" >> "$CHECKSUMS"
done < <(find "$RELEASE_DIR" -maxdepth 1 -type f ! -name SHA256SUMS ! -name RELEASE_MANIFEST.md -print | sort)

GIT_COMMIT="${RELEASE_GIT_COMMIT:-unknown}"
if [ "$GIT_COMMIT" = "unknown" ] && git rev-parse --verify HEAD >/dev/null 2>&1; then
  GIT_COMMIT="$(git rev-parse HEAD)"
fi

{
  printf '# CryptoCarver release manifest\n\n'
  printf -- '- Version: `%s`\n' "$VERSION"
  printf -- '- Channel: `%s`\n' "$CHANNEL"
  printf -- '- Commit: `%s`\n' "$GIT_COMMIT"
  printf -- '- Java: `%s`\n' "$(java -version 2>&1 | head -n 1)"
  printf -- '- SBOM: `%s`\n\n' "$(find "$RELEASE_DIR" -maxdepth 1 -type f -iname '*sbom*' -exec basename {} \; | head -n 1 || true)"
  printf '## Artifacts\n\n'
  while IFS= read -r line; do
    printf -- '- `%s`\n' "$line"
  done < "$CHECKSUMS"
} > "$RELEASE_DIR/RELEASE_MANIFEST.md"

echo "Release manifest created in $RELEASE_DIR"
