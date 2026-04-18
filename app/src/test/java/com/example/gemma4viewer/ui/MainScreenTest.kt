package com.example.gemma4viewer.ui

import com.example.gemma4viewer.viewmodel.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MainScreen のロジックユニットテスト。
 *
 * Compose UI テストはインストルメントテストが必要なため、ここでは
 * MainScreen のレイアウト選択ロジックを純粋関数として抽出し、JVM で検証する。
 */
class MainScreenTest {

    // -----------------------------------------------------------------------
    // resolveScreenMode: AppState → ScreenMode マッピングのテスト
    // -----------------------------------------------------------------------

    @Test
    fun downloadRequired_mapsToDownloadMode() {
        val mode = resolveScreenMode(AppState.DownloadRequired)
        assertEquals(ScreenMode.DOWNLOAD, mode)
    }

    @Test
    fun downloading_mapsToDownloadMode() {
        val mode = resolveScreenMode(AppState.Downloading(progress = 50, label = "モデルファイル"))
        assertEquals(ScreenMode.DOWNLOAD, mode)
    }

    @Test
    fun downloadFailed_mapsToDownloadMode() {
        val mode = resolveScreenMode(AppState.DownloadFailed(error = "接続エラー"))
        assertEquals(ScreenMode.DOWNLOAD, mode)
    }

    @Test
    fun modelLoading_mapsToLoadingMode() {
        val mode = resolveScreenMode(AppState.ModelLoading)
        assertEquals(ScreenMode.LOADING, mode)
    }

    @Test
    fun modelReady_mapsToCameraMode() {
        val mode = resolveScreenMode(AppState.ModelReady)
        assertEquals(ScreenMode.CAMERA_SPLIT, mode)
    }

    @Test
    fun inferencing_mapsToCameraMode() {
        val mode = resolveScreenMode(AppState.Inferencing)
        assertEquals(ScreenMode.CAMERA_SPLIT, mode)
    }

    @Test
    fun inferenceResult_mapsToCameraMode() {
        val mode = resolveScreenMode(AppState.InferenceResult(text = "猫が写っています。"))
        assertEquals(ScreenMode.CAMERA_SPLIT, mode)
    }

    @Test
    fun inferenceError_mapsToCameraMode() {
        val mode = resolveScreenMode(AppState.InferenceError(message = "推論エラー"))
        assertEquals(ScreenMode.CAMERA_SPLIT, mode)
    }

    // -----------------------------------------------------------------------
    // isDownloadState / isModelReadyOrBeyond の境界テスト
    // -----------------------------------------------------------------------

    @Test
    fun downloadStates_areRecognizedAsDownloadMode() {
        val downloadStates = listOf(
            AppState.DownloadRequired,
            AppState.Downloading(0, ""),
            AppState.DownloadFailed("")
        )
        downloadStates.forEach { state ->
            assertEquals(
                "Expected DOWNLOAD for $state",
                ScreenMode.DOWNLOAD,
                resolveScreenMode(state)
            )
        }
    }

    @Test
    fun postDownloadStates_areNotDownloadMode() {
        val postDownloadStates = listOf(
            AppState.ModelLoading,
            AppState.ModelReady,
            AppState.Inferencing,
            AppState.InferenceResult(""),
            AppState.InferenceError("")
        )
        postDownloadStates.forEach { state ->
            assertFalse(
                "Expected non-DOWNLOAD for $state",
                resolveScreenMode(state) == ScreenMode.DOWNLOAD
            )
        }
    }

    @Test
    fun allAppStatesAreMapped() {
        val allStates: List<AppState> = listOf(
            AppState.DownloadRequired,
            AppState.Downloading(0, ""),
            AppState.DownloadFailed(""),
            AppState.ModelLoading,
            AppState.ModelReady,
            AppState.Inferencing,
            AppState.InferenceResult(""),
            AppState.InferenceError("")
        )
        allStates.forEach { state ->
            val mode = resolveScreenMode(state)
            assertNotNull("resolveScreenMode must return non-null for $state", mode)
        }
    }

    // -----------------------------------------------------------------------
    // DownloadUIContent: DownloadRequired / Downloading / DownloadFailed 分類テスト
    // -----------------------------------------------------------------------

    @Test
    fun downloadRequired_isNotDownloadingOrFailed() {
        val state = AppState.DownloadRequired
        assertFalse(state is AppState.Downloading)
        assertFalse(state is AppState.DownloadFailed)
        assertTrue(state is AppState.DownloadRequired)
    }

    @Test
    fun downloading_progressIsInRange() {
        val state = AppState.Downloading(progress = 75, label = "mmprojファイル")
        assertTrue(state.progress in 0..100 || state.progress == -1)
        assertEquals("mmprojファイル", state.label)
    }

    @Test
    fun downloadFailed_hasErrorMessage() {
        val error = "HTTP 503 Service Unavailable"
        val state = AppState.DownloadFailed(error = error)
        assertTrue(state.error.isNotEmpty())
        assertEquals(error, state.error)
    }
}
