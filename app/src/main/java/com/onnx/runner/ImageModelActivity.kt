package com.onnx.runner

import ai.onnxruntime.TensorInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.onnx.runner.data.ModelInfo
import com.onnx.runner.data.ModelRepository
import com.onnx.runner.data.ModelType
import com.onnx.runner.databinding.ActivityImageModelBinding
import com.onnx.runner.onnx.ImageInference
import com.onnx.runner.onnx.OnnxEngine
import com.onnx.runner.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * 图像处理模型界面
 *
 * 功能：
 * - 选择已导入的图像处理模型
 * - 选择输入图片
 * - 运行推理（图到图处理）
 * - 查看输出图片
 * - 保存输出图片
 *
 * 支持的模型类型：超分辨率、去噪、风格迁移、图像增强等图到图模型。
 */
class ImageModelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageModelBinding
    private val repository = ModelRepository.getInstance(this)

    private var selectedModel: ModelInfo? = null
    private var inputBitmap: Bitmap? = null
    private var outputBitmap: Bitmap? = null
    private var imageInference: ImageInference? = null

    // 选择模型
    private val selectModelLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val modelId = result.data?.getStringExtra(EXTRA_MODEL_ID)
            if (modelId != null) {
                selectedModel = repository.getModelById(modelId)
                selectedModel?.let { model ->
                    binding.tvModelName.text = model.name
                    binding.tvModelDetails.text = "类型: ${model.type.displayName}  大小: ${model.formattedSize}"
                    // 预加载模型信息
                    loadModelInfo(model)
                }
            }
        }
    }

    // 选择图片
    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            loadInputImage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageModelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        // 选择模型按钮
        binding.btnSelectModel.setOnClickListener {
            val intent = Intent(this, ModelManagerActivity::class.java).apply {
                putExtra(EXTRA_FILTER_TYPE, ModelType.IMAGE.name)
                putExtra(EXTRA_PICK_MODE, true)
            }
            selectModelLauncher.launch(intent)
        }

        // 选择图片按钮
        binding.btnSelectImage.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        // 运行推理按钮
        binding.btnRunInference.setOnClickListener {
            runInference()
        }

        // 保存结果按钮
        binding.btnSaveResult.setOnClickListener {
            saveResult()
        }
    }

    /**
     * 加载模型信息（在后台线程中读取模型输入输出形状）
     */
    private fun loadModelInfo(model: ModelInfo) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val session = OnnxEngine.createSession(model.filePath, binding.switchNnapi.isChecked)
                val inputName = session.inputNames.firstOrNull() ?: "unknown"
                val inputShape = (session.inputInfo[inputName]?.info as? TensorInfo)
                    ?.shape?.joinToString(" x ") ?: "非张量输入"
                val outputName = session.outputNames.firstOrNull() ?: "unknown"
                val outputShape = (session.outputInfo[outputName]?.info as? TensorInfo)
                    ?.shape?.joinToString(" x ") ?: "非张量输出"
                session.close()

                withContext(Dispatchers.Main) {
                    binding.tvModelDetails.text = buildString {
                        append("类型: ${model.type.displayName}  大小: ${model.formattedSize}\n")
                        append("输入: $inputName [$inputShape]\n")
                        append("输出: $outputName [$outputShape]")
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
     * 加载输入图片
     */
    private fun loadInputImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    inputBitmap = bitmap
                    withContext(Dispatchers.Main) {
                        binding.ivInputImage.setImageBitmap(bitmap)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ImageModelActivity, "图片加载失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ImageModelActivity, "图片加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 运行推理
     */
    private fun runInference() {
        val model = selectedModel
        val bitmap = inputBitmap

        if (model == null) {
            Toast.makeText(this, R.string.toast_select_model_first, Toast.LENGTH_SHORT).show()
            return
        }
        if (bitmap == null) {
            Toast.makeText(this, R.string.toast_select_image_first, Toast.LENGTH_SHORT).show()
            return
        }

        // 显示进度条
        binding.progressBar.visibility = View.VISIBLE
        binding.btnRunInference.isEnabled = false
        binding.tvInferenceTime.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                // 创建推理会话
                val useNnapi = binding.switchNnapi.isChecked
                val session = OnnxEngine.createSession(model.filePath, useNnapi)
                val inference = ImageInference(session)
                imageInference = inference

                // 运行推理；无论成功、失败或协程取消，都释放会话资源。
                val result = try {
                    inference.runInference(bitmap)
                } finally {
                    inference.close()
                    if (imageInference === inference) imageInference = null
                }
                outputBitmap = result.outputBitmap

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRunInference.isEnabled = true

                    // 显示输出图片
                    binding.ivOutputImage.setImageBitmap(result.outputBitmap)

                    // 显示推理耗时
                    binding.tvInferenceTime.visibility = View.VISIBLE
                    binding.tvInferenceTime.text = buildString {
                        append("${getString(R.string.label_inference_time)}: ${result.inferenceTimeMs} ms\n")
                        append("输入形状: ${result.inputShape}\n")
                        append("输出形状: ${result.outputShape}")
                    }

                    // 启用保存按钮
                    binding.btnSaveResult.isEnabled = true

                    Toast.makeText(this@ImageModelActivity, R.string.toast_inference_done, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRunInference.isEnabled = true
                    Toast.makeText(
                        this@ImageModelActivity,
                        "${getString(R.string.toast_inference_failed)}: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * 保存结果图片
     */
    private fun saveResult() {
        val bitmap = outputBitmap ?: return
        val fileName = "output_${System.currentTimeMillis()}"

        lifecycleScope.launch(Dispatchers.IO) {
            val success = FileUtils.saveBitmapToGallery(this@ImageModelActivity, bitmap, fileName)
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@ImageModelActivity, R.string.toast_image_saved, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ImageModelActivity, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        imageInference?.close()
    }

    companion object {
        const val EXTRA_MODEL_ID = "extra_model_id"
        const val EXTRA_FILTER_TYPE = "extra_filter_type"
        const val EXTRA_PICK_MODE = "extra_pick_mode"
    }
}