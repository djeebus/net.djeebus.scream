#!/usr/bin/env bash
# Builds the signed release APK for the version semantic-release just decided on.
set -euo pipefail
version="$1"

yes | mise x -- sdkmanager --licenses > /dev/null || true
mise x -- sdkmanager "platforms;android-36" "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"

test -n "${KEYSTORE_BASE64:-}" || { echo "SCREAM_KEYSTORE_BASE64 secret is not set" >&2; exit 1; }
echo "$KEYSTORE_BASE64" | base64 -d > "$RUNNER_TEMP/scream-release.jks"

SCREAM_KEYSTORE_FILE="$RUNNER_TEMP/scream-release.jks" \
  mise x -- ./gradlew :app:assembleRelease -PappVersion="$version"
