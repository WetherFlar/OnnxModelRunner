# ONNX 模型运行器 (OnnxModelRunner)

一个可以在 Android 设备上运行 ONNX 模型的应用，使用最新的 ONNX Runtime (1.27.0)。

## ✨ 功能特性

| 功能 | 说明 |
|------|------|
| 🖼️ 图像处理模型 | 支持图到图模型：超分辨率、去噪、风格迁移、图像增强等 |
| 🔊 TTS 语音合成 | 支持文本转语音模型：VITS、Kokoro 等 |
| 📁 永久导入模型 | 模型保存在应用内部存储，重启后依然存在 |
| 🎛️ GUI 界面 | Material Design 风格，操作简单直观 |
| ⚡ NNAPI 加速 | 可选启用 Android NNAPI 硬件加速 |
| 💾 结果保存 | 支持保存处理后的图片和生成的音频 |

## 📋 系统要求

- **Android 设备**：Android 7.0 (API 24) 或更高
- **开发环境**：Android Studio Hedgehog (2023.1.1) 或更高
- **JDK**：JDK 17
- **Gradle**：8.7（项目会自动下载）

## 🚀 快速开始

### 第一步：用 Android Studio 打开项目

1. 打开 Android Studio
2. 选择 **File → Open**
3. 选择 `OnnxModelRunner` 文件夹
4. 等待 Gradle 同步完成（首次会自动下载依赖，需要联网）

> ⏱️ 首次同步可能需要 5-15 分钟，取决于网络速度

### 第二步：编译 APK

**方式一：直接运行到设备**
1. 用 USB 连接你的 Android 手机（需开启 USB 调试）
2. 在设备下拉栏选择你的手机
3. 点击 ▶️ Run 按钮

**方式二：生成 APK 文件**
1. 选择 **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. 等待编译完成
3. 点击通知栏的 **locate** 找到 APK 文件
4. APK 路径：`app/build/outputs/apk/debug/app-debug.apk`

### 第三步：使用应用

1. **导入模型**：打开应用 → 点击「模型管理」→ 点击「导入图像模型」或「导入 TTS 模型」→ 选择 `.onnx` 文件
2. **图像处理**：点击「图像处理模型」→ 选择模型 → 选择图片 → 点击「运行推理」
3. **语音合成**：点击「TTS 语音合成」→ 选择模型 → 输入文本 → 点击「生成语音」→ 点击「播放」

## 📂 项目结构

```
OnnxModelRunner/
├── build.gradle                    # 项目级构建配置
├── settings.gradle                 # 项目设置
├── gradle.properties               # Gradle 属性
├── app/
│   ├── build.gradle                 # 模块级构建配置（含 ONNX Runtime 依赖）
│   ├── proguard-rules.pro           # 代码混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml      # 应用清单
│       ├── java/com/onnx/runner/
│       │   ├── MainActivity.kt          # 主界面（功能入口）
│       │   ├── ModelManagerActivity.kt  # 模型管理（导入/删除/选择）
│       │   ├── ImageModelActivity.kt    # 图像处理界面
│       │   ├── TtsModelActivity.kt      # TTS 语音合成界面
│       │   ├── data/
│       │   │   ├── ModelInfo.kt         # 模型信息数据类
│       │   │   └── ModelRepository.kt   # 模型仓库（永久存储管理）
│       │   ├── onnx/
│       │   │   ├── OnnxEngine.kt        # ONNX Runtime 引擎管理
│       │   │   ├── ImageInference.kt    # 图像推理器
│       │   │   └── TtsInference.kt      # TTS 推理器
│       │   └── util/
│       │       └── FileUtils.kt         # 文件工具类
│       └── res/                         # 资源文件（布局、字符串、主题等）
```

## 🔧 技术细节

### ONNX Runtime 版本
- 使用 `com.microsoft.onnxruntime:onnxruntime-android:1.27.0`（2026年6月最新版）

### 支持的模型格式

**图像处理模型：**
- 输入：NCHW 或 NHWC 格式的 float 张量（自动检测）
- 通道数：1（灰度）、3（RGB）或 4（RGBA）
- 输出：与输入格式对应的图像张量

**TTS 模型：**
- 自动检测输入名称（text/token/input/speaker/speed 等）
- 输出：音频波形 float 数组（范围 [-1, 1]）
- 自动检测采样率（默认 22050 Hz）

### NNAPI 硬件加速
- 在图像处理界面可勾选「使用 NNAPI 硬件加速」
- 利用 Android 设备的 GPU/NPU 加速推理
- 不支持时自动回退到 CPU

## 📝 获取 ONNX 模型

### 图像处理模型
- [ONNX Model Zoo](https://github.com/onnx/models) - 官方模型库
- 超分辨率模型：Real-ESRGAN、ESPCN 等
- 去噪模型：DnCNN、N2N 等

### TTS 模型
- [sherpa-onnx TTS Models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models)
- VITS 系列、Kokoro 等模型

## ⚠️ 注意事项

1. **模型大小**：大模型可能需要较多内存，低端设备可能 OOM
2. **TTS 文本处理**：本应用使用简单的字符级 token 映射，复杂模型可能需要额外的文本前端处理
3. **首次推理**：首次运行推理会较慢（需要初始化），后续会更快
4. **ABI 过滤**：APK 只包含 arm64-v8a 和 armeabi-v7a 架构，覆盖绝大多数设备

## 🐛 常见问题

**Q: Gradle 同步失败？**
A: 检查网络连接，确保能访问 google() 和 mavenCentral() 仓库

**Q: 编译时内存不足？**
A: 编辑 `gradle.properties`，将 `-Xmx2048m` 改为 `-Xmx4096m`

**Q: 推理时崩溃？**
A: 检查模型输入输出格式是否与预期匹配，查看 Logcat 中的错误日志

**Q: 找不到 .onnx 文件？**
A: 在文件选择器中选择「所有文件」类型，确保能看到 .onnx 文件