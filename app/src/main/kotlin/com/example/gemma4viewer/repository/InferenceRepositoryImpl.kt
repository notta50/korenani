package com.example.gemma4viewer.repository

import android.graphics.Bitmap
import com.example.gemma4viewer.engine.LiteRtLmEngine
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

open class InferenceRepositoryImpl(
    private val engine: LiteRtLmEngine,
    private val cacheDir: File
) : InferenceRepository {

    override suspend fun initialize(modelPath: String, mmprojPath: String) {
        // mmprojPath は LiteRT-LM では使用しない（要件 5.3）
        engine.initialize(modelPath)
    }

    override fun infer(bitmap: Bitmap, prompt: String): Flow<String> = flow {
        val tempFile = createAndWriteTempFile(bitmap)
        try {
            engine.infer(tempFile.absolutePath, prompt).collect { token ->
                emit(token)
            }
        } finally {
            tempFile.delete()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun release() {
        engine.release()
    }

    /**
     * Bitmap を最大 [MAX_IMAGE_SIZE]px にリサイズし JPEG 一時ファイルとして [cacheDir] に保存する。
     * テストでオーバーライド可能（open）。
     */
    open fun createAndWriteTempFile(bitmap: Bitmap): File {
        val scaled = bitmap.scaleToMax(MAX_IMAGE_SIZE)
        val tempFile = File.createTempFile("infer_", ".jpg", cacheDir)
        FileOutputStream(tempFile).use { fos ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        }
        return tempFile
    }

    companion object {
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
