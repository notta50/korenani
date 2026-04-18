package com.example.gemma4viewer.viewmodel

import android.graphics.Bitmap
import com.example.gemma4viewer.repository.DownloadState
import com.example.gemma4viewer.repository.InferenceRepository
import com.example.gemma4viewer.repository.ModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeInferenceRepo() = object : InferenceRepository {
        override suspend fun initialize(modelPath: String, mmprojPath: String) {}
        override fun infer(bitmap: Bitmap, prompt: String): Flow<String> = emptyFlow()
        override suspend fun release() {}
    }

    @Test
    fun `onAppStart sets DownloadRequired when model not ready`() = runTest {
        val modelRepo = object : ModelRepository {
            override fun isModelReady() = false
            override fun downloadModels(): Flow<DownloadState> = emptyFlow()
            override fun getModelPath() = ""
            override fun getMmprojPath() = ""
        }
        val vm = MainViewModel(modelRepo, fakeInferenceRepo())
        vm.onAppStart()
        advanceUntilIdle()
        assertEquals(AppState.DownloadRequired, vm.appState.value)
    }

    @Test
    fun `onAppStart sets ModelReady when model already ready`() = runTest {
        val modelRepo = object : ModelRepository {
            override fun isModelReady() = true
            override fun downloadModels(): Flow<DownloadState> = emptyFlow()
            override fun getModelPath() = "/fake/model.gguf"
            override fun getMmprojPath() = "/fake/mmproj.gguf"
        }
        val vm = MainViewModel(modelRepo, fakeInferenceRepo())
        vm.onAppStart()
        advanceUntilIdle()
        assertEquals(AppState.ModelReady, vm.appState.value)
    }

    @Test
    fun `onStartDownload transitions through Downloading to ModelReady`() = runTest {
        val modelRepo = object : ModelRepository {
            override fun isModelReady() = false
            override fun downloadModels(): Flow<DownloadState> = flowOf(
                DownloadState.Progress(50, "model.gguf"),
                DownloadState.Finished
            )
            override fun getModelPath() = "/fake/model.gguf"
            override fun getMmprojPath() = "/fake/mmproj.gguf"
        }
        val vm = MainViewModel(modelRepo, fakeInferenceRepo())
        vm.onStartDownload()
        advanceUntilIdle()
        assertEquals(AppState.ModelReady, vm.appState.value)
    }

    @Test
    fun `onStartDownload sets DownloadFailed on error`() = runTest {
        val modelRepo = object : ModelRepository {
            override fun isModelReady() = false
            override fun downloadModels(): Flow<DownloadState> = flowOf(
                DownloadState.Failed(RuntimeException("ネットワークエラー"))
            )
            override fun getModelPath() = ""
            override fun getMmprojPath() = ""
        }
        val vm = MainViewModel(modelRepo, fakeInferenceRepo())
        vm.onStartDownload()
        advanceUntilIdle()
        assertTrue(vm.appState.value is AppState.DownloadFailed)
    }

    @Test
    fun `onRetryDownload restarts download flow`() = runTest {
        val modelRepo = object : ModelRepository {
            override fun isModelReady() = false
            override fun downloadModels(): Flow<DownloadState> = flowOf(DownloadState.Finished)
            override fun getModelPath() = "/fake/model.gguf"
            override fun getMmprojPath() = "/fake/mmproj.gguf"
        }
        val vm = MainViewModel(modelRepo, fakeInferenceRepo())
        vm.onRetryDownload()
        advanceUntilIdle()
        assertEquals(AppState.ModelReady, vm.appState.value)
    }

}
