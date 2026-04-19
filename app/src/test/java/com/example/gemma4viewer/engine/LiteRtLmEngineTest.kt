package com.example.gemma4viewer.engine

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * LiteRtLmEngine のユニットテスト。
 *
 * LiteRT-LM SDK の実際のクラスはユニットテスト環境でインスタンス化できないため、
 * コンストラクタインジェクションによるファクトリ関数でモック可能にする。
 *
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.2, 3.3, 3.4
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiteRtLmEngineTest {

    // ---------------------------------------------------------------------------
    // GPU フォールバックテスト (要件 2.1, 2.2)
    // ---------------------------------------------------------------------------

    /**
     * GPU ファクトリが例外をスローした場合、CPU ファクトリが呼ばれて初期化が完了すること。
     * Requirements: 2.1, 2.2
     */
    @Test
    fun `initialize falls back to CPU when GPU throws`() = runTest {
        var cpuCalled = false

        val engine = LiteRtLmEngine(
            gpuEngineFactory = { _ ->
                throw RuntimeException("GPU not available")
            },
            cpuEngineFactory = { _ ->
                cpuCalled = true
                FakeEngineHandle()
            }
        )

        engine.initialize("/fake/model.litertlm")

        assert(cpuCalled) { "GPU失敗時はCPUファクトリが呼ばれるべき" }
    }

    /**
     * GPU ファクトリが成功した場合、CPU ファクトリは呼ばれないこと。
     * Requirements: 2.1
     */
    @Test
    fun `initialize uses GPU when GPU succeeds`() = runTest {
        var gpuCalled = false
        var cpuCalled = false

        val engine = LiteRtLmEngine(
            gpuEngineFactory = { _ ->
                gpuCalled = true
                FakeEngineHandle()
            },
            cpuEngineFactory = { _ ->
                cpuCalled = true
                FakeEngineHandle()
            }
        )

        engine.initialize("/fake/model.litertlm")

        assert(gpuCalled) { "GPU初期化が試みられるべき" }
        assert(!cpuCalled) { "GPU成功時はCPUファクトリは呼ばれるべきでない" }
    }

    /**
     * GPU・CPU 両方のファクトリが例外をスローした場合、例外が伝播すること。
     * Requirements: 2.4
     */
    @Test
    fun `initialize throws when both GPU and CPU fail`() = runTest {
        val engine = LiteRtLmEngine(
            gpuEngineFactory = { _ ->
                throw RuntimeException("GPU failed")
            },
            cpuEngineFactory = { _ ->
                throw RuntimeException("CPU failed")
            }
        )

        assertThrows(Exception::class.java) {
            kotlinx.coroutines.runBlocking {
                engine.initialize("/fake/model.litertlm")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // release 後の infer 呼び出しテスト (要件 2.5)
    // ---------------------------------------------------------------------------

    /**
     * release() 後に infer() を呼び出すと IllegalStateException がスローされること。
     * Requirements: 2.5
     */
    @Test
    fun `infer throws IllegalStateException after release`() = runTest {
        val engine = LiteRtLmEngine(
            gpuEngineFactory = { _ -> FakeEngineHandle() },
            cpuEngineFactory = { _ -> FakeEngineHandle() }
        )

        engine.initialize("/fake/model.litertlm")
        engine.release()

        assertThrows(IllegalStateException::class.java) {
            engine.infer("/fake/image.jpg", "説明してください")
        }
    }

    /**
     * initialize() を呼ばずに infer() を呼び出すと IllegalStateException がスローされること。
     * Requirements: 2.5 (防御)
     */
    @Test
    fun `infer throws IllegalStateException when not initialized`() {
        val engine = LiteRtLmEngine(
            gpuEngineFactory = { _ -> FakeEngineHandle() },
            cpuEngineFactory = { _ -> FakeEngineHandle() }
        )

        assertThrows(IllegalStateException::class.java) {
            engine.infer("/fake/image.jpg", "説明してください")
        }
    }

    // ---------------------------------------------------------------------------
    // Task 2.2: infer() Flow ストリーミングテスト (要件 2.3, 3.2, 3.3, 3.4)
    // ---------------------------------------------------------------------------

    /**
     * initialize() 成功後に infer() が呼び出し可能で、
     * ConversationHandle.sendMessageAsync() が返すトークンを Flow として emit すること。
     * Requirements: 2.3, 3.2
     */
    @Test
    fun `infer emits tokens from conversation sendMessageAsync`() = runTest {
        val expectedTokens = listOf("こんにちは", "、", "これは", "テスト", "です。")
        val fakeConversation = FakeConversationHandle(
            flowOf(*expectedTokens.toTypedArray())
        )
        val engine = LiteRtLmEngine(
            gpuEngineFactory = { _ -> FakeEngineHandle(fakeConversation) },
            cpuEngineFactory = { _ -> FakeEngineHandle() }
        )

        engine.initialize("/fake/model.litertlm")

        engine.infer("/fake/image.jpg", "説明してください").test {
            expectedTokens.forEach { token ->
                assertEquals(token, awaitItem())
            }
            awaitComplete()
        }
    }

    /**
     * sendMessageAsync() が正常に完了した場合、Flow が正常終了すること。
     * Requirements: 3.3
     */
    @Test
    fun `infer flow completes normally when generation ends`() = runTest {
        val fakeConversation = FakeConversationHandle(flowOf("トークン1", "トークン2"))
        val engine = LiteRtLmEngine(
            gpuEngineFactory = { _ -> FakeEngineHandle(fakeConversation) },
            cpuEngineFactory = { _ -> FakeEngineHandle() }
        )

        engine.initialize("/fake/model.litertlm")

        engine.infer("/fake/image.jpg", "テスト").test {
            awaitItem()
            awaitItem()
            awaitComplete()
        }
    }

    /**
     * sendMessageAsync() がエラーをスローした場合、Flow が例外で終了すること。
     * Requirements: 3.4
     */
    @Test
    fun `infer flow terminates with exception on inference error`() = runTest {
        val inferenceError = RuntimeException("推論エンジンエラー")
        val fakeConversation = FakeConversationHandle(
            flow { throw inferenceError }
        )
        val engine = LiteRtLmEngine(
            gpuEngineFactory = { _ -> FakeEngineHandle(fakeConversation) },
            cpuEngineFactory = { _ -> FakeEngineHandle() }
        )

        engine.initialize("/fake/model.litertlm")

        engine.infer("/fake/image.jpg", "エラーテスト").test {
            val error = awaitError()
            assertEquals("推論エンジンエラー", error.message)
        }
    }

    /**
     * sendMessageAsync() が一部トークン emit 後にエラーをスローした場合、
     * emit済みトークンが受信でき、その後 Flow が例外で終了すること。
     * Requirements: 3.2, 3.4
     */
    @Test
    fun `infer flow emits partial tokens then terminates with exception`() = runTest {
        val inferenceError = RuntimeException("中途エラー")
        val fakeConversation = FakeConversationHandle(
            flow {
                emit("最初のトークン")
                throw inferenceError
            }
        )
        val engine = LiteRtLmEngine(
            gpuEngineFactory = { _ -> FakeEngineHandle(fakeConversation) },
            cpuEngineFactory = { _ -> FakeEngineHandle() }
        )

        engine.initialize("/fake/model.litertlm")

        engine.infer("/fake/image.jpg", "部分エラーテスト").test {
            assertEquals("最初のトークン", awaitItem())
            val error = awaitError()
            assertEquals("中途エラー", error.message)
        }
    }

    /**
     * imagePath と prompt が ConversationHandle に正しく渡されること。
     * Requirements: 3.2
     */
    @Test
    fun `infer passes imagePath and prompt to conversation`() = runTest {
        val fakeConversation = FakeConversationHandle(flowOf("OK"))
        val engine = LiteRtLmEngine(
            gpuEngineFactory = { _ -> FakeEngineHandle(fakeConversation) },
            cpuEngineFactory = { _ -> FakeEngineHandle() }
        )

        engine.initialize("/fake/model.litertlm")
        engine.infer("/path/to/test.jpg", "テストプロンプト").test {
            awaitItem()
            awaitComplete()
        }

        assertEquals("/path/to/test.jpg", fakeConversation.lastImagePath)
        assertEquals("テストプロンプト", fakeConversation.lastPrompt)
    }

    // ---------------------------------------------------------------------------
    // Fake helpers
    // ---------------------------------------------------------------------------

    /**
     * テスト用のエンジンハンドル。SDK クラスを使わずに成功を模擬する。
     */
    class FakeEngineHandle(
        private val conversationHandle: FakeConversationHandle = FakeConversationHandle(flowOf())
    ) : LiteRtLmEngine.EngineHandle {
        private var closed = false

        override fun close() {
            closed = true
        }

        override fun createConversation(): LiteRtLmEngine.ConversationHandle {
            return conversationHandle
        }
    }

    /**
     * テスト用の会話ハンドル。指定された Flow をそのまま返す。
     * imagePath と prompt の受け取りを記録してテスト検証に使用する。
     */
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
