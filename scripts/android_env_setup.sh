#!/bin/bash
set -euo pipefail

echo "=== Android Environment Setup ==="

# Export Android SDK paths
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

# Export Java 17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"

echo ""
echo "Environment Variables:"
echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
echo ""

echo "Java Version:"
java -version
echo ""

echo "ADB Location:"
which adb || echo "  (adb not found - will be installed)"
echo ""

# Ensure cmdline-tools exists
if [ ! -d "$ANDROID_SDK_ROOT/cmdline-tools/latest" ]; then
    echo "ERROR: cmdline-tools not found at $ANDROID_SDK_ROOT/cmdline-tools/latest"
    echo "Please install Android Studio or download cmdline-tools manually"
    echo "Visit: https://developer.android.com/studio#command-tools"
    exit 1
fi

echo "=== Installing SDK Components ==="
echo "Accepting licenses..."
yes | sdkmanager --licenses || true

echo ""
echo "Installing platform-tools, platforms, emulator, and system images..."
sdkmanager --install \
    "platform-tools" \
    "platforms;android-34" \
    "emulator" \
    "system-images;android-34;google_apis;arm64-v8a"

echo ""
echo "=== Setup Complete ==="
echo ""
echo "Installed ADB:"
which adb
adb version
echo ""
