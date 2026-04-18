package com.example.gemma4viewer.repository

import android.graphics.Bitmap
import com.example.gemma4viewer.engine.LiteRtLmEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock

class InferenceRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ---------------------------------------------------------------------------
    // インターフェース実装確認
    // ---------------------------------------------------------------------------

    @Test
    fun `InferenceRepositoryImpl implements InferenceRepository`() {
        assertTrue(
            InferenceRepository::class.java.isAssignableFrom(InferenceRepositoryImpl::class.java)
        )
    }

    // ---------------------------------------------------------------------------
    // 一時ファイル削除テスト (要件 3.6)
    // ---------------------------------------------------------------------------

    /**
     * infer() が正常完了した場合、一時ファイルが削除されること。
     * Requirements: 3.6
     */
    @Test
    fun `infer deletes temp file on normal completion`() = runBlocking {
        val cacheDir = tempFolder.newFolder("cache")
        val fakeConversation = FakeConversationHandle(flowOf("token1", "token2"))
        val fakeEngine = buildFakeEngine(fakeConversation)

        val tempFile = java.io.File.createTempFile("infer_", ".jpg", cacheDir)
        tempFile.writeText("fake image data")
        assertTrue("テスト前: 一時ファイルが存在する", tempFile.exists())

        val mockBitmap: Bitmap = mock(Bitmap::class.java)

        // createAndWriteTempFile をオーバーライドしてテスト用一時ファイルを返す
        val testRepo = object : InferenceRepositoryImpl(engine = fakeEngine, cacheDir = cacheDir) {
            override fun createAndWriteTempFile(bitmap: Bitmap): java.io.File {
                return tempFile
            }
        }

        testRepo.initialize("/model", "/mmproj")
        testRepo.infer(mockBitmap, "test prompt").collect { }

        assertFalse("正常完了後: 一時ファイルが削除される", tempFile.exists())
    }

    /**
     * engine.infer() が例外をスローした場合、一時ファイルが削除されること。
     * Requirements: 3.6
     */
    @Test
    fun `infer deletes temp file when engine throws exception`() = runBlocking {
        val cacheDir = tempFolder.newFolder("cache2")
        val errorFlow: Flow<String> = flow { throw RuntimeException("inference error") }
        val fakeConversation = FakeConversationHandle(errorFlow)
        val fakeEngine = buildFakeEngine(fakeConversation)

        val tempFile = java.io.File.createTempFile("infer_", ".jpg", cacheDir)
        tempFile.writeText("fake image data")
        assertTrue("テスト前: 一時ファイルが存在する", tempFile.exists())

        val mockBitmap: Bitmap = mock(Bitmap::class.java)

        val testRepo = object : InferenceRepositoryImpl(engine = fakeEngine, cacheDir = cacheDir) {
            override fun createAndWriteTempFile(bitmap: Bitmap): java.io.File {
                return tempFile
            }
        }

        testRepo.initialize("/model", "/mmproj")
        try {
            testRepo.infer(mockBitmap, "test prompt").collect { }
        } catch (e: Exception) {
            // 例外は期待どおり
        }

        assertFalse("例外発生後: 一時ファイルが削除される", tempFile.exists())
    }

    // ---------------------------------------------------------------------------
    // Helper factories
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
        override fun sendMessageAsync(imagePath: String, prompt: String): Flow<String> = responseFlow
        override fun close() {}
    }
}
