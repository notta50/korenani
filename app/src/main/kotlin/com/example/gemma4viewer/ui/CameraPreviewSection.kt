package com.example.gemma4viewer.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.gemma4viewer.viewmodel.AppState

/**
 * 権限状態をUIメッセージに変換する純粋関数。JVMユニットテスト可能。
 */
fun resolveCameraPermissionMessage(hasPermission: Boolean): String? =
    if (!hasPermission) "カメラ権限が必要です。設定からカメラ権限を許可してください。" else null

/**
 * AppState から撮影ボタンの有効/無効を決める純粋関数。JVMユニットテスト可能。
 * 推論中（Inferencing）はボタンを無効にして多重撮影を防ぐ。
 */
fun resolveIsCaptureEnabled(appState: AppState): Boolean =
    appState !is AppState.Inferencing

/**
 * カメラプレビューと撮影ボタンを表示するComposable。
 *
 * - hasCameraPermission=false: 権限拒否時の説明UIと権限要求ボタンを表示
 * - hasCameraPermission=true: CameraXリアルタイムプレビュー + 撮影ボタン
 */
@Composable
fun CameraPreviewSection(
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    appState: AppState,
    capturedBitmap: Bitmap?,
    onCapture: (Bitmap) -> Unit,
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
    } else if (capturedBitmap != null && appState is AppState.Inferencing) {
        // 推論中は撮影した静止画を表示
        Box(modifier = modifier.fillMaxSize()) {
            Image(
                bitmap = capturedBitmap.asImageBitmap(),
                contentDescription = "解析中の画像",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
        }
    } else {
        CameraXPreviewContent(
            appState = appState,
            onCapture = onCapture,
            modifier = modifier,
        )
    }
}

@Composable
internal fun CameraXPreviewContent(
    appState: AppState,
    onCapture: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val surfaceRequest = remember { mutableStateOf<SurfaceRequest?>(null) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider { req ->
                    surfaceRequest.value = req
                }
            }
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        surfaceRequest.value?.let { req ->
            CameraXViewfinder(
                surfaceRequest = req,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Button(
            onClick = {
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(imageProxy: ImageProxy) {
                            val rotation = imageProxy.imageInfo.rotationDegrees
                            val raw: Bitmap = imageProxy.toBitmap()
                            imageProxy.close()
                            val bitmap = if (rotation != 0) {
                                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                            } else raw
                            onCapture(bitmap)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            // 撮影エラーは無視（再撮影可能）
                        }
                    }
                )
            },
            enabled = resolveIsCaptureEnabled(appState),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        ) {
            Text(text = "撮影")
        }
    }
}
