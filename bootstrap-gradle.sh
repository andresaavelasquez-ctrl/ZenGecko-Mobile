#!/usr/bin/env sh
set -eu
VERSION="8.13"
BASE="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
DIST="$BASE/.gradle-dist/gradle-$VERSION"
ZIP="$BASE/.gradle-dist/gradle-$VERSION-bin.zip"
mkdir -p "$BASE/.gradle-dist"
if [ ! -x "$DIST/bin/gradle" ]; then
  URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then curl -L "$URL" -o "$ZIP";
  elif command -v wget >/dev/null 2>&1; then wget -O "$ZIP" "$URL";
  else echo "Install curl or wget." >&2; exit 1; fi
  unzip -q -o "$ZIP" -d "$BASE/.gradle-dist"
fi
exec "$DIST/bin/gradle" "$@"
