package com.example.gemma4viewer.repository

import com.example.gemma4viewer.ModelConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class ModelRepositoryImpl(
    private val filesDir: File,
    private val modelUrl: String = ModelConfig.MODEL_URL,
    private val mmprojUrl: String = ModelConfig.MMPROJ_URL,
    private val httpClient: OkHttpClient = OkHttpClient()
) : ModelRepository {

    override fun isModelReady(): Boolean {
        val modelFile = File(filesDir, ModelConfig.MODEL_FILENAME)
        val mmprojFile = File(filesDir, ModelConfig.MMPROJ_FILENAME)
        return modelFile.exists() && mmprojFile.exists()
    }

    override fun downloadModels(): Flow<DownloadState> = flow {
        // model.gguf を先にダウンロード
        var failed = false
        downloadFile(
            url = modelUrl,
            file = File(filesDir, ModelConfig.MODEL_FILENAME),
            label = "モデルファイル"
        ).collect { state ->
            emit(state)
            if (state is DownloadState.Failed) {
                failed = true
            }
        }
        if (failed) return@flow

        // mmproj.gguf をダウンロード
        downloadFile(
            url = mmprojUrl,
            file = File(filesDir, ModelConfig.MMPROJ_FILENAME),
            label = "mmprojファイル"
        ).collect { state ->
            emit(state)
            if (state is DownloadState.Failed) {
                failed = true
            }
        }
        if (failed) return@flow

        emit(DownloadState.Finished)
    }

    /**
     * 単一ファイルのレジューム対応ダウンロード。
     * Progress(percent, label) を随時 emit し、エラー時は Failed を emit して終了する。
     */
    private fun downloadFile(url: String, file: File, label: String): Flow<DownloadState> = flow {
        val existingLength = if (file.exists() && file.length() > 0) file.length() else 0L

        val requestBuilder = Request.Builder().url(url)
        if (existingLength > 0) {
            requestBuilder.addHeader("Range", "bytes=$existingLength-")
        }

        val response = try {
            httpClient.newCall(requestBuilder.build()).execute()
        } catch (e: Exception) {
            emit(DownloadState.Failed(e))
            return@flow
        }

        response.use { resp ->
            val code = resp.code
            // 200 でも 206 でもないエラーコード
            if (code != 200 && code != 206) {
                emit(
                    DownloadState.Failed(
                        Exception("HTTPエラー: $code")
                    )
                )
                return@flow
            }

            val body = resp.body ?: run {
                emit(DownloadState.Failed(Exception("レスポンスボディが空です")))
                return@flow
            }

            // 206 (Partial Content) → append mode。200 (Range非対応) → 最初から書き直し
            val appendMode = (code == 206)
            val contentLength = body.contentLength() // -1 の場合は不明

            // 合計サイズ: 既存バイト数 + 残りのコンテンツ長
            val totalLength = if (contentLength >= 0) {
                if (appendMode) existingLength + contentLength else contentLength
            } else {
                -1L
            }

            val inputStream = body.byteStream()
            FileOutputStream(file, appendMode).use { output ->
                val buffer = ByteArray(8 * 1024)
                var downloaded = if (appendMode) existingLength else 0L

                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read

                    val percent = if (totalLength > 0) {
                        ((downloaded * 100L) / totalLength).toInt().coerceIn(0, 100)
                    } else {
                        -1
                    }
                    emit(DownloadState.Progress(percent = percent, label = label))
                }
                output.flush()
            }
        }
    }

    override fun getModelPath(): String {
        return File(filesDir, ModelConfig.MODEL_FILENAME).absolutePath
    }

    override fun getMmprojPath(): String {
        return File(filesDir, ModelConfig.MMPROJ_FILENAME).absolutePath
    }
}
