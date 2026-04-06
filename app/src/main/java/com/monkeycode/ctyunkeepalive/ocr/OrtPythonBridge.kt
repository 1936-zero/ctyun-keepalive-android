package com.monkeycode.ctyunkeepalive.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

object OrtPythonBridge {
    class SessionHandle(
        val env: OrtEnvironment,
        val session: OrtSession,
    )

    data class TensorMeta(
        val name: String,
        val shape: LongArray,
        val type: String,
    )

    data class OutputTensor(
        val shape: LongArray,
        val data: FloatArray,
    )

    @JvmStatic
    fun createSession(modelPath: String): SessionHandle {
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        options.setIntraOpNumThreads(1)
        return SessionHandle(env = env, session = env.createSession(modelPath, options))
    }

    @JvmStatic
    fun getInputs(handle: SessionHandle): kotlin.Array<TensorMeta> {
        return handle.session.inputInfo.map { (name, info) ->
            val tensorInfo = info.info as ai.onnxruntime.TensorInfo
            TensorMeta(name = name, shape = tensorInfo.shape, type = tensorInfo.type.toString())
        }.toTypedArray()
    }

    @JvmStatic
    fun getOutputs(handle: SessionHandle): kotlin.Array<TensorMeta> {
        return handle.session.outputInfo.map { (name, info) ->
            val tensorInfo = info.info as ai.onnxruntime.TensorInfo
            TensorMeta(name = name, shape = tensorInfo.shape, type = tensorInfo.type.toString())
        }.toTypedArray()
    }

    @JvmStatic
    fun getProviders(handle: SessionHandle): kotlin.Array<String> {
        return arrayOf("CPUExecutionProvider")
    }

    @JvmStatic
    fun run(
        handle: SessionHandle,
        inputName: String,
        inputData: FloatArray,
        inputShape: LongArray,
    ): kotlin.Array<OutputTensor> {
        val tensor = OnnxTensor.createTensor(handle.env, FloatBuffer.wrap(inputData), inputShape)
        tensor.use { inputTensor ->
            handle.session.run(mapOf(inputName to inputTensor)).use { result ->
                return kotlin.Array(result.size()) { index ->
                    val value = result[index]
                    val extracted = extractTensor(value)
                    OutputTensor(shape = extracted.first, data = extracted.second)
                }
            }
        }
    }

    private fun extractTensor(value: OnnxValue): Pair<LongArray, FloatArray> {
        val raw = value.value
        val shape = inferShape(raw)
        val flat = ArrayList<Float>()
        flatten(raw, flat)
        return shape to flat.toFloatArray()
    }

    private fun inferShape(value: Any?): LongArray {
        if (value == null) return longArrayOf()
        return when (value) {
            is FloatArray -> longArrayOf(value.size.toLong())
            is DoubleArray -> longArrayOf(value.size.toLong())
            is IntArray -> longArrayOf(value.size.toLong())
            else -> {
                if (!value.javaClass.isArray) {
                    longArrayOf()
                } else {
                    val len = java.lang.reflect.Array.getLength(value)
                    if (len == 0) {
                        longArrayOf(0)
                    } else {
                        longArrayOf(len.toLong()) + inferShape(java.lang.reflect.Array.get(value, 0))
                    }
                }
            }
        }
    }

    private fun flatten(value: Any?, out: MutableList<Float>) {
        when (value) {
            null -> Unit
            is FloatArray -> value.forEach { out += it }
            is DoubleArray -> value.forEach { out += it.toFloat() }
            is IntArray -> value.forEach { out += it.toFloat() }
            is Number -> out += value.toFloat()
            else -> {
                if (value.javaClass.isArray) {
                    for (index in 0 until java.lang.reflect.Array.getLength(value)) {
                        flatten(java.lang.reflect.Array.get(value, index), out)
                    }
                }
            }
        }
    }
}
