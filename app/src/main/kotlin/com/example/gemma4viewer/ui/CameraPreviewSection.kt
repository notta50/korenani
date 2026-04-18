package com.example.gemma4viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * カメラプレビューと撮影ボタンを表示するComposable。
 *
 * 現在はプレースホルダー実装。CameraX 統合は Task 7.x で実装される。
 */
@Composable
fun CameraPreviewSection(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "カメラプレビュー（準備中）")
    }
}
