#!/bin/bash
# ---------------------------------------------------------------------------
# Coin Pulse — install & launch on connected Android device
# Usage: ./run_on_device.sh
# ---------------------------------------------------------------------------
set -e

JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ANDROID_HOME="$HOME/Library/Android/sdk"
ADB="$ANDROID_HOME/platform-tools/adb"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.coinpulse.coinpulsegame"
ACTIVITY="$PACKAGE/.MainActivity"

export JAVA_HOME
export ANDROID_HOME

echo "=========================================="
echo "  Coin Pulse — Run on Device"
echo "=========================================="

# --- Check device ---
echo ""
echo "Checking connected devices..."
DEVICES=$("$ADB" devices | grep -v "List of devices" | grep -c "device$" || true)

if [ "$DEVICES" -eq 0 ]; then
    echo ""
    echo "  ERROR: No device found!"
    echo ""
    echo "  Make sure:"
    echo "  1. Phone is connected via USB cable"
    echo "  2. USB Debugging is ON  (Settings → Developer Options → USB Debugging)"
    echo "  3. You tapped 'Allow' on the phone when prompted"
    echo "  4. USB mode is set to File Transfer (not Charge Only)"
    echo ""
    echo "  Then run this script again."
    exit 1
fi

echo "  Found $DEVICES device(s) — OK"
"$ADB" devices -l

# --- Build ---
echo ""
echo "Building debug APK..."
cd "$PROJECT_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug --quiet

echo "  Build OK — $(du -sh "$APK" | cut -f1) APK"

# --- Install ---
echo ""
echo "Installing on device..."
"$ADB" install -r "$APK"
echo "  Installed OK"

# --- Launch ---
echo ""
echo "Launching..."
"$ADB" shell am start -n "$ACTIVITY"

echo ""
echo "=========================================="
echo "  Coin Pulse is running on your device!"
echo "=========================================="
