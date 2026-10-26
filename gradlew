#!/usr/bin/env sh
# Thin bootstrap for local machines; GitHub Actions uses setup-gradle directly.
set -eu
cd "$(dirname "$0")"
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
V=8.13
TARGET=".gradle-dist/gradle-$V/bin/gradle"
if [ ! -x "$TARGET" ]; then
  mkdir -p .gradle-dist
  echo "Downloading official Gradle $V distribution..."
  curl --fail --location --retry 3 --output ".gradle-dist/gradle.zip" "https://services.gradle.org/distributions/gradle-$V-bin.zip"
  unzip -q ".gradle-dist/gradle.zip" -d .gradle-dist
  rm ".gradle-dist/gradle.zip"
fi
exec "$TARGET" "$@"
