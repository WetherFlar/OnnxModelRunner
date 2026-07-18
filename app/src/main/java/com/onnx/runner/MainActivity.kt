package com.onnx.runner

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.onnx.runner.databinding.ActivityMainBinding
import com.onnx.runner.onnx.OnnxEngine

/**
 * 主界面
 *
 * 应用的入口界面，提供三个功能入口：
 * 1. 图像处理模型
 * 2. TTS 语音合成模型
 * 3. 模型管理
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 显示 ONNX Runtime 版本
        binding.tvOrtVersion.text = "ONNX Runtime ${OnnxEngine.getVersion()}"

        // 图像处理模型
        binding.cardImageModel.setOnClickListener {
            startActivity(Intent(this, ImageModelActivity::class.java))
        }

        // TTS 模型
        binding.cardTtsModel.setOnClickListener {
            startActivity(Intent(this, TtsModelActivity::class.java))
        }

        // 模型管理
        binding.cardModelManager.setOnClickListener {
            startActivity(Intent(this, ModelManagerActivity::class.java))
        }
    }
}