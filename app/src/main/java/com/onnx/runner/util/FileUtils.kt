package com.onnx.runner.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 文件工具类
 */
object FileUtils {

    /**
     * 从 Uri 获取文件名
     */
    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.lastPathSegment ?: "unknown"
        }
        return result
    }

    /**
     * 从 Uri 读取文件内容到临时文件
     */
    fun uriToFile(context: Context, uri: Uri, suffix: String = ".tmp"): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return null
            val tempFile = File.createTempFile("import", suffix, context.cacheDir)
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 将 Bitmap 保存到相册目录
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        return try {
            val dir = File(context.getExternalFilesDir("images"))
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "$fileName.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 将音频采样数据保存为 WAV 文件
     */
    fun savePcmToWav(samples: FloatArray, sampleRate: Int, outputFile: File): Boolean {
        return try {
            // 将 float [-1, 1] 转换为 16-bit PCM
            val pcmData = ShortArray(samples.size)
            for (i in samples.indices) {
                val clamped = samples[i].coerceIn(-1.0f, 1.0f)
                pcmData[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
            }

            FileOutputStream(outputFile).use { fos ->
                // WAV 文件头
                val totalDataLen = pcmData.size * 2
                val byteRate = sampleRate * 2 // 16-bit mono
                val header = ByteArray(44)

                // RIFF header
                header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
                header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
                writeInt(header, 4, totalDataLen + 36)
                header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
                header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()

                // fmt chunk
                header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
                header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
                writeInt(header, 16, 16)       // chunk size
                writeShort(header, 20, 1.toShort())  // PCM format
                writeShort(header, 22, 1.toShort())  // mono
                writeInt(header, 24, sampleRate)
                writeInt(header, 28, byteRate)
                writeShort(header, 32, 2.toShort())  // block align
                writeShort(header, 34, 16.toShort()) // bits per sample

                // data chunk
                header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
                header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
                writeInt(header, 40, totalDataLen)

                fos.write(header)

                // 写入 PCM 数据
                val byteBuffer = java.nio.ByteBuffer.allocate(pcmData.size * 2)
                byteBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                for (sample in pcmData) {
                    byteBuffer.putShort(sample)
                }
                fos.write(byteBuffer.array())
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun writeInt(header: ByteArray, offset: Int, value: Int) {
        header[offset] = (value and 0xFF).toByte()
        header[offset + 1] = ((value shr 8) and 0xFF).toByte()
        header[offset + 2] = ((value shr 16) and 0xFF).toByte()
        header[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShort(header: ByteArray, offset: Int, value: Short) {
        header[offset] = (value.toInt() and 0xFF).toByte()
        header[offset + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }
}