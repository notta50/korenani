package com.example.gemma4viewer.viewmodel

import android.graphics.Bitmap
import app.cash.turbine.test
import com.example.gemma4viewer.repository.DownloadState
import com.example.gemma4viewer.repository.InferenceRepository
import com.example.gemma4viewer.repository.ModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelStateTransitionTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // テスト1: DownloadRequired → Downloading → ModelLoading → ModelReady の遷移
    @Test
    fun `onStartDownload transitions DownloadRequired through Downloading to ModelReady`() = runTest {
        val modelRepo = object : ModelRepository {
            override fun isModelReady() = false
            override fun downloadModels(): Flow<DownloadState> = flowOf(
                DownloadState.Progress(30, "model.gguf"),
                DownloadState.Progress(70, "model.gguf"),
                DownloadState.Finished
            )
            override fun getModelPath() = "/fake/model.gguf"
            override fun getMmprojPath() = "/fake/mmproj.gguf"
        }
        val inferenceRepo = object : InferenceRepository {
            override suspend fun initialize(modelPath: String, mmprojPath: String) {}
            override fun infer(bitmap: Bitmap, prompt: String): Flow<String> = emptyFlow()
            override suspend fun release() {}
        }

        val vm = MainViewModel(modelRepo, inferenceRepo)

        vm.appState.test {
            assertEquals(AppState.DownloadRequired, awaitItem())

            vm.onStartDownload()

            val downloading1 = awaitItem()
            assertTrue(downloading1 is AppState.Downloading)
            assertEquals(30, (downloading1 as AppState.Downloading).progress)

            val downloading2 = awaitItem()
            assertTrue(downloading2 is AppState.Downloading)
            assertEquals(70, (downloading2 as AppState.Downloading).progress)

            assertEquals(AppState.ModelLoading, awaitItem())
            assertEquals(AppState.ModelReady, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // テスト2: DownloadFailed への遷移（500エラー時）
    @Test
    fun `onStartDownload transitions to DownloadFailed on error`() = runTest {
        val modelRepo = object : ModelRepository {
            override fun isModelReady() = false
            override fun downloadModels(): Flow<DownloadState> = flowOf(
                DownloadState.Failed(RuntimeException("接続エラー"))
            )
            override fun getModelPath() = ""
            override fun getMmprojPath() = ""
        }
        val inferenceRepo = object : InferenceRepository {
            override suspend fun initialize(modelPath: String, mmprojPath: String) {}
            override fun infer(bitmap: Bitmap, prompt: String): Flow<String> = emptyFlow()
            override suspend fun release() {}
        }

        val vm = MainViewModel(modelRepo, inferenceRepo)

        vm.appState.test {
            assertEquals(AppState.DownloadRequired, awaitItem())
            vm.onStartDownload()
            val failed = awaitItem()
            assertTrue(failed is AppState.DownloadFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // テスト3: ModelReady → Inferencing → InferenceResult → ModelReady の遷移
    @Test
    fun `onCapture transitions ModelReady through Inferencing to InferenceResult to ModelReady`() = runTest {
        val modelRepo = object : ModelRepository {
            override fun isModelReady() = true
            override fun downloadModels(): Flow<DownloadState> = emptyFlow()
            override fun getModelPath() = "/fake/model.gguf"
            override fun getMmprojPath() = "/fake/mmproj.gguf"
        }
        val inferenceRepo = object : InferenceRepository {
            override suspend fun initialize(modelPath: String, mmprojPath: String) {}
            override fun infer(bitmap: Bitmap, prompt: String): Flow<String> =
                flowOf("こんにちは", "、世界")
            override suspend fun release() {}
        }

        val vm = MainViewModel(modelRepo, inferenceRepo)
        val fakeBitmap = mock(Bitmap::class.java)

        vm.appState.test {
            assertEquals(AppState.DownloadRequired, awaitItem())

            vm.onAppStart()

            assertEquals(AppState.ModelLoading, awaitItem())
            assertEquals(AppState.ModelReady, awaitItem())

            vm.onCapture(fakeBitmap)

            assertEquals(AppState.Inferencing, awaitItem())

            val result1 = awaitItem()
            assertTrue(result1 is AppState.InferenceResult)
            assertEquals("こんにちは", (result1 as AppState.InferenceResult).text)

            val result2 = awaitItem()
            assertTrue(result2 is AppState.InferenceResult)
            assertEquals("こんにちは、世界", (result2 as AppState.InferenceResult).text)

            assertEquals(AppState.ModelReady, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // テスト4: 推論エラー時に InferenceError へ遷移
    @Test
    fun `onCapture transitions to InferenceError on infer failure`() = runTest {
        val modelRepo = object : ModelRepository {
            override fun isModelReady() = true
            override fun downloadModels(): Flow<DownloadState> = emptyFlow()
            override fun getModelPath() = "/fake/model.gguf"
            override fun getMmprojPath() = "/fake/mmproj.gguf"
        }
        val inferenceRepo = object : InferenceRepository {
            override suspend fun initialize(modelPath: String, mmprojPath: String) {}
            override fun infer(bitmap: Bitmap, prompt: String): Flow<String> =
                kotlinx.coroutines.flow.flow { throw RuntimeException("JNIエラー") }
            override suspend fun release() {}
        }

        val vm = MainViewModel(modelRepo, inferenceRepo)
        val fakeBitmap = mock(Bitmap::class.java)

        vm.appState.test {
            assertEquals(AppState.DownloadRequired, awaitItem())
            vm.onAppStart()
            assertEquals(AppState.ModelLoading, awaitItem())
            assertEquals(AppState.ModelReady, awaitItem())

            vm.onCapture(fakeBitmap)

            assertEquals(AppState.Inferencing, awaitItem())
            val error = awaitItem()
            assertTrue(error is AppState.InferenceError)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
