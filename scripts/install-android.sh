#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

./scripts/build-android.sh

if ! command -v adb >/dev/null 2>&1; then
  echo "adb не найден. APK лежит здесь: $ROOT/app/build/outputs/apk/debug/app-debug.apk" >&2
  exit 2
fi

adb start-server >/dev/null
mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [[ ${#devices[@]} -eq 0 ]]; then
  cat >&2 <<'EOF'
Телефон не виден через adb.
1. Подключите кабель и разблокируйте экран.
2. Настройки → О телефоне → 7 раз нажать «Номер сборки».
3. Для разработчика → «Отладка по USB» включить.
4. Подтвердите запрос «Разрешить отладку по USB» на телефоне.
Затем повторите: ./scripts/install-android.sh
EOF
  exit 2
fi

adb -s "${devices[0]}" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "${devices[0]}" shell monkey -p com.irontrack.app -c android.intent.category.LAUNCHER 1 >/dev/null
echo "Установлено и запущено на ${devices[0]}"
