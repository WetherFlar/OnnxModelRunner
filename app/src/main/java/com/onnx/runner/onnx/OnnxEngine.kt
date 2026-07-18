package com.onnx.runner.onnx

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log

/**
 * ONNX Runtime 引擎管理器
 *
 * 负责管理 OrtEnvironment（全局单例）和创建 OrtSession（推理会话）。
 * 这是所有 ONNX 推理的基础。
 */
object OnnxEngine {

    private const val TAG = "OnnxEngine"

    /** ONNX Runtime 环境（全局单例，整个应用共享一个） */
    val environment: OrtEnvironment by lazy {
        OrtEnvironment.getEnvironment()
    }

    /**
     * 创建推理会话
     *
     * @param modelPath  模型文件路径
     * @param useNnapi   是否使用 NNAPI 硬件加速
     * @param numThreads CPU 推理线程数
     * @return OrtSession 推理会话
     */
    fun createSession(
        modelPath: String,
        useNnapi: Boolean = false,
        numThreads: Int = 4
    ): OrtSession {
        val options = OrtSession.SessionOptions().apply {
            // 设置 CPU 线程数
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setInterOpNumThreads(numThreads)
            setIntraOpNumThreads(numThreads)

            // 启用内存优化
            setMemoryPatternOptimization(true)
            setCPUArenaAllocator(true)

            // 如果启用 NNAPI，添加 NNAPI 执行提供者
            if (useNnapi) {
                try {
                    addNnapi()
                    Log.i(TAG, "NNAPI 执行提供者已启用")
                } catch (e: Exception) {
                    Log.w(TAG, "NNAPI 不可用，回退到 CPU: ${e.message}")
                }
            }
        }

        Log.i(TAG, "正在加载模型: $modelPath")
        return environment.createSession(modelPath, options)
    }

    /**
     * 从字节数组创建会话（用于从内存加载模型）
     */
    fun createSession(
        modelBytes: ByteArray,
        useNnapi: Boolean = false,
        numThreads: Int = 4
    ): OrtSession {
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setInterOpNumThreads(numThreads)
            setIntraOpNumThreads(numThreads)
            setMemoryPatternOptimization(true)
            setCPUArenaAllocator(true)
            if (useNnapi) {
                try {
                    addNnapi()
                } catch (e: Exception) {
                    Log.w(TAG, "NNAPI 不可用: ${e.message}")
                }
            }
        }
        return environment.createSession(modelBytes, options)
    }

    /**
     * 获取 ONNX Runtime 版本号
     */
    fun getVersion(): String {
        return try {
            environment.version
        } catch (e: Exception) {
            "unknown"
        }
    }
}