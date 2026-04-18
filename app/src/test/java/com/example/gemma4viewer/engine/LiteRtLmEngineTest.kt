package com.example.gemma4viewer.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * LiteRtLmEngine のユニットテスト。
 *
 * LiteRT-LM SDK の実際のクラスはユニットテスト環境でインスタンス化できないため、
 * コンストラクタインジェクションによるファクトリ関数でモック可能にする。
 *
 * Requirements: 2.1, 2.2, 2.4, 2.5
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
    // Fake helpers
    // ---------------------------------------------------------------------------

    /**
     * テスト用のエンジンハンドル。SDK クラスを使わずに成功を模擬する。
     */
    class FakeEngineHandle : LiteRtLmEngine.EngineHandle {
        private var closed = false

        override fun close() {
            closed = true
        }
    }
}
