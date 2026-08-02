# RakibCame Stabilizer

A CameraX-based Android camera app upgraded into an **AI Video Stabilization / Electronic Image Stabilization (EIS)** style camera. The previous DSLR-style UI, voice controls, ML Kit overlays, gallery/database configuration, Gradle structure, and signing configuration are kept, while the camera pipeline now includes stabilized preview and stabilized video recording controls.

## What is new

- **CameraX Preview Stabilization** when supported by the device.
- **CameraX VideoCapture stabilization request** for recorded video.
- **Gyroscope-based preview correction** for phones where native stabilization is unavailable or limited.
- **Stabilizer modes:**
  - Normal Video
  - AI Stabilized
  - Action Mode
  - Cinematic Mode
  - Extreme Stabilizer
- **Stabilization levels:** Low, Medium, High, Ultra.
- **Buffered preview smoothing:** mode-dependent delay from 0ms to 1000ms.
- **Automatic crop margin:** preview is scaled slightly to hide stabilization edges.
- **Voice commands:** “stabilizer on/off”, “action mode”, “extreme stabilizer”, plus Bengali equivalents.
- **HUD status:** shows EIS level, buffer delay, native hardware support, and gyro fallback state.

## Architecture

```text
Camera Sensor
  ↓
CameraX Preview + VideoCapture
  ↓
Native CameraX EIS if supported
  ↓
Gyroscope motion sampling
  ↓
Buffered smoothing transform
  ↓
Crop/scale + translate + roll correction
  ↓
Smooth preview + stabilized recording request
  ↓
MediaStore save
```

## Important files

- `app/src/main/java/com/example/ui/CameraHomeScreen.kt` — CameraX bind pipeline, preview transform, mode wheel, HUD.
- `app/src/main/java/com/example/ui/CameraViewModel.kt` — settings, voice commands, recording/photo actions, stabilizer state.
- `app/src/main/java/com/example/ui/GyroStabilizationEngine.kt` — gyroscope-based EIS preview smoothing engine.
- `app/src/main/java/com/example/ui/AppSettingsPanel.kt` — EIS settings UI.
- `app/src/main/AndroidManifest.xml` — camera/mic permissions and optional gyroscope feature.

## Build locally

**Prerequisites**

- Android Studio recent stable/canary that supports the configured Android Gradle Plugin.
- JDK 17 or newer.
- Android SDK matching compile SDK 36.

**Steps**

1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Create `.env` from `.env.example` if you use Gemini features:

   ```bash
   cp .env.example .env
   ```

4. Run on a physical Android phone for best gyroscope/camera results.
5. Grant Camera and Microphone permissions.

## Notes

- Premium-phone Action Mode/Super Steady quality depends heavily on device Camera2/CameraX hardware support.
- The app requests native CameraX stabilization and also applies an on-device gyroscope fallback to the preview.
- True deep-learning frame-warp stabilization can be added later with OpenCV/TensorFlow Lite, but this version is designed to work without requiring a bundled model file.
