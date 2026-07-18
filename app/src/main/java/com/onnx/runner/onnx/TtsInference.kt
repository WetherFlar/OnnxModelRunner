package com.onnx.runner.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Log
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * TTS 推理结果
 *
 * @param samples    音频采样数据（float，范围 [-1.0, 1.0]）
 * @param sampleRate 采样率
 */
data class TtsResult(
    val samples: FloatArray,
    val sampleRate: Int
)

/**
 * TTS 模型推理器
 *
 * 支持通用的 ONNX TTS 模型。自动检测模型的输入输出格式。
 *
 * 常见支持的模型类型：
 * - VITS 系列（输入：token IDs + speaker ID）
 * - 其他文本到音频的 ONNX 模型
 *
 * 注意：对于需要复杂文本前端处理（如 phoneme 转换、espeak-ng）的模型，
 * 本推理器提供基础的文本到 token 映射。高级模型可能需要额外的预处理。
 */
class TtsInference(
    private val session: OrtSession
) {
    companion object {
        private const val TAG = "TtsInference"
        private const val DEFAULT_SAMPLE_RATE = 22050
    }

    private val inputNames: List<String> = session.inputNames.toList()
    private val outputNames: List<String> = session.outputNames.toList()

    init {
        Log.i(TAG, "TTS 模型输入: $inputNames")
        Log.i(TAG, "TTS 模型输出: $outputNames")
        inputNames.forEach { name ->
            val shape = (session.inputInfo[name]?.info as? TensorInfo)?.shape?.toList()
                ?: throw IllegalArgumentException("TTS 模型输入 '$name' 不是张量")
            Log.i(TAG, "  输入 '$name' 形状: $shape")
        }
        outputNames.forEach { name ->
            val shape = (session.outputInfo[name]?.info as? TensorInfo)?.shape?.toList()
                ?: throw IllegalArgumentException("TTS 模型输出 '$name' 不是张量")
            Log.i(TAG, "  输出 '$name' 形状: $shape")
        }
    }

    /**
     * 运行 TTS 推理
     *
     * @param text      输入文本
     * @param speakerId 说话人 ID（多说话人模型使用）
     * @param speed     语速（1.0 = 正常速度）
     * @return TTS 推理结果
     */
    fun runInference(text: String, speakerId: Int = 0, speed: Float = 1.0f): TtsResult {
        Log.i(TAG, "开始 TTS 推理: text='$text', speakerId=$speakerId, speed=$speed")

        // 1. 准备输入张量
        val inputs = mutableMapOf<String, OnnxTensor>()
        val tensorsToClose = mutableListOf<OnnxTensor>()

        try {
            for (inputName in inputNames) {
                val tensor = createInputTensor(inputName, text, speakerId, speed)
                if (tensor != null) {
                    inputs[inputName] = tensor
                    tensorsToClose.add(tensor)
                }
            }

            // 2. 运行推理
            val output = session.run(inputs)
            output.use {
                // 3. 解析输出
                val samples = parseAudioOutput(output)
                val sampleRate = detectSampleRate()

                Log.i(TAG, "TTS 推理完成: ${samples.size} 个采样点, 采样率=$sampleRate")
                return TtsResult(samples, sampleRate)
            }
        } finally {
            tensorsToClose.forEach { it.close() }
        }
    }

    /**
     * 根据输入名称创建对应的张量
     */
    private fun createInputTensor(
        name: String,
        text: String,
        speakerId: Int,
        speed: Float
    ): OnnxTensor? {
        val info = session.inputInfo[name]?.info as? TensorInfo
            ?: throw IllegalArgumentException("TTS 模型输入 '$name' 不是张量")
        val shape = info.shape
        val lowerName = name.lowercase()

        return try {
            when {
                // 长度输入必须在文本输入前判断：如 input_lengths 同时包含 input 和 length。
                lowerName.contains("length") || lowerName.contains("len") -> {
                    val textLen = text.length.toLong()
                    OnnxTensor.createTensor(
                        OnnxEngine.environment,
                        LongBuffer.wrap(longArrayOf(textLen)),
                        longArrayOf(1)
                    )
                }
                // 说话人 ID
                lowerName.contains("sid") || lowerName.contains("speaker") ||
                lowerName.contains("spk") || lowerName.contains("speaker_id") -> {
                    createSpeakerIdTensor(name, speakerId, shape)
                }
                // 语速 / 长度缩放
                lowerName.contains("speed") || lowerName.contains("length_scale") ||
                lowerName.contains("noise") -> {
                    createFloatScalarTensor(name, speed, shape)
                }
                // 文本输入：可能是 token IDs（int64）或文本特征（float）
                lowerName.contains("text") || lowerName.contains("token") ||
                lowerName.contains("input") || lowerName.contains("phoneme") ||
                lowerName.contains("seq") -> {
                    createTextTensor(name, text, shape)
                }
                else -> {
                    Log.w(TAG, "未知输入 '$name'，尝试创建文本张量")
                    createTextTensor(name, text, shape)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建输入张量 '$name' 失败", e)
            null
        }
    }

    /**
     * 创建文本/Token 张量
     * 将文本字符的 Unicode 码点作为 token IDs
     */
    private fun createTextTensor(name: String, text: String, shape: LongArray): OnnxTensor {
        // 将文本转换为 token IDs（使用字符的 Unicode 码点）
        val tokens = text.map { it.code.toLong() }.toLongArray()

        // 根据形状确定张量形状
        val tensorShape = if (shape.size >= 2) {
            // [batch, seq_len] 格式
            longArrayOf(1, tokens.size.toLong())
        } else {
            longArrayOf(tokens.size.toLong())
        }

        return OnnxTensor.createTensor(
            OnnxEngine.environment,
            LongBuffer.wrap(tokens),
            tensorShape
        )
    }

    /**
     * 创建说话人 ID 张量
     */
    private fun createSpeakerIdTensor(name: String, speakerId: Int, shape: LongArray): OnnxTensor {
        return OnnxTensor.createTensor(
            OnnxEngine.environment,
            LongBuffer.wrap(longArrayOf(speakerId.toLong())),
            longArrayOf(1)
        )
    }

    /**
     * 创建浮点标量张量
     */
    private fun createFloatScalarTensor(name: String, value: Float, shape: LongArray): OnnxTensor {
        return OnnxTensor.createTensor(
            OnnxEngine.environment,
            FloatBuffer.wrap(floatArrayOf(value)),
            longArrayOf(1)
        )
    }

    /**
     * 解析音频输出
     */
    private fun parseAudioOutput(output: OrtSession.Result): FloatArray {
        // 获取第一个输出（强制转换为 OnnxTensor 以获取 value）
        val outputValue = (output[0] as OnnxTensor).value
        return flattenToFloatArray(outputValue)
    }

    /**
     * 将任意嵌套数组展平为一维 float 数组
     */
    private fun flattenToFloatArray(value: Any): FloatArray {
        val result = mutableListOf<Float>()
        fun flatten(v: Any) {
            when (v) {
                is FloatArray -> v.forEach { result.add(it) }
                is Array<*> -> v.forEach { if (it != null) flatten(it) }
                is Float -> result.add(v)
                is Double -> result.add(v.toFloat())
                is Number -> result.add(v.toFloat())
                else -> {}
            }
        }
        flatten(value)
        return result.toFloatArray()
    }

    /**
     * 检测采样率
     * 尝试从模型元数据中获取，否则使用默认值
     */
    private fun detectSampleRate(): Int {
        return try {
            val metadata = session.metadata
            val customMetadata = metadata.customMetadata
            // 尝试从元数据中获取采样率
            for (key in customMetadata.keys) {
                val lowerKey = key.lowercase()
                if (lowerKey.contains("sample_rate") || lowerKey.contains("samplerate") ||
                    lowerKey.contains("sr")) {
                    return customMetadata[key]?.toIntOrNull() ?: DEFAULT_SAMPLE_RATE
                }
            }
            DEFAULT_SAMPLE_RATE
        } catch (e: Exception) {
            DEFAULT_SAMPLE_RATE
        }
    }

    /**
     * 关闭会话
     */
    fun close() {
        session.close()
    }
}