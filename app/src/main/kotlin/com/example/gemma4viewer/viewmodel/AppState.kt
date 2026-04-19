package com.example.gemma4viewer.viewmodel

sealed class AppState {
    object DownloadRequired : AppState()
    data class Downloading(val progress: Int, val label: String) : AppState()
    data class DownloadFailed(val error: String) : AppState()
    object ModelLoading : AppState()
    object ModelReady : AppState()
    object Inferencing : AppState()
    data class InferenceResult(val text: String) : AppState()
    data class InferenceError(val message: String) : AppState()
    /** 推論完了: 写真と結果テキストを表示したまま待機する状態 */
    data class InferenceDone(val text: String) : AppState()
}
