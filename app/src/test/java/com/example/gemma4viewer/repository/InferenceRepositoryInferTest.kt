package com.example.gemma4viewer.repository

import android.graphics.Bitmap
import com.example.gemma4viewer.engine.LiteRtLmEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock

class InferenceRepositoryInferTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ---------------------------------------------------------------------------
    // initialize: mmprojPath を受け取るが engine.initialize(modelPath) のみを呼ぶ (要件 5.3)
    // ---------------------------------------------------------------------------

    @Test
    fun `initialize calls engine initialize with modelPath only`() = runBlocking {
        val cacheDir = tempFolder.newFolder("cache")
        var initializedModelPath: String? = null
        val fakeEngine = LiteRtLmEngine(
            gpuEngineFactory = { modelPath ->
                initializedModelPath = modelPath
                FakeEngineHandle(FakeConversationHandle(flowOf()))
            },
            cpuEngineFactory = { _ -> FakeEngineHandle(FakeConversationHandle(flowOf())) }
        )

        val repo = InferenceRepositoryImpl(engine = fakeEngine, cacheDir = cacheDir)
        repo.initialize("/path/to/model.litertlm", "/ignored/mmproj.bin")

        assertEquals("/path/to/model.litertlm", initializedModelPath)
    }

    // ---------------------------------------------------------------------------
    // release: engine.release() が呼ばれること (要件 2.5)
    // ---------------------------------------------------------------------------

    @Test
    fun `release calls engine release`() = runBlocking {
        val cacheDir = tempFolder.newFolder("cache2")
        val fakeConversation = FakeConversationHandle(flowOf())
        val fakeEngine = buildFakeEngine(fakeConversation)

        val repo = InferenceRepositoryImpl(engine = fakeEngine, cacheDir = cacheDir)
        repo.initialize("/model", "/mmproj")
        // release 後に infer すると IllegalStateException になる
        repo.release()

        var threw = false
        try {
            fakeEngine.infer("/any.jpg", "test")
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue("release後はinferがIllegalStateExceptionをスロー", threw)
    }

    // ---------------------------------------------------------------------------
    // 一時ファイル: imagePath が engine.infer に渡されること (要件 3.1, 3.5)
    // ---------------------------------------------------------------------------

    @Test
    fun `infer passes temp file path to engine`() = runBlocking {
        val cacheDir = tempFolder.newFolder("cache3")
        val fakeConversation = FakeConversationHandle(flowOf("ok"))
        val fakeEngine = buildFakeEngine(fakeConversation)

        val tempFile = java.io.File.createTempFile("infer_", ".jpg", cacheDir)
        tempFile.writeText("fake")

        val mockBitmap: Bitmap = mock(Bitmap::class.java)

        val testRepo = object : InferenceRepositoryImpl(engine = fakeEngine, cacheDir = cacheDir) {
            override fun createAndWriteTempFile(bitmap: Bitmap): java.io.File {
                return tempFile
            }
        }

        testRepo.initialize("/model", "/mmproj")
        testRepo.infer(mockBitmap, "hello").collect { }

        assertEquals(tempFile.absolutePath, fakeConversation.lastImagePath)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun buildFakeEngine(conversation: FakeConversationHandle): LiteRtLmEngine {
        return LiteRtLmEngine(
            gpuEngineFactory = { _ -> FakeEngineHandle(conversation) },
            cpuEngineFactory = { _ -> FakeEngineHandle(conversation) }
        )
    }

    class FakeEngineHandle(
        private val conversationHandle: FakeConversationHandle
    ) : LiteRtLmEngine.EngineHandle {
        override fun close() {}
        override fun createConversation(): LiteRtLmEngine.ConversationHandle = conversationHandle
    }

    class FakeConversationHandle(
        private val responseFlow: Flow<String>
    ) : LiteRtLmEngine.ConversationHandle {
        var lastImagePath: String? = null
        var lastPrompt: String? = null

        override fun sendMessageAsync(imagePath: String, prompt: String): Flow<String> {
            lastImagePath = imagePath
            lastPrompt = prompt
            return responseFlow
        }

        override fun close() {}
    }
}
