package com.example.gemma4viewer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gemma4viewer.viewmodel.AppState

// --------------------------------------------------------------------------
// ResultContent: AppState を ResultSection の表示内容に変換する sealed class
// --------------------------------------------------------------------------

/**
 * ResultSection が表示すべきコンテンツを表す sealed class。
 *
 * AppState を UI 表示ロジックから切り離すことで、JVM ユニットテストで
 * [resolveResultContent] を純粋関数として検証可能にする。
 */
sealed class ResultContent {
    /** 推論中: CircularProgressIndicator を表示する */
    object Loading : ResultContent()

    /** 推論結果あり: [text] をスクロール可能エリアに表示する */
    data class Success(val text: String) : ResultContent()

    /** 推論エラー: [message] を日本語エラーメッセージとして表示する */
    data class Error(val message: String) : ResultContent()

    /** 空表示: ModelReady / ModelLoading / ダウンロード関連状態では何も表示しない */
    object Empty : ResultContent()
}

/**
 * AppState を ResultContent に変換する純粋関数。
 *
 * UI ロジックの中心であり、JVM ユニットテストで直接検証可能。
 */
fun resolveResultContent(appState: AppState): ResultContent = when (appState) {
    is AppState.Inferencing     -> ResultContent.Loading
    is AppState.InferenceResult -> ResultContent.Success(text = appState.text)
    is AppState.InferenceDone   -> ResultContent.Success(text = appState.text)
    is AppState.InferenceError  -> ResultContent.Error(message = "推論エラー: ${appState.message}")
    else                        -> ResultContent.Empty
}

// --------------------------------------------------------------------------
// ResultSection Composable
// --------------------------------------------------------------------------

/**
 * 推論結果テキストをスクロール可能エリアに表示するComposable。
 *
 * AppState に応じて以下のUIを表示する:
 * - [AppState.Inferencing]: CircularProgressIndicator（中央表示）
 * - [AppState.InferenceResult]: verticalScroll + テキスト表示（トークン追加ごとに更新）
 * - [AppState.InferenceError]: エラーメッセージを日本語で表示
 * - その他（ModelReady / ModelLoading / ダウンロード系）: 空表示
 *
 * Requirements: 1.3, 4.2, 4.3, 4.5
 */
@Composable
fun ResultSection(
    appState: AppState,
    modifier: Modifier = Modifier,
) {
    when (val content = resolveResultContent(appState)) {
        is ResultContent.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is ResultContent.Success -> {
            val scrollState = rememberScrollState()
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = content.text,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        is ResultContent.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    text = content.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        is ResultContent.Empty -> {
            // ModelReady / ModelLoading / ダウンロード系の状態では何も表示しない
            Box(modifier = modifier.fillMaxSize())
        }
    }
}
