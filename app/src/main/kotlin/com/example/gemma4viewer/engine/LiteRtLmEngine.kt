package com.example.gemma4viewer.engine

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * LiteRT-LM SDK の Engine ライフサイクルを管理し、GPU 優先・CPU フォールバック初期化と
 * マルチモーダル推論ストリーミングを提供するエンジンクラス。
 *
 * @param gpuEngineFactory GPU バックエンドで EngineHandle を生成するファクトリ（テスト時はモック可）
 * @param cpuEngineFactory CPU バックエンドで EngineHandle を生成するファクトリ（GPU 失敗時のフォールバック）
 */
class LiteRtLmEngine(
    private val gpuEngineFactory: suspend (modelPath: String) -> EngineHandle = { modelPath ->
        SdkEngineHandle(
            Engine(EngineConfig(modelPath = modelPath, backend = Backend.GPU()))
                .also { it.initialize() }
        )
    },
    private val cpuEngineFactory: suspend (modelPath: String) -> EngineHandle = { modelPath ->
        SdkEngineHandle(
            Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU()))
                .also { it.initialize() }
        )
    },
) {

    /**
     * エンジンリソースのライフサイクルを抽象化するインターフェース。
     * プロダクションコードでは SdkEngineHandle が実装し、テストでは FakeEngineHandle が実装する。
     */
    interface EngineHandle {
        fun close()
    }

    private var engineHandle: EngineHandle? = null

    /**
     * GPU バックエンドでエンジンを初期化する。GPU が利用できない場合は CPU にフォールバックする。
     * 全バックエンドで初期化に失敗した場合は例外をスローする。
     *
     * @param modelPath .litertlm モデルファイルの絶対パス
     */
    suspend fun initialize(modelPath: String) {
        engineHandle = try {
            val handle = gpuEngineFactory(modelPath)
            Log.i(TAG, "GPU バックエンドで初期化成功")
            handle
        } catch (gpuException: Exception) {
            Log.w(TAG, "GPU 初期化失敗。CPU フォールバックを試みます: ${gpuException.message}")
            val handle = cpuEngineFactory(modelPath)
            Log.i(TAG, "CPU バックエンドで初期化成功（フォールバック）")
            handle
        }
    }

    /**
     * エンジンリソースを解放する。解放後の infer() 呼び出しは IllegalStateException をスローする。
     */
    suspend fun release() {
        engineHandle?.close()
        engineHandle = null
        Log.i(TAG, "エンジンリソースを解放しました")
    }

    /**
     * 画像とプロンプトを使ったマルチモーダル推論を実行し、生成トークンを Flow でストリーミングする。
     * Task 2.2 で完全なストリーミング実装に置き換えられる。
     *
     * @param imagePath 推論に使用する画像の絶対ファイルパス
     * @param prompt テキストプロンプト
     * @return 生成トークンのストリーム
     * @throws IllegalStateException initialize() が呼ばれていないか、release() 後に呼ばれた場合
     */
    fun infer(imagePath: String, prompt: String): Flow<String> {
        checkNotNull(engineHandle) {
            "エンジンが初期化されていません。infer() の前に initialize() を呼び出してください。"
        }
        return emptyFlow()
    }

    companion object {
        private const val TAG = "LiteRtLmEngine"

        /**
         * LiteRT-LM SDK の Engine インスタンスをラップする EngineHandle 実装。
         * プロダクションコードでのみ使用される。
         */
        private class SdkEngineHandle(private val engine: Engine) : EngineHandle {
            override fun close() {
                engine.close()
            }
        }
    }
}
