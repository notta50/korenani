package com.example.gemma4viewer.repository

import android.util.Log
import com.example.gemma4viewer.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ModelRepositoryImpl(
    private val filesDir: File,
    private val modelUrl: String = ModelConfig.MODEL_URL,
    private val mmprojUrl: String = ModelConfig.MMPROJ_URL,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .build()
) : ModelRepository {

    companion object {
        private const val TAG = "ModelRepository"
    }

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
    }.flowOn(Dispatchers.IO)

    /**
     * 単一ファイルのレジューム対応ダウンロード。
     * Progress(percent, label) を随時 emit し、エラー時は Failed を emit して終了する。
     */
    private fun downloadFile(url: String, file: File, label: String): Flow<DownloadState> = flow {
        val existingLength = if (file.exists() && file.length() > 0) file.length() else 0L
        Log.d(TAG, "[$label] 開始: url=$url, 既存サイズ=${existingLength}B, 保存先=${file.absolutePath}")

        val requestBuilder = Request.Builder().url(url)
        if (existingLength > 0) {
            requestBuilder.addHeader("Range", "bytes=$existingLength-")
            Log.d(TAG, "[$label] レジュームリクエスト: Range=bytes=$existingLength-")
        }

        val response = try {
            httpClient.newCall(requestBuilder.build()).execute()
        } catch (e: Exception) {
            Log.e(TAG, "[$label] 接続失敗: ${e.javaClass.simpleName}: ${e.message}", e)
            emit(DownloadState.Failed(e))
            return@flow
        }

        response.use { resp ->
            val code = resp.code
            Log.d(TAG, "[$label] HTTPレスポンス: $code, Content-Length=${resp.header("Content-Length")}, Content-Type=${resp.header("Content-Type")}")
            Log.d(TAG, "[$label] 最終URL(リダイレクト後): ${resp.request.url}")

            if (code == 416) {
                Log.i(TAG, "[$label] HTTP 416: 既にダウンロード済み (${file.length()}B) → スキップ")
                emit(DownloadState.Progress(percent = 100, label = label))
                return@flow
            }

            if (code != 200 && code != 206) {
                val body = resp.body?.string()?.take(200) ?: "(body無し)"
                Log.e(TAG, "[$label] HTTPエラー: $code, body=$body")
                emit(DownloadState.Failed(Exception("HTTPエラー: $code")))
                return@flow
            }

            val body = resp.body ?: run {
                Log.e(TAG, "[$label] レスポンスボディが null")
                emit(DownloadState.Failed(Exception("レスポンスボディが空です")))
                return@flow
            }

            val appendMode = (code == 206)
            val contentLength = body.contentLength()
            val totalLength = if (contentLength >= 0) {
                if (appendMode) existingLength + contentLength else contentLength
            } else {
                -1L
            }
            Log.d(TAG, "[$label] appendMode=$appendMode, contentLength=${contentLength}B, totalLength=${totalLength}B")

            try {
                val inputStream = body.byteStream()
                FileOutputStream(file, appendMode).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var downloaded = if (appendMode) existingLength else 0L
                    var lastLoggedPercent = -1

                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read

                        val percent = if (totalLength > 0) {
                            ((downloaded * 100L) / totalLength).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }
                        // 10%ごとにログ出力
                        if (percent >= 0 && percent / 10 != lastLoggedPercent / 10) {
                            Log.d(TAG, "[$label] 進捗: $percent% (${downloaded}/${totalLength}B)")
                            lastLoggedPercent = percent
                        }
                        emit(DownloadState.Progress(percent = percent, label = label))
                    }
                    output.flush()
                    Log.i(TAG, "[$label] ダウンロード完了: ${downloaded}B → ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[$label] ストリーム読み込みエラー: ${e.javaClass.simpleName}: ${e.message}", e)
                emit(DownloadState.Failed(e))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun getModelPath(): String {
        return File(filesDir, ModelConfig.MODEL_FILENAME).absolutePath
    }

    override fun getMmprojPath(): String {
        return File(filesDir, ModelConfig.MMPROJ_FILENAME).absolutePath
    }
}
