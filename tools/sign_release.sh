#!/usr/bin/env bash
# Signs the release APK with YOUR keystore. The key never enters this repo or any chat.
# One-time key creation (keep the .jks file and both passwords safe — losing them means you can't update the app):
#   keytool -genkeypair -v -keystore ~/banglaai-release.jks -alias banglaai -keyalg RSA -keysize 4096 -validity 9125
# Usage: tools/sign_release.sh ~/banglaai-release.jks banglaai   (passwords are asked interactively)
set -euo pipefail
KS="${1:?keystore path}"; ALIAS="${2:?key alias}"
SDK="${ANDROID_HOME:?set ANDROID_HOME}"; BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
cd "$(dirname "$0")/.."
./gradlew assembleRelease
IN=app/build/outputs/apk/release/app-release-unsigned.apk; OUT=app/build/outputs/apk/release/ChoudhurySahebsPS-release.apk
"$BT/zipalign" -f -p 4 "$IN" "$OUT.aligned"
"$BT/apksigner" sign --ks "$KS" --ks-key-alias "$ALIAS" --out "$OUT" "$OUT.aligned"
rm -f "$OUT.aligned" "$OUT.idsig"
"$BT/apksigner" verify --verbose --print-certs "$OUT"
# Hard checks: no INTERNET permission, not debuggable
"$BT/aapt2" dump permissions "$OUT" | tee /dev/stderr | grep -q "android.permission.INTERNET" && { echo "FAIL: INTERNET present"; exit 1; } || true
echo "OK: $OUT"
