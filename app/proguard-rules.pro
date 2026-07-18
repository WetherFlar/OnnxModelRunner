# ==========================================
# ProGuard / R8 混淆规则
# ==========================================

# ===== ONNX Runtime 相关类不能被混淆/移除 =====
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }

# 保留 native 方法（JNI 调用需要）
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留 Kotlin 元数据
-keep class kotlin.Metadata { *; }

# 保留协程相关类
-keepclassmembers class kotlinx.coroutines.** { *; }