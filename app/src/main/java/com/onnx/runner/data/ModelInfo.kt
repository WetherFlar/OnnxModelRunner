package com.onnx.runner.data

/**
 * 模型类型枚举
 */
enum class ModelType(val displayName: String) {
    IMAGE("图像处理"),   // 图到图模型：超分辨率、去噪、风格迁移等
    TTS("语音合成");     // 文本转语音模型：VITS、Kokoro 等
}

/**
 * 已导入模型的信息
 *
 * @param id            模型唯一ID（用文件名生成）
 * @param name          模型显示名称
 * @param filePath      模型文件在应用内部存储的路径
 * @param type          模型类型
 * @param fileSizeBytes 模型文件大小（字节）
 * @param importTime    导入时间戳
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val filePath: String,
    val type: ModelType,
    val fileSizeBytes: Long,
    val importTime: Long
) {
    /** 格式化文件大小，如 "12.3 MB" */
    val formattedSize: String
        get() = when {
            fileSizeBytes >= 1_073_741_824 -> "%.2f GB".format(fileSizeBytes / 1_073_741_824.0)
            fileSizeBytes >= 1_048_576    -> "%.2f MB".format(fileSizeBytes / 1_048_576.0)
            fileSizeBytes >= 1024         -> "%.2f KB".format(fileSizeBytes / 1024.0)
            else                          -> "$fileSizeBytes B"
        }
}