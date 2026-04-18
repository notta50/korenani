package com.example.gemma4viewer.viewmodel

import android.graphics.Bitmap
import com.example.gemma4viewer.repository.DownloadState
import com.example.gemma4viewer.repository.InferenceRepository
import com.example.gemma4viewer.repository.ModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MainViewModel

    private val fakeModelRepo = object : ModelRepository {
        override fun isModelReady(): Boolean = false
        override fun downloadModels(): Flow<DownloadState> = kotlinx.coroutines.flow.flow {}
        override fun getModelPath(): String = ""
        override fun getMmprojPath(): String = ""
    }

    private val fakeInferenceRepo = object : InferenceRepository {
        override suspend fun initialize(modelPath: String, mmprojPath: String) {}
        override fun infer(bitmap: Bitmap, prompt: String): Flow<String> =
            kotlinx.coroutines.flow.flow {}
        override suspend fun release() {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MainViewModel(fakeModelRepo, fakeInferenceRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun appState_initialValue_isDownloadRequired() {
        val state = viewModel.appState.value
        assertEquals(AppState.DownloadRequired, state)
        assertTrue(state is AppState.DownloadRequired)
    }

    @Test
    fun appState_isStateFlowType() {
        val stateFlow: StateFlow<AppState> = viewModel.appState
        assertTrue(stateFlow.value is AppState)
    }

    @Test
    fun onAppStart_doesNotCrash() {
        viewModel.onAppStart()
    }

    @Test
    fun onStartDownload_doesNotCrash() {
        viewModel.onStartDownload()
    }

    @Test
    fun onRetryDownload_doesNotCrash() {
        viewModel.onRetryDownload()
    }

    @Test
    fun onCapture_methodSignatureAcceptsBitmap() {
        val method = MainViewModel::class.java.getDeclaredMethod(
            "onCapture",
            Bitmap::class.java
        )
        assertEquals("onCapture", method.name)
        assertEquals(Bitmap::class.java, method.parameterTypes[0])
    }
}
