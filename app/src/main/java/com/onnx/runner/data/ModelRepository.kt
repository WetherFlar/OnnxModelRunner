package com.onnx.runner.data

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 模型仓库：负责管理已导入的模型文件
 *
 * 模型被永久保存在应用的内部存储中（filesDir/models/），
 * 即使应用关闭后重新打开，模型依然存在。
 */
class ModelRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ModelRepository"
        private const val MODELS_DIR = "models"
        private const val TTS_PREFIX = "tts_"
        private const val IMAGE_PREFIX = "img_"

        @Volatile
        private var instance: ModelRepository? = null

        fun getInstance(context: Context): ModelRepository {
            return instance ?: synchronized(this) {
                instance ?: ModelRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    /** 模型存储目录 */
    private val modelsDir: File by lazy {
        File(context.filesDir, MODELS_DIR).also { it.mkdirs() }
    }

    /**
     * 获取所有已导入的模型列表
     */
    fun getAllModels(): List<ModelInfo> {
        val models = mutableListOf<ModelInfo>()
        modelsDir.listFiles { file ->
            file.isFile && file.extension.equals("onnx", ignoreCase = true)
        }
            ?.forEach { file ->
                val type = if (file.name.startsWith(TTS_PREFIX)) ModelType.TTS else ModelType.IMAGE
                models.add(
                    ModelInfo(
                        id = file.nameWithoutExtension,
                        name = file.name.removePrefix(TTS_PREFIX).removePrefix(IMAGE_PREFIX),
                        filePath = file.absolutePath,
                        type = type,
                        fileSizeBytes = file.length(),
                        importTime = file.lastModified()
                    )
                )
            }
        return models.sortedByDescending { it.importTime }
    }

    /**
     * 获取指定类型的模型列表
     */
    fun getModelsByType(type: ModelType): List<ModelInfo> {
        return getAllModels().filter { it.type == type }
    }

    /**
     * 导入模型文件到应用内部存储
     *
     * @param sourceFile 源模型文件
     * @param type       模型类型
     * @return 导入后的 ModelInfo，失败返回 null
     */
    fun importModel(sourceFile: File, type: ModelType): ModelInfo? {
        return try {
            if (!sourceFile.exists() || !sourceFile.extension.equals("onnx", ignoreCase = true)) {
                Log.e(TAG, "无效的模型文件: ${sourceFile.absolutePath}")
                return null
            }

            // 生成唯一文件名，加上类型前缀
            val prefix = when (type) {
                ModelType.IMAGE -> IMAGE_PREFIX
                ModelType.TTS -> TTS_PREFIX
            }
            val destName = prefix + sourceFile.name
            val destFile = File(modelsDir, destName)

            // 如果同名文件已存在，添加数字后缀
            var finalFile = destFile
            var counter = 1
            while (finalFile.exists()) {
                finalFile = File(modelsDir, "${prefix}${sourceFile.nameWithoutExtension}_$counter.onnx")
                counter++
            }

            // 复制文件
            sourceFile.inputStream().use { input ->
                finalFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Log.i(TAG, "模型导入成功: ${finalFile.absolutePath}")
            ModelInfo(
                id = finalFile.nameWithoutExtension,
                name = finalFile.name.removePrefix(TTS_PREFIX).removePrefix(IMAGE_PREFIX),
                filePath = finalFile.absolutePath,
                type = type,
                fileSizeBytes = finalFile.length(),
                importTime = finalFile.lastModified()
            )
        } catch (e: Exception) {
            Log.e(TAG, "导入模型失败", e)
            null
        }
    }

    /**
     * 删除模型
     */
    fun deleteModel(modelInfo: ModelInfo): Boolean {
        return try {
            val file = File(modelInfo.filePath)
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    Log.i(TAG, "模型已删除: ${modelInfo.name}")
                }
                deleted
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "删除模型失败", e)
            false
        }
    }

    /**
     * 根据 ID 获取模型
     */
    fun getModelById(id: String): ModelInfo? {
        return getAllModels().find { it.id == id }
    }
}