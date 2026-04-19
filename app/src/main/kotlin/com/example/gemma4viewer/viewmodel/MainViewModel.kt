package com.example.gemma4viewer.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gemma4viewer.repository.DownloadState
import com.example.gemma4viewer.repository.InferenceRepository
import com.example.gemma4viewer.repository.ModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val modelRepo: ModelRepository,
    private val inferenceRepo: InferenceRepository
) : ViewModel() {

    private val _appState: MutableStateFlow<AppState> =
        MutableStateFlow(AppState.DownloadRequired)

    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    /** 実行中の推論ジョブ。キャンセル操作のために保持する。 */
    private var inferenceJob: Job? = null

    fun onAppStart() {
        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) { modelRepo.isModelReady() }
            if (ready) {
                loadModel()
            } else {
                _appState.value = AppState.DownloadRequired
            }
        }
    }

    fun onStartDownload() {
        viewModelScope.launch {
            runDownloadAndLoad()
        }
    }

    fun onRetryDownload() {
        viewModelScope.launch {
            runDownloadAndLoad()
        }
    }

    fun onCapture(bitmap: Bitmap) {
        inferenceJob = viewModelScope.launch {
            _capturedBitmap.value = bitmap
            _appState.value = AppState.Inferencing
            try {
                var accumulatedText = ""
                inferenceRepo.infer(bitmap, DEFAULT_PROMPT).collect { token ->
                    accumulatedText += token
                    _appState.value = AppState.InferenceResult(accumulatedText)
                }
                // 推論完了: 写真と結果テキストをそのまま表示する
                _appState.value = AppState.InferenceDone(accumulatedText)
            } catch (e: Exception) {
                // CancellationException は必ず再スローしてコルーチンビルドの従来動作を守る
                if (e is kotlinx.coroutines.CancellationException) throw e
                _appState.value = AppState.InferenceError(e.message ?: "推論エラーが発生しました。")
            } finally {
                inferenceJob = null
            }
        }
    }

    /** 『挊り込み中の推論』を中断し、途中結果を表示したまま InferenceDone 状態に遷移する */
    fun onCancelInference() {
        inferenceJob?.cancel()
        inferenceJob = null
        // キャンセル時点で溜まっているテキストを取得して InferenceDone に遷移
        // capturedBitmap はそのまま保持し、静止画と「カメラ起動」ボタンを表示し続ける
        val partialText = when (val state = _appState.value) {
            is AppState.InferenceResult -> state.text
            else -> ""
        }
        _appState.value = AppState.InferenceDone(partialText)
    }

    /** 『カメラ起動』ボタン按下: 写真と結果をクリアしてカメラモードに戻る */
    fun onReturnToCamera() {
        _capturedBitmap.value = null
        _appState.value = AppState.ModelReady
    }

    private suspend fun runDownloadAndLoad() {
        Log.d(TAG, "runDownloadAndLoad: 開始")
        try {
            modelRepo.downloadModels().collect { state ->
                when (state) {
                    is DownloadState.Progress -> {
                        _appState.value = AppState.Downloading(
                            progress = state.percent,
                            label = state.label
                        )
                    }
                    is DownloadState.Finished -> {
                        Log.i(TAG, "runDownloadAndLoad: ダウンロード完了 → モデルロード開始")
                        loadModel()
                    }
                    is DownloadState.Failed -> {
                        Log.e(TAG, "runDownloadAndLoad: DownloadFailed: ${state.error.message}", state.error)
                        _appState.value = AppState.DownloadFailed(
                            state.error.message ?: "ダウンロードに失敗しました"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "runDownloadAndLoad: 予期しない例外: ${e.javaClass.simpleName}: ${e.message}", e)
            _appState.value = AppState.DownloadFailed(e.message ?: "ダウンロードに失敗しました")
        }
        Log.d(TAG, "runDownloadAndLoad: 終了")
    }

    private suspend fun loadModel() {
        _appState.value = AppState.ModelLoading
        try {
            withContext(Dispatchers.IO) {
                inferenceRepo.initialize(
                    modelRepo.getModelPath(),
                    modelRepo.getMmprojPath()
                )
            }
            _appState.value = AppState.ModelReady
        } catch (e: Exception) {
            _appState.value = AppState.InferenceError(e.message ?: "モデルロードに失敗しました")
        }
    }

    class Factory(
        private val modelRepo: ModelRepository,
        private val inferenceRepo: InferenceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(modelRepo, inferenceRepo) as T
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val DEFAULT_PROMPT =
            "この画像に写っているものを日本語で詳しく説明してください。"
    }
}
