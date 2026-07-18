package com.onnx.runner

import ai.onnxruntime.TensorInfo
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.onnx.runner.data.ModelInfo
import com.onnx.runner.data.ModelRepository
import com.onnx.runner.data.ModelType
import com.onnx.runner.databinding.ActivityTtsModelBinding
import com.onnx.runner.onnx.OnnxEngine
import com.onnx.runner.onnx.TtsInference
import com.onnx.runner.onnx.TtsResult
import com.onnx.runner.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * TTS 语音合成界面
 *
 * 功能：
 * - 选择已导入的 TTS 模型
 * - 输入要合成的文本
 * - 设置说话人 ID 和语速
 * - 生成语音并播放
 * - 保存生成的音频为 WAV 文件
 *
 * 支持通用的 ONNX TTS 模型。
 */
class TtsModelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTtsModelBinding
    private val repository = ModelRepository.getInstance(this)

    private var selectedModel: ModelInfo? = null
    private var ttsResult: TtsResult? = null
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    // 选择模型
    private val selectModelLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val modelId = result.data?.getStringExtra(ImageModelActivity.EXTRA_MODEL_ID)
            if (modelId != null) {
                selectedModel = repository.getModelById(modelId)
                selectedModel?.let { model ->
                    binding.tvModelName.text = model.name
                    binding.tvModelDetails.text = "类型: ${model.type.displayName}  大小: ${model.formattedSize}"
                    loadModelInfo(model)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTtsModelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        // 选择模型
        binding.btnSelectModel.setOnClickListener {
            val intent = Intent(this, ModelManagerActivity::class.java).apply {
                putExtra(ImageModelActivity.EXTRA_FILTER_TYPE, ModelType.TTS.name)
                putExtra(ImageModelActivity.EXTRA_PICK_MODE, true)
            }
            selectModelLauncher.launch(intent)
        }

        // 生成语音
        binding.btnGenerate.setOnClickListener {
            generateSpeech()
        }

        // 播放
        binding.btnPlay.setOnClickListener {
            playAudio()
        }

        // 停止
        binding.btnStop.setOnClickListener {
            stopAudio()
        }

        // 保存音频
        binding.btnSaveAudio.setOnClickListener {
            saveAudio()
        }
    }

    /**
     * 加载模型信息
     */
    private fun loadModelInfo(model: ModelInfo) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val session = OnnxEngine.createSession(model.filePath)
                val inputs = session.inputNames.joinToString { name ->
                    val shape = (session.inputInfo[name]?.info as? TensorInfo)?.shape
                        ?.joinToString("x") ?: "非张量"
                    "$name[$shape]"
                }
                val outputs = session.outputNames.joinToString { name ->
                    val shape = (session.outputInfo[name]?.info as? TensorInfo)?.shape
                        ?.joinToString("x") ?: "非张量"
                    "$name[$shape]"
                }
                session.close()

                withContext(Dispatchers.Main) {
                    binding.tvModelDetails.text = buildString {
                        append("类型: ${model.type.displayName}  大小: ${model.formattedSize}\n")
                        append("输入: $inputs\n")
                        append("输出: $outputs")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvModelDetails.text = "类型: ${model.type.displayName}  大小: ${model.formattedSize}\n模型信息读取失败: ${e.message}"
                }
            }
        }
    }

    /**
     * 生成语音
     */
    private fun generateSpeech() {
        val model = selectedModel
        val text = binding.etInputText.text?.toString()?.trim() ?: ""

        if (model == null) {
            Toast.makeText(this, R.string.toast_select_model_first, Toast.LENGTH_SHORT).show()
            return
        }
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.toast_input_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val speakerId = binding.etSpeakerId.text?.toString()?.toIntOrNull() ?: 0
        val speed = binding.etSpeed.text?.toString()?.toFloatOrNull() ?: 1.0f

        // 显示进度
        binding.progressBar.visibility = View.VISIBLE
        binding.btnGenerate.isEnabled = false
        binding.btnPlay.isEnabled = false
        binding.btnStop.isEnabled = false
        binding.btnSaveAudio.isEnabled = false
        Toast.makeText(this, R.string.toast_generating, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val session = OnnxEngine.createSession(model.filePath)
                val tts = TtsInference(session)
                val result = try {
                    tts.runInference(text, speakerId, speed)
                } finally {
                    tts.close()
                }
                ttsResult = result

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnGenerate.isEnabled = true
                    binding.btnPlay.isEnabled = true
                    binding.btnSaveAudio.isEnabled = true

                    // 显示音频信息
                    val durationSec = result.samples.size.toFloat() / result.sampleRate
                    binding.tvAudioInfo.text = buildString {
                        append("${getString(R.string.label_sample_rate)}: ${result.sampleRate} Hz\n")
                        append("${getString(R.string.label_audio_duration)}: %.2f 秒\n".format(durationSec))
                        append("采样点数: ${result.samples.size}")
                    }

                    Toast.makeText(this@TtsModelActivity, R.string.toast_generate_done, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnGenerate.isEnabled = true
                    Toast.makeText(
                        this@TtsModelActivity,
                        "${getString(R.string.toast_generate_failed)}: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * 播放音频
     */
    private fun playAudio() {
        val result = ttsResult ?: return

        stopAudio()

        // 使用 AudioTrack 以 STREAM 模式播放 PCM float 数据
        val sampleRate = result.sampleRate
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        audioTrack = AudioTrack(
            audioAttributes,
            audioFormat,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        // 在后台线程写入并播放音频数据
        Thread {
            try {
                audioTrack?.play()
                isPlaying = true
                // 分块写入音频数据
                val chunkSize = bufferSize
                var offset = 0
                while (offset < result.samples.size && isPlaying) {
                    val end = (offset + chunkSize).coerceAtMost(result.samples.size)
                    audioTrack?.write(result.samples, offset, end - offset, AudioTrack.WRITE_BLOCKING)
                    offset = end
                }
                // 等待播放完成
                audioTrack?.stop()
                runOnUiThread {
                    isPlaying = false
                    binding.btnStop.isEnabled = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        binding.btnStop.isEnabled = true
    }

    /**
     * 停止播放
     */
    private fun stopAudio() {
        audioTrack?.let {
            it.stop()
            it.release()
        }
        audioTrack = null
        isPlaying = false
        binding.btnStop.isEnabled = false
    }

    /**
     * 保存音频为 WAV 文件
     */
    private fun saveAudio() {
        val result = ttsResult ?: return
        val fileName = "tts_${System.currentTimeMillis()}.wav"

        lifecycleScope.launch(Dispatchers.IO) {
            val dir = File(getExternalFilesDir("audio"))
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            val success = FileUtils.savePcmToWav(result.samples, result.sampleRate, file)

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@TtsModelActivity, R.string.toast_audio_saved, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@TtsModelActivity, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
    }
}