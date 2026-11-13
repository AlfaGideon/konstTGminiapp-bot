#!/usr/bin/env bash
set -euo pipefail

APP_DIR="android/app/src/main"
ACTIVITY_DIR="$APP_DIR/java/ru/botcraft/studio"
mkdir -p "$ACTIVITY_DIR"
cp native-android/MainActivity.java "$ACTIVITY_DIR/MainActivity.java"

python3 - <<'PY'
from pathlib import Path
manifest = Path('android/app/src/main/AndroidManifest.xml')
text = manifest.read_text()
permission = '    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />'
if 'android.permission.REQUEST_INSTALL_PACKAGES' not in text:
    text = text.replace('</manifest>', permission + '\n</manifest>')
manifest.write_text(text)
PY

# The native Activity never starts Capacitor or a WebView. Do not package the web UI in the APK.
rm -rf "$APP_DIR/assets/public"
