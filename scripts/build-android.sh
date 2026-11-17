#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  for candidate in "$HOME/Android/Sdk" "$HOME/android-sdk" /opt/android-sdk; do
    if [[ -d "$candidate/platforms" ]]; then
      export ANDROID_HOME="$candidate"
      export ANDROID_SDK_ROOT="$candidate"
      break
    fi
  done
fi

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  echo "Android SDK не найден. Укажите ANDROID_HOME, например:" >&2
  echo "  ANDROID_HOME=$HOME/Android/Sdk $0" >&2
  exit 2
fi

if [[ ! -x ./gradlew ]]; then
  if command -v gradle >/dev/null 2>&1; then
    gradle wrapper --gradle-version 8.12.1 --distribution-type bin
  else
    echo "Нет ./gradlew и нет gradle. Установите Gradle 8.12.1 или восстановите папку gradle/wrapper." >&2
    exit 2
  fi
fi

./gradlew :app:assembleDebug "$@"
echo "APK: $ROOT/app/build/outputs/apk/debug/app-debug.apk"
