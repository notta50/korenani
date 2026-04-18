package com.example.gemma4viewer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 推論結果テキストをスクロール可能エリアに表示するComposable。
 *
 * 現在はプレースホルダー実装。テキスト表示・スクロール・エラー表示は Task 3.2 で実装される。
 */
@Composable
fun ResultSection(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "推論結果（準備中）")
    }
}
