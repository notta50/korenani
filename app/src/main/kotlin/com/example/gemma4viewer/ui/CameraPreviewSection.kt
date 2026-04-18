package com.example.gemma4viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 権限状態をUIメッセージに変換する純粋関数。JVMユニットテスト可能。
 * 権限なし → "設定から..." メッセージ、権限あり → null（カメラUIを表示）
 */
fun resolveCameraPermissionMessage(hasPermission: Boolean): String? =
    if (!hasPermission) "カメラ権限が必要です。設定からカメラ権限を許可してください。" else null

/**
 * カメラプレビューと撮影ボタンを表示するComposable。
 *
 * - hasCameraPermission=false: 権限拒否時の説明UIと権限要求ボタンを表示
 * - hasCameraPermission=true: カメラプレビュー（Task 7.2で実装）
 */
@Composable
fun CameraPreviewSection(
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deniedMessage = resolveCameraPermissionMessage(hasCameraPermission)
    if (deniedMessage != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                Text(
                    text = deniedMessage,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRequestPermission) {
                    Text(text = "権限を許可する")
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "カメラプレビュー（準備中）")
        }
    }
}
