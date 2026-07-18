package com.onnx.runner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.onnx.runner.data.ModelInfo
import com.onnx.runner.data.ModelRepository
import com.onnx.runner.data.ModelType
import com.onnx.runner.databinding.ActivityModelManagerBinding
import com.onnx.runner.databinding.ItemModelBinding
import com.onnx.runner.util.FileUtils

/**
 * 模型管理界面
 *
 * 功能：
 * - 查看已导入的模型列表
 * - 导入新的 ONNX 模型（图像模型 / TTS 模型）
 * - 删除已导入的模型
 * - 支持选择模式：从其他界面启动时，点击模型返回选中结果
 *
 * 模型被永久保存在应用内部存储中，重启应用后依然存在。
 */
class ModelManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModelManagerBinding
    private val repository = ModelRepository.getInstance(this)
    private lateinit var adapter: ModelAdapter

    // 是否为选择模式（从其他界面启动来选择模型）
    private var isPickMode = false
    // 过滤的模型类型（选择模式下使用）
    private var filterType: ModelType? = null

    // 文件选择器：导入图像模型
    private val importImageModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importModel(uri, ModelType.IMAGE)
        }
    }

    // 文件选择器：导入 TTS 模型
    private val importTtsModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importModel(uri, ModelType.TTS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModelManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 检查是否为选择模式
        isPickMode = intent.getBooleanExtra(ImageModelActivity.EXTRA_PICK_MODE, false)
        val filterTypeName = intent.getStringExtra(ImageModelActivity.EXTRA_FILTER_TYPE)
        filterType = if (filterTypeName != null) {
            try { ModelType.valueOf(filterTypeName) } catch (e: Exception) { null }
        } else null

        // 返回按钮
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 设置 RecyclerView
        adapter = ModelAdapter(
            isPickMode = isPickMode,
            onItemClick = { model ->
                // 选择模式：返回选中的模型 ID
                val resultIntent = Intent().apply {
                    putExtra(ImageModelActivity.EXTRA_MODEL_ID, model.id)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            },
            onDeleteClick = { model -> showDeleteDialog(model) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // 导入按钮
        binding.btnImportImageModel.setOnClickListener {
            importImageModelLauncher.launch(arrayOf("*/*"))
        }
        binding.btnImportTtsModel.setOnClickListener {
            importTtsModelLauncher.launch(arrayOf("*/*"))
        }

        // 选择模式下，根据类型隐藏不相关的导入按钮
        if (isPickMode && filterType != null) {
            when (filterType) {
                ModelType.IMAGE -> binding.btnImportTtsModel.visibility = View.GONE
                ModelType.TTS -> binding.btnImportImageModel.visibility = View.GONE
            }
        }

        refreshModelList()
    }

    override fun onResume() {
        super.onResume()
        refreshModelList()
    }

    /**
     * 刷新模型列表
     */
    private fun refreshModelList() {
        val models = if (filterType != null) {
            repository.getModelsByType(filterType!!)
        } else {
            repository.getAllModels()
        }

        if (models.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            // 选择模式下更新提示文字
            if (isPickMode && filterType != null) {
                binding.tvEmpty.text = when (filterType) {
                    ModelType.IMAGE -> "还没有图像处理模型\n请先导入 .onnx 模型文件"
                    ModelType.TTS -> "还没有 TTS 模型\n请先导入 .onnx 模型文件"
                }
            }
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            adapter.updateModels(models)
        }
    }

    /**
     * 导入模型
     */
    private fun importModel(uri: Uri, type: ModelType) {
        val fileName = FileUtils.getFileName(this, uri)
        if (!fileName.endsWith(".onnx", ignoreCase = true)) {
            Toast.makeText(this, R.string.toast_invalid_model, Toast.LENGTH_SHORT).show()
            return
        }

        // 在后台线程复制文件
        Thread {
            val tempFile = FileUtils.uriToFile(this, uri, ".onnx")
            if (tempFile != null) {
                val modelInfo = repository.importModel(tempFile, type)
                tempFile.delete() // 清理临时文件

                runOnUiThread {
                    if (modelInfo != null) {
                        Toast.makeText(this, R.string.toast_model_imported, Toast.LENGTH_SHORT).show()
                        refreshModelList()
                    } else {
                        Toast.makeText(this, R.string.toast_import_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, R.string.toast_import_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /**
     * 显示删除确认对话框
     */
    private fun showDeleteDialog(model: ModelInfo) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, model.name))
            .setPositiveButton(R.string.btn_confirm) { _, _ ->
                if (repository.deleteModel(model)) {
                    Toast.makeText(this, R.string.toast_model_deleted, Toast.LENGTH_SHORT).show()
                    refreshModelList()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // ===== RecyclerView 适配器 =====

    private inner class ModelAdapter(
        private val isPickMode: Boolean,
        private val onItemClick: (ModelInfo) -> Unit,
        private val onDeleteClick: (ModelInfo) -> Unit
    ) : RecyclerView.Adapter<ModelAdapter.ViewHolder>() {

        private val models = mutableListOf<ModelInfo>()

        fun updateModels(newModels: List<ModelInfo>) {
            models.clear()
            models.addAll(newModels)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemModelBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(models[position])
        }

        override fun getItemCount() = models.size

        inner class ViewHolder(private val binding: ItemModelBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(model: ModelInfo) {
                binding.tvModelName.text = model.name
                binding.tvModelType.text = model.type.displayName
                binding.tvModelSize.text = model.formattedSize

                // 根据类型设置图标背景色
                binding.ivModelIcon.setBackgroundColor(
                    when (model.type) {
                        ModelType.IMAGE -> getColor(R.color.primary_light)
                        ModelType.TTS -> getColor(R.color.accent)
                    }
                )

                // 选择模式下隐藏删除按钮，点击整行选择
                if (isPickMode) {
                    binding.btnDelete.visibility = View.GONE
                    binding.root.setOnClickListener { onItemClick(model) }
                } else {
                    binding.btnDelete.visibility = View.VISIBLE
                    binding.btnDelete.setOnClickListener { onDeleteClick(model) }
                }
            }
        }
    }
}