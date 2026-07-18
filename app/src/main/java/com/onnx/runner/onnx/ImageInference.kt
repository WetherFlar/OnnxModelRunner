package com.onnx.runner.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.graphics.Bitmap
import android.util.Log
import java.nio.FloatBuffer

/**
 * 图像推理结果
 */
data class ImageInferenceResult(
    val outputBitmap: Bitmap,
    val inferenceTimeMs: Long,
    val inputShape: String,
    val outputShape: String
)

/**
 * 图像处理模型推理器
 *
 * 支持图到图（image-to-image）模型，如：
 * - 超分辨率（Super Resolution）
 * - 图像去噪（Denoising）
 * - 风格迁移（Style Transfer）
 * - 图像增强（Enhancement）
 *
 * 自动检测模型的输入输出格式，支持常见的 NCHW / NHWC 布局。
 */
class ImageInference(
    private val session: OrtSession
) {
    companion object {
        private const val TAG = "ImageInference"
    }

    // 模型输入信息
    private val inputName: String = session.inputNames.first()
    private val inputInfo: TensorInfo = session.inputInfo[inputName]?.info as? TensorInfo
        ?: throw IllegalArgumentException("图像模型输入 '$inputName' 不是张量")
    private val inputShape: LongArray = inputInfo.shape

    // 模型输出信息
    private val outputName: String = session.outputNames.first()

    init {
        val outputShape = (session.outputInfo[outputName]?.info as? TensorInfo)?.shape
            ?: throw IllegalArgumentException("图像模型输出 '$outputName' 不是张量")
        Log.i(TAG, "模型输入: $inputName, 形状: ${inputShape.toList()}")
        Log.i(TAG, "模型输出: $outputName, 形状: ${outputShape.toList()}")
    }

    /**
     * 运行图像推理
     *
     * @param inputBitmap 输入图片
     * @return 推理结果
     */
    fun runInference(inputBitmap: Bitmap): ImageInferenceResult {
        val startTime = System.currentTimeMillis()

        // 1. 预处理：将 Bitmap 转换为模型需要的输入张量
        val inputTensor = preprocess(inputBitmap)

        inputTensor.use {
            // 2. 运行推理
            val output = session.run(mapOf(inputName to inputTensor))
            output.use {
                // 获取输出张量（强制转换为 OnnxTensor 以获取 info 和 value）
                val outputTensor = output[0] as OnnxTensor
                val actualOutputShape = (outputTensor.info as? TensorInfo)?.shape
                    ?: throw IllegalStateException("图像模型输出不是张量")

                // 3. 后处理：将输出张量转换为 Bitmap
                val outputBitmap = postprocess(outputTensor.value, actualOutputShape)

                val inferenceTime = System.currentTimeMillis() - startTime

                return ImageInferenceResult(
                    outputBitmap = outputBitmap,
                    inferenceTimeMs = inferenceTime,
                    inputShape = inputShape.joinToString(" x "),
                    outputShape = actualOutputShape.joinToString(" x ")
                )
            }
        }
    }

    /**
     * 预处理：Bitmap -> ONNX 输入张量
     *
     * 自动检测输入形状，支持：
     * - [N, C, H, W] NCHW 格式（最常见）
     * - [N, H, W, C] NHWC 格式
     * - [1, C, H, W] 或 [1, H, W, C]
     */
    private fun preprocess(bitmap: Bitmap): OnnxTensor {
        // 解析输入形状
        // 找到 H 和 W 的维度（跳过 batch 和 channel 维度）
        val shape = inputShape.map { if (it < 0) 256L else it }.toLongArray()

        // 判断布局：NCHW 还是 NHWC
        val isNchw = isNchwLayout(shape)
        val channels = if (isNchw) shape[1].toInt() else shape[3].toInt()
        val targetHeight = if (isNchw) shape[2].toInt() else shape[1].toInt()
        val targetWidth = if (isNchw) shape[3].toInt() else shape[2].toInt()

        Log.i(TAG, "输入布局: ${if (isNchw) "NCHW" else "NHWC"}, 通道=$channels, 尺寸=${targetWidth}x${targetHeight}")

        // 缩放图片到目标尺寸
        val scaledBitmap = if (targetHeight > 0 && targetWidth > 0) {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } else {
            bitmap
        }

        val width = scaledBitmap.width
        val height = scaledBitmap.height

        // 提取像素数据并归一化到 [0, 1]
        val pixels = IntArray(width * height)
        scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 创建 float 数组
        val floatData = FloatArray(width * height * channels)

        if (isNchw) {
            // NCHW: [N][C][H][W] -> 先按通道分组，再按行列
            // R 通道
            for (i in pixels.indices) {
                val pixel = pixels[i]
                floatData[i] = ((pixel shr 16) and 0xFF) / 255.0f           // R
            }
            if (channels >= 3) {
                val offset = width * height
                for (i in pixels.indices) {
                    val pixel = pixels[i]
                    floatData[offset + i] = ((pixel shr 8) and 0xFF) / 255.0f  // G
                }
                val offset2 = width * height * 2
                for (i in pixels.indices) {
                    val pixel = pixels[i]
                    floatData[offset2 + i] = (pixel and 0xFF) / 255.0f        // B
                }
                if (channels == 4) {
                    val offset3 = width * height * 3
                    for (i in pixels.indices) {
                        val pixel = pixels[i]
                        floatData[offset3 + i] = ((pixel shr 24) and 0xFF) / 255.0f // A
                    }
                }
            }
        } else {
            // NHWC: [N][H][W][C] -> 按行列，每像素的通道连续
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val base = i * channels
                floatData[base] = ((pixel shr 16) and 0xFF) / 255.0f       // R
                if (channels >= 3) {
                    floatData[base + 1] = ((pixel shr 8) and 0xFF) / 255.0f  // G
                    floatData[base + 2] = (pixel and 0xFF) / 255.0f          // B
                    if (channels == 4) {
                        floatData[base + 3] = ((pixel shr 24) and 0xFF) / 255.0f // A
                    }
                }
            }
        }

        // 创建 ONNX 张量
        val actualShape = if (isNchw) {
            longArrayOf(1, channels.toLong(), height.toLong(), width.toLong())
        } else {
            longArrayOf(1, height.toLong(), width.toLong(), channels.toLong())
        }

        val buffer = FloatBuffer.wrap(floatData)
        return OnnxTensor.createTensor(OnnxEngine.environment, buffer, actualShape)
    }

    /**
     * 后处理：ONNX 输出张量 -> Bitmap
     *
     * @param outputValue       输出张量的值
     * @param actualOutputShape 输出张量的实际形状
     */
    private fun postprocess(outputValue: Any, actualOutputShape: LongArray): Bitmap {
        val outputShape = actualOutputShape.map { if (it < 0) 0L else it }

        Log.i(TAG, "输出形状: $outputShape")

        // 判断输出布局
        val isNchw = isNchwLayout(outputShape.toLongArray())
        val channels: Int
        val height: Int
        val width: Int

        if (outputShape.size == 4) {
            if (isNchw) {
                channels = outputShape[1].toInt()
                height = outputShape[2].toInt()
                width = outputShape[3].toInt()
            } else {
                height = outputShape[1].toInt()
                width = outputShape[2].toInt()
                channels = outputShape[3].toInt()
            }
        } else if (outputShape.size == 3) {
            // [C, H, W] 或 [H, W, C]
            if (isNchw) {
                channels = outputShape[0].toInt()
                height = outputShape[1].toInt()
                width = outputShape[2].toInt()
            } else {
                height = outputShape[0].toInt()
                width = outputShape[1].toInt()
                channels = outputShape[2].toInt()
            }
        } else {
            throw IllegalStateException("不支持的输出形状: $outputShape")
        }

        // 确保尺寸有效
        if (width <= 0 || height <= 0) {
            throw IllegalStateException("无效的输出尺寸: ${width}x${height}")
        }

        val actualChannels = channels.coerceAtMost(4).coerceAtLeast(1)
        Log.i(TAG, "输出图片: ${width}x${height}, 通道=$actualChannels")

        // 将输出值转换为 float 数组
        val floatData = flattenToArray(outputValue)

        // 创建 Bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        if (isNchw) {
            // NCHW 输出
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val idx = y * width + x
                    val r = clamp(floatData[idx])
                    val g = if (actualChannels >= 3) clamp(floatData[width * height + idx]) else r
                    val b = if (actualChannels >= 3) clamp(floatData[width * height * 2 + idx]) else r
                    val a = if (actualChannels >= 4) clamp(floatData[width * height * 3 + idx]) else 255
                    pixels[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        } else {
            // NHWC 输出
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val idx = y * width + x
                    val base = idx * actualChannels
                    val r = clamp(floatData[base])
                    val g = if (actualChannels >= 3) clamp(floatData[base + 1]) else r
                    val b = if (actualChannels >= 3) clamp(floatData[base + 2]) else r
                    val a = if (actualChannels >= 4) clamp(floatData[base + 3]) else 255
                    pixels[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * 判断是否为 NCHW 布局
     * NCHW: 形状中第2维（index=1）较小（通常是1或3，代表通道数）
     * NHWC: 形状中最后1维较小
     */
    private fun isNchwLayout(shape: LongArray): Boolean {
        if (shape.size < 3) return true
        val dim1 = shape.getOrElse(1) { 1 }
        val lastDim = shape.last()
        // 如果第2维比最后一维小很多，很可能是 NCHW
        return dim1 <= 4 && dim1 < lastDim
    }

    /**
     * 将任意嵌套数组展平为一维 float 数组
     */
    private fun flattenToArray(value: Any): FloatArray {
        val result = mutableListOf<Float>()
        fun flatten(v: Any) {
            when (v) {
                is FloatArray -> v.forEach { result.add(it) }
                is Array<*> -> v.forEach { if (it != null) flatten(it) }
                is Float -> result.add(v)
                is Number -> result.add(v.toFloat())
                else -> {}
            }
        }
        flatten(value)
        return result.toFloatArray()
    }

    /**
     * 将 float 值限制在 [0, 255] 范围内并转为 Int
     */
    private fun clamp(value: Float): Int {
        // 假设输出在 [0, 1] 范围，乘以 255
        val v = (value * 255).toInt()
        return v.coerceIn(0, 255)
    }

    /**
     * 关闭会话
     */
    fun close() {
        session.close()
    }
}