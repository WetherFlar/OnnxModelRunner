# ONNX Model Runner (OnnxModelRunner)

An Android application for importing and running ONNX models on-device. It uses **ONNX Runtime for Android 1.27.0** and provides a graphical interface for image-to-image processing and basic text-to-speech (TTS) inference.

> [中文版 README](README.md)

## Features

| Feature | Description |
| --- | --- |
| Image-to-image models | Run image processing models such as super-resolution, denoising, style transfer, and image enhancement. |
| Basic TTS inference | Run compatible text-to-speech ONNX models, including some VITS- or Kokoro-style exports. |
| Persistent model import | Imported models are copied into the app's internal storage and remain available after the app is restarted. |
| Graphical interface | A simple Material Design interface for managing models and running inference. |
| Optional NNAPI | Enable Android NNAPI acceleration for image inference when supported by the device and model. |
| Save results | Save generated images as PNG files and generated audio as WAV files. |

## Requirements

- **Android device:** Android 7.0 (API 24) or later
- **Development environment:** Android Studio Hedgehog (2023.1.1) or later
- **JDK:** JDK 17
- **Android SDK:** API 34 (`compileSdk` / `targetSdk`)
- **Gradle:** 8.7 (included through the Gradle Wrapper)

## Getting Started

### 1. Open the project

1. Start Android Studio.
2. Select **File → Open**.
3. Select the `OnnxModelRunner` directory.
4. Wait for the Gradle sync to finish. The first sync downloads dependencies, so an Internet connection is required.

### 2. Build and install

#### Run directly on a device

1. Connect an Android phone by USB and enable **USB debugging**.
2. Select the device from Android Studio's device selector.
3. Click **Run** (▶).

#### Build a debug APK

1. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
2. After the build succeeds, use **locate** in the notification.
3. The debug APK is normally generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

You can also build from a terminal:

```bash
./gradlew assembleDebug
```

### 3. Use the app

1. **Import a model:** Open the app → **Model Manager** → choose **Import Image Model** or **Import TTS Model** → select an `.onnx` file.
2. **Process an image:** Open **Image Processing Model** → select a model → select an image → tap **Run Inference**.
3. **Generate speech:** Open **TTS** → select a model → enter text → tap **Generate Speech** → tap **Play**.

## Project Structure

```text
OnnxModelRunner/
├── build.gradle                         # Project-level Gradle configuration
├── settings.gradle                      # Project settings
├── gradle.properties                    # Gradle properties
├── gradlew / gradlew.bat                # Gradle Wrapper launchers
├── gradle/wrapper/                      # Gradle Wrapper files
├── README.md                            # Chinese README
├── README-en.md                         # English README
└── app/
    ├── build.gradle                      # App module configuration and dependencies
    ├── proguard-rules.pro                # R8 / ProGuard rules
    └── src/main/
        ├── AndroidManifest.xml           # App manifest
        ├── java/com/onnx/runner/
        │   ├── MainActivity.kt           # Home screen
        │   ├── ModelManagerActivity.kt   # Import, delete, and select models
        │   ├── ImageModelActivity.kt     # Image inference screen
        │   ├── TtsModelActivity.kt       # TTS screen
        │   ├── data/
        │   │   ├── ModelInfo.kt          # Model metadata
        │   │   └── ModelRepository.kt    # Persistent model storage
        │   ├── onnx/
        │   │   ├── OnnxEngine.kt         # ONNX Runtime session management
        │   │   ├── ImageInference.kt     # Image preprocessing and inference
        │   │   └── TtsInference.kt       # Basic TTS input preparation and inference
        │   └── util/
        │       └── FileUtils.kt          # File, image, and WAV utilities
        └── res/                          # Layouts, strings, themes, and drawables
```

## Technical Notes

### ONNX Runtime

The app depends on:

```gradle
com.microsoft.onnxruntime:onnxruntime-android:1.27.0
```

The APK includes `arm64-v8a` and `armeabi-v7a` native libraries.

### Image Models

The image runner is intended for models with a single tensor input and a single tensor output.

- Input layout: NCHW or NHWC (detected heuristically)
- Input type: floating-point image tensor
- Supported channel counts: 1, 3, or 4
- Input normalization: pixel values are converted to the `[0, 1]` range
- Output: a tensor that can be interpreted as an image, typically with values in `[0, 1]`

The app resizes the selected image to the model's expected input size. Models requiring special normalization (for example, `[-1, 1]`, mean/std normalization, BGR order, or multiple inputs) are not fully supported without code changes.

### TTS Models

The TTS runner provides **basic, generic input handling** only.

- It infers likely input roles from names such as `text`, `token`, `input`, `speaker`, `sid`, `speed`, and `length`.
- Text is currently converted to token IDs using character Unicode code points.
- It expects an audio waveform tensor as the first output.
- The sample rate is read from custom model metadata when available; otherwise it defaults to **22050 Hz**.

Many real TTS models need a model-specific text frontend, phonemizer, vocabulary, language settings, speaker embeddings, or additional inputs. Such models may not work correctly with the generic runner as-is.

### NNAPI Acceleration

The Image Processing screen includes an optional NNAPI switch.

- It may use a device's NPU, GPU, or other supported accelerator.
- Availability and performance depend on the device, Android version, and model operators.
- If NNAPI cannot be enabled, the app falls back to CPU execution.

## Where to Find ONNX Models

### Image Processing

- [ONNX Model Zoo](https://github.com/onnx/models)
- Super-resolution models such as Real-ESRGAN and ESPCN
- Denoising models such as DnCNN and N2N

### TTS

- [sherpa-onnx TTS model releases](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models)
- VITS-based models and Kokoro exports

Before importing a model, make sure it is an `.onnx` file and that its inputs are compatible with the app's current generic preprocessing.

## Important Notes

1. **Memory usage:** Large models can require substantial RAM and may cause out-of-memory errors on lower-end devices.
2. **First inference:** The first run is often slower because ONNX Runtime needs to initialize the model and execution provider.
3. **Model compatibility:** ONNX is a file format, not a universal preprocessing standard. A successfully imported model can still require model-specific inputs or preprocessing.
4. **Saved files:** Images and audio are saved in the app-specific external files directory. The exact location is device-dependent.
5. **Model storage:** Imported models are stored in the app's internal storage. Uninstalling or clearing app data removes them.

## Troubleshooting

### Gradle sync fails

Check your Internet connection and verify that Gradle can access the Google and Maven Central repositories. Also make sure Android SDK Platform 34 is installed.

### The build runs out of memory

Edit `gradle.properties` and increase the Gradle JVM heap, for example:

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```

### Inference fails or the app crashes

Check Logcat and verify the model's input/output names, tensor types, shapes, channel order, and normalization requirements. The built-in image and TTS preprocessors support common cases, not every ONNX model.

### I cannot see an `.onnx` file in the file picker

Choose **All files** in the system picker if available, then verify that the model file has an `.onnx` extension.

## License

No license file is currently included in this repository. Add a `LICENSE` file before distributing or accepting external contributions.
