package com.example.gemma4viewer.repository

import android.graphics.Bitmap
import com.example.gemma4viewer.engine.LlamaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import kotlin.math.min

internal fun computeNThreads(): Int =
    min(Runtime.getRuntime().availableProcessors(), 8)

class InferenceRepositoryImpl(
    private val engine: LlamaEngine = LlamaEngine()
) : InferenceRepository {

    override suspend fun initialize(modelPath: String, mmprojPath: String) =
        withContext(Dispatchers.IO) {
            val nThreads = computeNThreads()
            if (engine.nativeLoad(modelPath) != 0) {
                error("nativeLoad failed: $modelPath")
            }
            if (engine.nativePrepare(nCtx = 4096, nThreads = nThreads) != 0) {
                error("nativePrepare failed")
            }
            if (engine.nativeLoadMmproj(mmprojPath) != 0) {
                error("nativeLoadMmproj failed: $mmprojPath")
            }
        }

    override fun infer(bitmap: Bitmap, prompt: String): Flow<String> =
        emptyFlow() // Task 8.2で実装

    override suspend fun release() = withContext(Dispatchers.IO) {
        engine.nativeUnload()
    }
}
