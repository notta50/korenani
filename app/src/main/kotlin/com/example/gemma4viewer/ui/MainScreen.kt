package com.example.gemma4viewer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gemma4viewer.viewmodel.AppState
import com.example.gemma4viewer.viewmodel.MainViewModel

// --------------------------------------------------------------------------
// ScreenMode: AppState を UI レイアウト選択用の enum に変換する
// --------------------------------------------------------------------------

enum class ScreenMode {
    /** DownloadRequired / Downloading / DownloadFailed — 全面ダウンロードUI */
    DOWNLOAD,

    /** ModelLoading — 全面ローディングインジケータ */
    LOADING,

    /** ModelReady / Inferencing / InferenceResult / InferenceError — 上下50:50分割 */
    CAMERA_SPLIT,
}

/**
 * AppState を ScreenMode に変換する純粋関数。
 * UI ロジックの中心であり、JVM ユニットテストで直接検証可能。
 */
fun resolveScreenMode(appState: AppState): ScreenMode = when (appState) {
    is AppState.DownloadRequired -> ScreenMode.DOWNLOAD
    is AppState.Downloading      -> ScreenMode.DOWNLOAD
    is AppState.DownloadFailed   -> ScreenMode.DOWNLOAD
    is AppState.ModelLoading     -> ScreenMode.LOADING
    is AppState.ModelReady       -> ScreenMode.CAMERA_SPLIT
    is AppState.Inferencing      -> ScreenMode.CAMERA_SPLIT
    is AppState.InferenceResult  -> ScreenMode.CAMERA_SPLIT
    is AppState.InferenceError   -> ScreenMode.CAMERA_SPLIT
}

// --------------------------------------------------------------------------
// MainScreen
// --------------------------------------------------------------------------

/**
 * ルートComposable。AppState に応じて以下の3つのレイアウトを切り替える:
 * - DOWNLOAD: 全面ダウンロードUI（DownloadRequired / Downloading / DownloadFailed）
 * - LOADING: 全面ローディングインジケータ（ModelLoading）
 * - CAMERA_SPLIT: 上下50:50分割（ModelReady / Inferencing / InferenceResult / InferenceError）
 */
@Composable
fun MainScreen(
    appState: AppState,
    viewModel: MainViewModel,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (resolveScreenMode(appState)) {
        ScreenMode.DOWNLOAD ->
            DownloadContent(
                appState = appState,
                onStartDownload = viewModel::onStartDownload,
                onRetryDownload = viewModel::onRetryDownload,
                modifier = modifier,
            )

        ScreenMode.LOADING ->
            LoadingContent(modifier = modifier)

        ScreenMode.CAMERA_SPLIT ->
            SplitContent(
                appState = appState,
                hasCameraPermission = hasCameraPermission,
                onRequestCameraPermission = onRequestCameraPermission,
                modifier = modifier,
            )
    }
}

// --------------------------------------------------------------------------
// ダウンロードUI（全面表示）
// --------------------------------------------------------------------------

@Composable
private fun DownloadContent(
    appState: AppState,
    onStartDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (appState) {
                is AppState.DownloadRequired -> {
                    Text(text = "Gemma 4 E2B モデルのダウンロードが必要です。")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onStartDownload) {
                        Text(text = "ダウンロード開始")
                    }
                }

                is AppState.Downloading -> {
                    Text(text = appState.label)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (appState.progress >= 0) {
                        LinearProgressIndicator(
                            progress = { appState.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "${appState.progress}%")
                    } else {
                        // chunked（サイズ不明）の場合は不定プログレス
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "ダウンロード中...")
                    }
                }

                is AppState.DownloadFailed -> {
                    Text(text = "ダウンロードに失敗しました: ${appState.error}")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRetryDownload) {
                        Text(text = "再試行")
                    }
                }

                else -> {
                    // resolveScreenMode によってここには到達しない
                }
            }
        }
    }
}

// --------------------------------------------------------------------------
// ローディングUI（全面表示）
// --------------------------------------------------------------------------

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "モデルを初期化中...")
        }
    }
}

// --------------------------------------------------------------------------
// 上下50:50分割レイアウト
// --------------------------------------------------------------------------

@Composable
private fun SplitContent(
    appState: AppState,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(0.5f)) {
            CameraPreviewSection(
                hasCameraPermission = hasCameraPermission,
                onRequestPermission = onRequestCameraPermission,
            )
        }
        Box(modifier = Modifier.weight(0.5f)) {
            ResultSection(appState = appState)
        }
    }
}
