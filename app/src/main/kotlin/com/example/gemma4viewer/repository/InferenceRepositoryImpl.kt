package com.example.gemma4viewer.repository

import android.graphics.Bitmap
import com.example.gemma4viewer.engine.LlamaEngine
import com.example.gemma4viewer.util.ImageUtils.toRgbByteArray
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

internal fun computeNThreads(): Int =
    min(Runtime.getRuntime().availableProcessors(), 8)

/**
 * Gemma 4マルチモーダルプロンプトテンプレートを構築する純粋関数。
 * mediaMarkerはmtmd_default_marker()が返す文字列（例: "<__media__>"）を渡す。
 */
internal fun buildGemma4Prompt(userText: String, mediaMarker: String): String =
    "<start_of_turn>user\n$mediaMarker\n$userText<end_of_turn>\n<start_of_turn>model\n"

class InferenceRepositoryImpl(
    private val nativeLibDir: String,
    private val engine: LlamaEngine = LlamaEngine()
) : InferenceRepository {

    override suspend fun initialize(modelPath: String, mmprojPath: String) =
        withContext(Dispatchers.IO) {
            engine.nativeInitBackend(nativeLibDir)
            val nThreads = computeNThreads()
            if (engine.nativeLoad(modelPath) != 0) {
                error("nativeLoad failed: $modelPath")
            }
            if (engine.nativePrepare(nCtx = 1024, nThreads = nThreads) != 0) {
                error("nativePrepare failed")
            }
            if (engine.nativeLoadMmproj(mmprojPath) != 0) {
                error("nativeLoadMmproj failed: $mmprojPath")
            }
        }

    override fun infer(bitmap: Bitmap, prompt: String): Flow<String> = flow {
        val scaled = bitmap.scaleToMax(MAX_IMAGE_SIZE)
        val rgb = with(com.example.gemma4viewer.util.ImageUtils) { scaled.toRgbByteArray() }
        val fullPrompt = buildGemma4Prompt(prompt, MEDIA_MARKER)

        if (engine.nativeProcessImageTurn(rgb, scaled.width, scaled.height, fullPrompt) != 0) {
            error("nativeProcessImageTurn failed")
        }

        while (true) {
            val token = engine.nativeGenerateNextToken()
            if (token.isEmpty()) break
            emit(token)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun release() = withContext(Dispatchers.IO) {
        engine.nativeUnload()
    }

    companion object {
        private const val MEDIA_MARKER = "<__media__>"
        private const val MAX_IMAGE_SIZE = 336

        private fun Bitmap.scaleToMax(maxPx: Int): Bitmap {
            val scale = min(maxPx.toFloat() / width, maxPx.toFloat() / height)
            if (scale >= 1f) return this
            val w = (width * scale).toInt().coerceAtLeast(1)
            val h = (height * scale).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(this, w, h, true)
        }
    }
}
