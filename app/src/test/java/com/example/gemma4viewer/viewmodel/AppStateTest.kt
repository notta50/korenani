package com.example.gemma4viewer.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateTest {

    // -----------------------------------------------------------------------
    // 各バリアントのインスタンス化テスト
    // -----------------------------------------------------------------------

    @Test
    fun downloadRequired_canBeInstantiated() {
        val state: AppState = AppState.DownloadRequired
        assertTrue(state is AppState.DownloadRequired)
    }

    @Test
    fun downloading_canBeInstantiated() {
        val state: AppState = AppState.Downloading(progress = 42, label = "モデルファイル")
        assertTrue(state is AppState.Downloading)
        val downloading = state as AppState.Downloading
        assertEquals(42, downloading.progress)
        assertEquals("モデルファイル", downloading.label)
    }

    @Test
    fun downloadFailed_canBeInstantiated() {
        val state: AppState = AppState.DownloadFailed(error = "ネットワーク接続エラー")
        assertTrue(state is AppState.DownloadFailed)
        val failed = state as AppState.DownloadFailed
        assertEquals("ネットワーク接続エラー", failed.error)
    }

    @Test
    fun modelLoading_canBeInstantiated() {
        val state: AppState = AppState.ModelLoading
        assertTrue(state is AppState.ModelLoading)
    }

    @Test
    fun modelReady_canBeInstantiated() {
        val state: AppState = AppState.ModelReady
        assertTrue(state is AppState.ModelReady)
    }

    @Test
    fun inferencing_canBeInstantiated() {
        val state: AppState = AppState.Inferencing
        assertTrue(state is AppState.Inferencing)
    }

    @Test
    fun inferenceResult_canBeInstantiated() {
        val state: AppState = AppState.InferenceResult(text = "猫が写っています。")
        assertTrue(state is AppState.InferenceResult)
        val result = state as AppState.InferenceResult
        assertEquals("猫が写っています。", result.text)
    }

    @Test
    fun inferenceError_canBeInstantiated() {
        val state: AppState = AppState.InferenceError(message = "推論中にエラーが発生しました。")
        assertTrue(state is AppState.InferenceError)
        val error = state as AppState.InferenceError
        assertEquals("推論中にエラーが発生しました。", error.message)
    }

    // -----------------------------------------------------------------------
    // `when` 式でスマートキャスト動作確認テスト（全バリアント網羅、else不要）
    // -----------------------------------------------------------------------

    @Test
    fun whenExpression_coversAllBranches_withoutElse() {
        val states: List<AppState> = listOf(
            AppState.DownloadRequired,
            AppState.Downloading(0, ""),
            AppState.DownloadFailed(""),
            AppState.ModelLoading,
            AppState.ModelReady,
            AppState.Inferencing,
            AppState.InferenceResult(""),
            AppState.InferenceError(""),
            AppState.InferenceDone("")
        )

        for (state in states) {
            // sealed class のため when は網羅的: else ブランチ不要でコンパイル可能
            val label: String = when (state) {
                is AppState.DownloadRequired -> "DownloadRequired"
                is AppState.Downloading      -> "Downloading:${state.progress}"   // スマートキャスト確認
                is AppState.DownloadFailed   -> "DownloadFailed:${state.error}"   // スマートキャスト確認
                is AppState.ModelLoading     -> "ModelLoading"
                is AppState.ModelReady       -> "ModelReady"
                is AppState.Inferencing      -> "Inferencing"
                is AppState.InferenceResult  -> "InferenceResult:${state.text}"   // スマートキャスト確認
                is AppState.InferenceError   -> "InferenceError:${state.message}" // スマートキャスト確認
                is AppState.InferenceDone    -> "InferenceDone:${state.text}"     // スマートキャスト確認
            }
            assertTrue("label must not be empty", label.isNotEmpty())
        }
    }

    @Test
    fun whenExpression_smartCast_downloading() {
        val state: AppState = AppState.Downloading(progress = 75, label = "mmprojファイル")
        val result = when (state) {
            is AppState.Downloading -> state.progress  // スマートキャストで .progress に直接アクセス
            else                    -> -1
        }
        assertEquals(75, result)
    }

    @Test
    fun whenExpression_smartCast_inferenceResult() {
        val state: AppState = AppState.InferenceResult(text = "テスト結果")
        val result = when (state) {
            is AppState.InferenceResult -> state.text  // スマートキャストで .text に直接アクセス
            else                        -> ""
        }
        assertEquals("テスト結果", result)
    }

    // -----------------------------------------------------------------------
    // データクラスバリアントの等価性テスト
    // -----------------------------------------------------------------------

    @Test
    fun downloading_equality_sameValues() {
        val a = AppState.Downloading(progress = 50, label = "test")
        val b = AppState.Downloading(progress = 50, label = "test")
        assertEquals(a, b)
    }

    @Test
    fun downloading_equality_differentValues() {
        val a = AppState.Downloading(progress = 50, label = "test")
        val b = AppState.Downloading(progress = 51, label = "test")
        assertNotEquals(a, b)
    }

    @Test
    fun downloadFailed_equality_sameValues() {
        val a = AppState.DownloadFailed(error = "error msg")
        val b = AppState.DownloadFailed(error = "error msg")
        assertEquals(a, b)
    }

    @Test
    fun inferenceResult_equality_sameValues() {
        val a = AppState.InferenceResult(text = "hello")
        val b = AppState.InferenceResult(text = "hello")
        assertEquals(a, b)
    }

    @Test
    fun inferenceResult_equality_differentValues() {
        val a = AppState.InferenceResult(text = "hello")
        val b = AppState.InferenceResult(text = "world")
        assertNotEquals(a, b)
    }

    @Test
    fun inferenceError_equality_sameValues() {
        val a = AppState.InferenceError(message = "err")
        val b = AppState.InferenceError(message = "err")
        assertEquals(a, b)
    }

    // -----------------------------------------------------------------------
    // object バリアントの同一性テスト（singleton確認）
    // -----------------------------------------------------------------------

    @Test
    fun downloadRequired_isSingleton() {
        val a: AppState = AppState.DownloadRequired
        val b: AppState = AppState.DownloadRequired
        assertTrue(a === b)
    }

    @Test
    fun modelReady_isSingleton() {
        val a: AppState = AppState.ModelReady
        val b: AppState = AppState.ModelReady
        assertTrue(a === b)
    }

    @Test
    fun inferencing_isNotSameAsModelReady() {
        val inferencing: AppState = AppState.Inferencing
        val ready: AppState = AppState.ModelReady
        assertFalse(inferencing === ready)
    }

    // -----------------------------------------------------------------------
    // data class の copy() 機能テスト
    // -----------------------------------------------------------------------

    @Test
    fun downloading_copy_changesProgress() {
        val original = AppState.Downloading(progress = 10, label = "モデルファイル")
        val copied = original.copy(progress = 90)
        assertEquals(90, copied.progress)
        assertEquals("モデルファイル", copied.label)
    }

    @Test
    fun inferenceResult_copy_changesText() {
        val original = AppState.InferenceResult(text = "first token")
        val updated = original.copy(text = "first token second token")
        assertEquals("first token second token", updated.text)
    }
}
