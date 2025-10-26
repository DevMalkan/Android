# PathSense Android Testing Guide (Lightweight - No Android Studio)

This guide shows how to build and run the PathSense Android app **without installing Android Studio**, using only command-line tools.

---

## Prerequisites

### 1. Install Java Development Kit (JDK)

Check if Java is already installed:

```bash
java -version
```

If not installed, install via Homebrew:

```bash
brew install openjdk@17
```

Add to PATH:

```bash
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

Verify installation:

```bash
java -version
# Expected: openjdk version "17.x.x"
```

### 2. Download Android Command Line Tools

Download the SDK command-line tools (no Android Studio needed):

```bash
# Create SDK directory
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools

# Download command-line tools for macOS
curl -O https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip

# Extract
unzip commandlinetools-mac-11076708_latest.zip

# Rename for proper structure
mv cmdline-tools latest

# Clean up
rm commandlinetools-mac-11076708_latest.zip
```

### 3. Set Up Environment Variables

Add Android SDK to your PATH:

```bash
# Add to ~/.zshrc
cat << 'EOF' >> ~/.zshrc

# Android SDK
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/build-tools/33.0.0

EOF

# Reload shell configuration
source ~/.zshrc
```

### 4. Install Required SDK Components

Accept licenses:

```bash
yes | sdkmanager --licenses
```

Install necessary packages:

```bash
sdkmanager "platform-tools" "platforms;android-33" "build-tools;33.0.0"
```

This installs:
- **platform-tools**: `adb`, `fastboot` commands
- **platforms;android-33**: Android 13 SDK
- **build-tools;33.0.0**: Build utilities

**Note:** This downloads ~500 MB. Wait for completion.

Verify installation:

```bash
adb --version
# Expected: Android Debug Bridge version X.X.X
```

### 5. Configure Android Device

On your Android phone:
1. Go to `Settings` → `About Phone`
2. Tap `Build Number` 7 times to enable Developer Options
3. Go to `Settings` → `System` → `Developer Options`
4. Enable `USB Debugging`
5. Enable `Install via USB` (if available)

### 6. Connect Device to MacBook

1. Connect your Android phone to MacBook using USB-C to USB-C cable
2. On your phone, you'll see a prompt "Allow USB debugging?"
3. Check "Always allow from this computer"
4. Tap "OK"

---

## Step-by-Step Testing Instructions

### Step 1: Verify Device Connection

Check if your device is connected:

```bash
adb devices
```

**Expected output:**
```
List of devices attached
ABC123XYZ    device
```

If you see "unauthorized", check your phone for the USB debugging prompt.

If `adb` is not found, verify PATH:

```bash
echo $ANDROID_HOME
ls $ANDROID_HOME/platform-tools/adb
```

### Step 2: Navigate to Project

```bash
cd /Users/devmalkan/Desktop/PathSense/Android
```

### Step 3: Build the App Using Gradle Wrapper

The project includes a Gradle wrapper (`gradlew`), so you don't need to install Gradle separately:

```bash
# Make gradlew executable (if not already)
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug
```

**First build may take 5-10 minutes** as it downloads dependencies.

**Expected output:**
```
BUILD SUCCESSFUL in Xs
```

The APK will be created at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Step 4: Install on Device

Install directly using the Gradle wrapper:

```bash
./gradlew installDebug
```

**Or** install manually using `adb`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The `-r` flag reinstalls the app if it already exists.

**Expected output:**
```
Success
```

### Step 5: Launch the App

**Option A: Launch from Phone**
- Open the app drawer on your phone
- Look for "PathSense" or your app name
- Tap to open

**Option B: Launch via ADB**

```bash
adb shell am start -n com.guide.app/.MainActivity
```

**Note:** Replace `MainActivity` with your actual launcher activity name if different.

### Step 6: Grant Camera Permissions

When the app launches for the first time:
1. You'll see a camera permission request
2. Tap "Allow" to grant camera access
3. The app needs camera access for real-time obstacle detection

---

## Testing the App

### Verify TFLite Model Loading

Check logs to ensure the model loaded successfully:

```bash
adb logcat | grep -i "TfliteInferenceEngine"
```

**Expected output:**
```
I/TfliteInferenceEngine: TFLite model loaded successfully
```

### Monitor Frame Processing

Watch for frame processing logs:

```bash
adb logcat | grep -i "CameraAnalyzer"
```

### Check Detection Output

Monitor detection and navigation decisions:

```bash
adb logcat | grep -E "TfliteInferenceEngine|Planner|CueManager"
```

### Listen for Audio Output

Point your phone camera at objects (person, chair, car, etc.) and listen for:
- "person ahead, stop"
- "chair ahead, move left"
- "car ahead, move right"
- "bicycle ahead, caution"

---

## Minimal Installation Summary (No Android Studio, No Homebrew)

If you absolutely cannot use Homebrew, here's the manual approach:

### Option 1: Use Existing Java (if installed)

Check if Java is already on your system:

```bash
/usr/libexec/java_home -V
```

If Java 11+ exists, set `JAVA_HOME`:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

### Option 2: Download JDK Manually

1. Download JDK 17 from: https://www.oracle.com/java/technologies/downloads/#jdk17-mac
2. Install the `.dmg` file
3. Set `JAVA_HOME`:
   ```bash
   export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
   ```

### Option 3: Use Project's Gradle Wrapper (Recommended)

The Android project already includes `gradlew` (Gradle wrapper), which downloads Gradle automatically. You only need:

1. **Java JDK** (JDK 11 or 17)
2. **Android SDK command-line tools** (downloaded manually above)
3. **USB cable** + **ADB**

**No Homebrew, No Android Studio, No Gradle installation needed!**

---

## Debugging Commands

### Clear App Data and Restart

```bash
adb shell pm clear com.guide.app
adb shell am start -n com.guide.app/.MainActivity
```

### View All Logs (Real-time)

```bash
adb logcat -c  # Clear old logs
adb logcat
```

### Filter by Tag

```bash
adb logcat -s TfliteInferenceEngine:D Planner:D CueManager:D
```

### Save Logs to File

```bash
adb logcat > pathsense_logs.txt
```

### Check App is Installed

```bash
adb shell pm list packages | grep com.guide.app
```

**Expected output:**
```
package:com.guide.app
```

### Uninstall App

```bash
adb uninstall com.guide.app
```

### Force Stop App

```bash
adb shell am force-stop com.guide.app
```

---

## Common Issues & Solutions

### Issue 1: `./gradlew: command not found`

**Solution:** Make gradlew executable:

```bash
chmod +x gradlew
./gradlew --version
```

### Issue 2: `adb: command not found`

**Solution:** Add platform-tools to PATH:

```bash
export PATH=$PATH:$HOME/android-sdk/platform-tools
adb --version
```

Make permanent:

```bash
echo 'export PATH=$PATH:$HOME/android-sdk/platform-tools' >> ~/.zshrc
source ~/.zshrc
```

### Issue 3: Device shows "unauthorized"

**Solution:**
1. Disconnect and reconnect USB cable
2. Check phone for USB debugging prompt
3. Revoke USB debugging authorizations: `Settings` → `Developer Options` → `Revoke USB debugging authorizations`
4. Reconnect and approve again

### Issue 4: Gradle build fails - "SDK not found"

**Solution:** Set `ANDROID_HOME`:

```bash
export ANDROID_HOME=$HOME/android-sdk
./gradlew assembleDebug
```

Or create `local.properties` in project root:

```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

### Issue 5: "Failed to find Build Tools revision 33.0.0"

**Solution:** Install build tools:

```bash
sdkmanager "build-tools;33.0.0"
```

### Issue 6: "No Java runtime present"

**Solution:** Install JDK:

```bash
# Check if Java exists
java -version

# If not, download from Oracle or use Homebrew
brew install openjdk@17
```

### Issue 7: Gradle download is slow

**Solution:** The first build downloads ~200 MB of dependencies. Be patient or use a faster internet connection.

---

## Quick Reference: Essential Commands

```bash
# ============================================
# ONE-TIME SETUP
# ============================================

# 1. Install Java (if not installed)
brew install openjdk@17

# 2. Download Android SDK command-line tools
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
curl -O https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip
unzip commandlinetools-mac-11076708_latest.zip
mv cmdline-tools latest

# 3. Set environment variables
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

# 4. Install SDK components
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-33" "build-tools;33.0.0"

# ============================================
# EVERY TIME YOU BUILD & TEST
# ============================================

# 1. Connect phone via USB and enable USB debugging

# 2. Verify connection
adb devices

# 3. Navigate to project
cd /Users/devmalkan/Desktop/PathSense/Android

# 4. Build and install
./gradlew clean assembleDebug installDebug

# 5. Launch app
adb shell am start -n com.guide.app/.MainActivity

# 6. Monitor logs
adb logcat | grep -E "TfliteInferenceEngine|Planner|CueManager"

# 7. Clear data and restart (if needed)
adb shell pm clear com.guide.app
adb shell am start -n com.guide.app/.MainActivity
```

---

## Disk Space Requirements

| Component | Size |
|-----------|------|
| Java JDK 17 | ~200 MB |
| Android Command Line Tools | ~100 MB |
| SDK Platform (android-33) | ~50 MB |
| SDK Build Tools | ~100 MB |
| Platform Tools (adb) | ~10 MB |
| Gradle cache (first build) | ~200 MB |
| **Total** | **~660 MB** |

**Android Studio requires ~3 GB**, so this approach saves ~2.3 GB.

---

## Alternative: Pre-built APK Installation

If you have build issues, you can also:

1. Build the APK on another machine (with Android Studio)
2. Copy the APK to your MacBook
3. Install directly via `adb`:

```bash
adb install -r path/to/app-debug.apk
```

This requires only `adb` (part of platform-tools), which is ~10 MB.

---

## Expected Behavior Summary

| Feature | Expected Behavior |
|---------|------------------|
| **Model Loading** | "TFLite model loaded successfully" in logs |
| **Frame Processing** | 1 frame every 15 frames (~1 FPS) |
| **Audio Output** | "[object] ahead, stop/move left/right" |
| **Audio Rate** | Max 1 per second (rate limited) |
| **Detection Threshold** | Confidence > 0.45 |
| **Navigation Zones** | LEFT (0-33%), CENTER (33-67%), RIGHT (67-100%) |
| **STOP Threshold** | Object height > 40% of frame |
| **VEER Threshold** | Object height > 25% of frame |
| **Supported Objects** | 80 COCO classes (person, car, chair, etc.) |

---

## Testing Checklist for Hackathon Demo

- [ ] Device connected: `adb devices` shows "device"
- [ ] App installed: `adb shell pm list packages | grep com.guide.app`
- [ ] Camera permission granted
- [ ] Model loaded successfully (check logs)
- [ ] Point camera at person → hears "person ahead, stop"
- [ ] Point camera at chair on left → hears "chair ahead, move right"
- [ ] Point camera at car on right → hears "car ahead, move left"
- [ ] Audio not chaotic (spaced ~1 second apart)
- [ ] Vibration works for different commands

---

## Troubleshooting Checklist

If app doesn't work:

1. **Check ADB connection:** `adb devices` → should show "device"
2. **Check app installed:** `adb shell pm list packages | grep com.guide.app`
3. **Check camera permission:** Settings → Apps → PathSense → Permissions
4. **Check logs:** `adb logcat | grep -i error`
5. **Check model files exist:**
   ```bash
   # Verify in source
   ls app/src/main/assets/models/detector.tflite
   ls app/src/main/assets/models/labels.txt
   ```
6. **Rebuild from clean:**
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

---

## Additional Resources

- **Android SDK Command Line Tools:** https://developer.android.com/studio/command-line
- **ADB Documentation:** https://developer.android.com/tools/adb
- **Gradle Wrapper:** https://docs.gradle.org/current/userguide/gradle_wrapper.html

---

**Good luck with your hackathon! 🚀**

This lightweight setup requires no Android Studio and minimal disk space (~660 MB vs 3 GB).