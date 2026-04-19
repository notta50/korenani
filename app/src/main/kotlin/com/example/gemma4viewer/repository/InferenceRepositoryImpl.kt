package com.example.gemma4viewer.repository

import android.graphics.Bitmap
import com.example.gemma4viewer.engine.LiteRtLmEngine
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

open class InferenceRepositoryImpl(
    private val engine: LiteRtLmEngine,
    private val cacheDir: File
) : InferenceRepository {

    private val engineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    override suspend fun initialize(modelPath: String, mmprojPath: String) {
        // mmprojPath は LiteRT-LM では使用しない（要件 5.3）
        withContext(engineDispatcher) {
            engine.initialize(modelPath)
        }
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
    }.flowOn(engineDispatcher)

    override suspend fun release() {
        withContext(engineDispatcher) {
            engine.release()
        }
        engineDispatcher.close()
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
        private const val MAX_IMAGE_SIZE = 896

        private fun Bitmap.scaleToMax(maxPx: Int): Bitmap {
            val scale = min(maxPx.toFloat() / width, maxPx.toFloat() / height)
            val targetW = if (scale >= 1f) width else (width * scale).toInt()
            val targetH = if (scale >= 1f) height else (height * scale).toInt()

            var alignedW = (targetW / 16) * 16
            var alignedH = (targetH / 16) * 16
            if (alignedW == 0) alignedW = 16
            if (alignedH == 0) alignedH = 16

            if (alignedW == width && alignedH == height) return this
            return Bitmap.createScaledBitmap(this, alignedW, alignedH, true)
        }
    }
}
