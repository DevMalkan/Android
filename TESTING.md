# PathSense Android Testing Guide

This guide provides step-by-step instructions to build and run the PathSense Android app on your physical Android device connected to your MacBook.

## Prerequisites

### 1. Install Android Studio
- Download from: https://developer.android.com/studio
- Install Android Studio on your MacBook
- During installation, ensure "Android SDK" and "Android SDK Platform" are selected

### 2. Install Required SDK Components
Open Android Studio and install:
- **SDK Platform**: Android 13.0 (API Level 33) or higher
- **SDK Build Tools**: 33.0.0 or higher
- **NDK** (if using native libraries)

To install:
1. Open Android Studio
2. Go to `Preferences` → `Appearance & Behavior` → `System Settings` → `Android SDK`
3. Click `SDK Platforms` tab → Check Android 13.0 (Tiramisu)
4. Click `SDK Tools` tab → Check "Android SDK Build-Tools"
5. Click "Apply" to install

### 3. Configure Android Device
On your Android phone:
1. Go to `Settings` → `About Phone`
2. Tap `Build Number` 7 times to enable Developer Options
3. Go to `Settings` → `System` → `Developer Options`
4. Enable `USB Debugging`
5. Enable `Install via USB` (if available)

### 4. Connect Device to MacBook
1. Connect your Android phone to MacBook using USB-C to USB-C cable
2. On your phone, you'll see a prompt "Allow USB debugging?"
3. Check "Always allow from this computer"
4. Tap "OK"

---

## Step-by-Step Testing Instructions

### Step 1: Verify Device Connection

Open Terminal on your MacBook and run:

```bash
cd /Users/devmalkan/Desktop/PathSense/Android
./gradlew --version
```

If Gradle is not found, you may need to use Android Studio's bundled Gradle or install it via Homebrew:

```bash
brew install gradle
```

Check if your device is connected:

```bash
# Add Android SDK platform-tools to PATH (if not already)
export PATH=$PATH:$HOME/Library/Android/sdk/platform-tools

# Verify device connection
adb devices
```

You should see output like:
```
List of devices attached
ABC123XYZ    device
```

If you see "unauthorized", check your phone for the USB debugging prompt.

### Step 2: Build the App

Navigate to the Android project directory:

```bash
cd /Users/devmalkan/Desktop/PathSense/Android
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

This will:
- Download dependencies
- Compile Kotlin code
- Package TFLite model and labels from `app/src/main/assets/models/`
- Generate a debug APK

**Expected output:**
```
BUILD SUCCESSFUL in Xs
```

The APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Step 3: Install on Device

Install the app directly to your connected device:

```bash
./gradlew installDebug
```

**Alternative:** Install using ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The `-r` flag reinstalls the app if it already exists.

**Expected output:**
```
BUILD SUCCESSFUL
```

### Step 4: Launch the App

**Option A: Launch from Phone**
- Open the app drawer on your phone
- Look for "PathSense" or your app name
- Tap to open

**Option B: Launch via ADB**

First, find the main activity:

```bash
# List all activities in the app
adb shell dumpsys package com.guide.app | grep -i activity
```

Then launch (replace with actual main activity name):

```bash
adb shell am start -n com.guide.app/.MainActivity
```

### Step 5: Grant Camera Permissions

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

You should see logs every 15 frames (when processing occurs).

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

### Test Frame Skipping

The app processes **1 frame every 15 frames** to avoid chaotic audio. You should hear:
- Audio instructions approximately every 1 second (not continuous)
- Clear, non-overlapping speech

### Verify JSON Logging

Check telemetry logs:

```bash
adb logcat | grep -i "AgentClient"
```

**Expected output:**
```
D/AgentClient: Flushed X events
```

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

### Filter by Log Level

```bash
# Only errors
adb logcat *:E

# Errors and warnings
adb logcat *:W

# Debug and above
adb logcat *:D
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

---

## Common Issues & Solutions

### Issue 1: `adb: command not found`

**Solution:** Add Android SDK platform-tools to PATH:

```bash
echo 'export PATH=$PATH:$HOME/Library/Android/sdk/platform-tools' >> ~/.zshrc
source ~/.zshrc
```

### Issue 2: Device shows "unauthorized"

**Solution:**
1. Disconnect and reconnect USB cable
2. Check phone for USB debugging prompt
3. Revoke USB debugging authorizations: `Settings` → `Developer Options` → `Revoke USB debugging authorizations`
4. Reconnect and approve again

### Issue 3: Gradle build fails

**Solution:**
1. Open project in Android Studio
2. Let Android Studio download missing dependencies
3. File → Sync Project with Gradle Files
4. Try building from Terminal again

### Issue 4: No detections in logs

**Solution:**
1. Ensure camera permission is granted
2. Point camera at supported objects (person, car, chair, etc.)
3. Check TFLite model exists: `adb shell ls /data/data/com.guide.app/assets/models/`
4. Check confidence threshold (0.45) - may need better lighting

### Issue 5: No audio output

**Solution:**
1. Check phone volume is not muted
2. Verify Text-to-Speech is enabled: `Settings` → `Accessibility` → `Text-to-Speech`
3. Check logs for TTS initialization: `adb logcat | grep -i "tts"`

### Issue 6: App crashes on launch

**Solution:**
1. Check crash logs: `adb logcat | grep -i "AndroidRuntime"`
2. Verify model files exist in `app/src/main/assets/models/`:
   - `detector.tflite`
   - `labels.txt`
3. Clean and rebuild:
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

---

## Performance Testing

### Test 1: Model Inference Speed

```bash
adb logcat | grep -i "inference"
```

Look for inference time. Should be < 100ms on most modern devices.

### Test 2: Frame Processing Rate

The app processes 1 frame every 15 frames:
- Camera: 15 FPS
- Processing: ~1 FPS
- Audio output: Max 1 per second (with rate limiting)

### Test 3: Memory Usage

```bash
adb shell dumpsys meminfo com.guide.app
```

Check for memory leaks if app runs for extended periods.

### Test 4: Battery Usage

Run the app for 10 minutes and check battery consumption:

```bash
adb shell dumpsys batterystats com.guide.app
```

---

## Testing Scenarios

### Scenario 1: Person Detection
1. Point camera at a person
2. **Expected audio:** "person ahead, stop" (if close) or "person ahead, move left/right"
3. **Expected vibration:** Long vibration (600ms) for STOP, short pattern for VEER

### Scenario 2: Multiple Objects
1. Point camera at multiple objects (person, chair, car)
2. **Expected:** App prioritizes most critical obstacle (largest, closest to center)
3. **Expected audio:** Single, clear instruction (not overlapping)

### Scenario 3: No Obstacles
1. Point camera at empty space or wall
2. **Expected:** No audio output
3. **Expected logs:** "ActionToken.CLEAR"

### Scenario 4: Left/Right Navigation
1. Position obstacle on left side of frame
2. **Expected audio:** "[object] ahead, move right"
3. Position obstacle on right side
4. **Expected audio:** "[object] ahead, move left"

### Scenario 5: Frame Skipping
1. Wave camera rapidly
2. **Expected:** Audio NOT chaotic or continuous
3. **Expected:** Instructions spaced ~1 second apart

---

## Build Variants

### Debug Build (for testing)
```bash
./gradlew assembleDebug
./gradlew installDebug
```

### Release Build (for production)
```bash
./gradlew assembleRelease
```

**Note:** Release builds require signing configuration in `build.gradle`

---

## Verify Model Integration

### Check Model File Size
```bash
ls -lh app/src/main/assets/models/detector.tflite
```

**Expected:** ~3-5 MB (YOLOv8-Nano INT8 quantized)

### Check Labels File
```bash
cat app/src/main/assets/models/labels.txt | wc -l
```

**Expected:** 80 lines (COCO classes)

### Verify Model in APK
```bash
# Extract APK
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep detector.tflite

# Expected output:
# assets/models/detector.tflite
```

---

## Quick Reference: Essential Commands

```bash
# 1. Check device connection
adb devices

# 2. Build and install
cd /Users/devmalkan/Desktop/PathSense/Android
./gradlew clean assembleDebug installDebug

# 3. Launch app
adb shell am start -n com.guide.app/.MainActivity

# 4. View logs
adb logcat | grep -E "TfliteInferenceEngine|Planner|CueManager"

# 5. Clear data and restart
adb shell pm clear com.guide.app
adb shell am start -n com.guide.app/.MainActivity

# 6. Uninstall
adb uninstall com.guide.app
```

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
| **JSON Logging** | `{"events": ["obstacle_center","stop"], "classes": ["person"], "confidence": 0.74}` |

---

## Contact & Support

If you encounter issues:
1. Check logs: `adb logcat`
2. Verify model files exist in assets
3. Ensure camera permissions granted
4. Check USB debugging enabled
5. Try clean rebuild: `./gradlew clean`

For hackathon demo, test these key scenarios:
- ✅ Person detection with STOP command
- ✅ Left/Right navigation with VEER commands
- ✅ Frame skipping (not chaotic audio)
- ✅ Real-time inference (< 100ms)
- ✅ Clear audio output with object names

**Good luck with your hackathon! 🚀**