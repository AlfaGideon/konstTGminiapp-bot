#!/usr/bin/env bash
set -euo pipefail

APP_DIR="android/app/src/main"
ACTIVITY_DIR="$APP_DIR/java/ru/botcraft/studio"
mkdir -p "$ACTIVITY_DIR"
cp native-android/MainActivity.java "$ACTIVITY_DIR/MainActivity.java"
cp native-android/WorldBotService.java "$ACTIVITY_DIR/WorldBotService.java"

python3 - <<'PY'
from pathlib import Path
manifest = Path('android/app/src/main/AndroidManifest.xml')
text = manifest.read_text()
permissions = [
    '    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />',
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />',
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />',
    '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />',
]
for permission in permissions:
    name = permission.split('"')[1]
    if name not in text:
        text = text.replace('</manifest>', permission + '\n</manifest>')
service = '''        <service
            android:name="ru.botcraft.studio.WorldBotService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />
'''
if 'ru.botcraft.studio.WorldBotService' not in text:
    text = text.replace('    </application>', service + '    </application>')
# BridgeActivity normally applies a splash-screen post theme. Our Activity is native and
# must start directly in the regular no-action-bar app theme instead.
text = text.replace('android:theme="@style/AppTheme.NoActionBarLaunch"', 'android:theme="@style/AppTheme.NoActionBar"')
manifest.write_text(text)
PY

# The native Activity never starts Capacitor or a WebView. Do not package the web UI in the APK.
rm -rf "$APP_DIR/assets/public"
