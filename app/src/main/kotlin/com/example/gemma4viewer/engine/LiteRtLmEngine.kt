package com.example.gemma4viewer.engine

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

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
            Engine(EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU(),
                visionBackend = Backend.GPU(), // マルチモーダル推論には visionBackend の指定が必須（公式ドキュメント導)
            ))
                .also { it.initialize() }
        )
    },
    private val cpuEngineFactory: suspend (modelPath: String) -> EngineHandle = { modelPath ->
        SdkEngineHandle(
            Engine(EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                visionBackend = Backend.CPU(),
            ))
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

        /**
         * エンジンから会話ハンドルを作成する。
         * プロダクションコードでは SDK の Conversation をラップし、テストではフェイクを返す。
         */
        fun createConversation(): ConversationHandle
    }

    /**
     * 会話セッションのライフサイクルを抽象化するインターフェース。
     * プロダクションコードでは SdkConversationHandle が実装し、テストでは FakeConversationHandle が実装する。
     */
    interface ConversationHandle {
        /**
         * マルチモーダルコンテンツで非同期推論を実行し、生成トークンを Flow として返す。
         *
         * @param imagePath 推論に使用する画像の絶対ファイルパス
         * @param prompt テキストプロンプト
         * @return 生成トークンのストリーム。生成完了で正常終了、エラー時は例外で終了する。
         */
        fun sendMessageAsync(imagePath: String, prompt: String): Flow<String>

        fun close()
    }

    private var engineHandle: EngineHandle? = null

    /** initialize() で渡されたモデルパス。推論時 CPU フォールバックで再初期化に使用する。 */
    private var currentModelPath: String? = null

    /**
     * true: GPU で初期化済み（推論時に GPU 失敗が起きたら CPU フォールバックを試みる余地がある）
     * false: すでに CPU フォールバック済み、または初期化前
     */
    private var gpuInitSucceeded = false

    /**
     * GPU バックエンドでエンジンを初期化する。GPU が利用できない場合は CPU にフォールバックする。
     * 全バックエンドで初期化に失敗した場合は例外をスローする。
     * 初期化成功後は infer() を受け付けられる状態に遷移する（要件 2.3）。
     *
     * @param modelPath .litertlm モデルファイルの絶対パス
     */
    suspend fun initialize(modelPath: String) {
        currentModelPath = modelPath
        gpuInitSucceeded = false
        engineHandle?.close()
        val handle = try {
            val h = gpuEngineFactory(modelPath)
            Log.i(TAG, "GPU バックエンドで初期化成功")
            gpuInitSucceeded = true
            h
        } catch (gpuException: Exception) {
            Log.w(TAG, "GPU 初期化失敗。CPU フォールバックを試みます: ${gpuException.message}")
            val h = cpuEngineFactory(modelPath)
            Log.i(TAG, "CPU バックエンドで初期化成功（フォールバック）")
            h
        }
        engineHandle = handle
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
     *
     * Contents.of(Content.ImageFile(imagePath), Content.Text(prompt)) を構築して
     * conversation.sendMessageAsync() に渡し、各 Message を文字列トークンとして emit する。
     * 生成完了時に Flow が正常終了し、エラー時に Flow が例外で終了する（要件 3.3, 3.4）。
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
        return flow {
            // LiteRT-LM の Conversation は 1 回の推論ごとに新規生成・廃棄する必要がある。
            // 同じ Conversation を再利用すると内部ネイティブポインタが無効化され SIGSEGV を引き起こす。
            val handle = checkNotNull(engineHandle) { "engineHandle が null" }
            val conversation = handle.createConversation()
            var conversationClosed = false
            try {
                conversation.sendMessageAsync(imagePath, prompt).collect { token ->
                    emit(token)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // GPU 推論失敗: CPU フォールバックへ切り替えて同一リクエストを再試行する
                if (gpuInitSucceeded && currentModelPath != null) {
                    Log.w(TAG, "GPU 推論失敗（${e.message}）。CPU フォールバックへ切り替えます")
                    gpuInitSucceeded = false
                    conversation.close()
                    conversationClosed = true
                    handle.close()
                    val cpuHandle = cpuEngineFactory(requireNotNull(currentModelPath))
                    engineHandle = cpuHandle
                    Log.i(TAG, "CPU バックエンドで再初期化成功（推論時フォールバック）")
                    val retryConversation = cpuHandle.createConversation()
                    try {
                        retryConversation.sendMessageAsync(imagePath, prompt).collect { token ->
                            emit(token)
                        }
                        Log.d(TAG, "CPU フォールバック推論完了")
                    } finally {
                        retryConversation.close()
                    }
                } else {
                    throw e
                }
            } finally {
                if (!conversationClosed) conversation.close()
                Log.d(TAG, "Conversation を廃棄しました")
            }
        }
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

            override fun createConversation(): ConversationHandle {
                return SdkConversationHandle(engine.createConversation())
            }
        }

        /**
         * LiteRT-LM SDK の Conversation インスタンスをラップする ConversationHandle 実装。
         * プロダクションコードでのみ使用される。
         *
         * sendMessageAsync(Contents) は Flow<Message> を返す。
         * Message.toString() は Contents.toString() を返し、テキストトークンを結合した文字列となる。
         */
        private class SdkConversationHandle(private val conversation: Conversation) : ConversationHandle {
            override fun sendMessageAsync(imagePath: String, prompt: String): Flow<String> {
                val contents = Contents.of(
                    Content.ImageFile(imagePath),
                    Content.Text(prompt)
                )
                return conversation.sendMessageAsync(contents).map { message ->
                    message.toString()
                }
            }

            override fun close() {
                conversation.close()
            }
        }
    }
}
