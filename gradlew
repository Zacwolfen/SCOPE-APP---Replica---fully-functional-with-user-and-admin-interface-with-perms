#!/bin/sh
set -e
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.9
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
CACHE="$HOME/.scope-local-gradle/$GRADLE_VERSION"
if [ ! -x "$CACHE/bin/gradle" ]; then
  mkdir -p "$HOME/.scope-local-gradle"
  TMP="$HOME/.scope-local-gradle/gradle-$GRADLE_VERSION.zip"
  echo "Gradle $GRADLE_VERSION not found. Downloading it once..."
  curl -L --fail --output "$TMP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  rm -rf "$CACHE"
  mkdir -p "$CACHE"
  unzip -q "$TMP" -d "$HOME/.scope-local-gradle"
  mv "$HOME/.scope-local-gradle/gradle-$GRADLE_VERSION" "$CACHE"
fi
exec "$CACHE/bin/gradle" "$@"
