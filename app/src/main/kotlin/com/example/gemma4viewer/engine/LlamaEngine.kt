package com.example.gemma4viewer.engine

/**
 * llama.cpp / libmtmd JNI関数のKotlinネイティブ宣言とSystem.loadLibraryエントリポイント。
 *
 * このクラスはJNI宣言のみを保持する（Task 5.1）。
 * C実装はTask 5.2/5.3/6.x で行う。
 *
 * Requirements: 4.1, 4.4
 */
class LlamaEngine {

    companion object {
        init {
            System.loadLibrary("llama-jni")
        }
    }

    // バックエンド初期化（nativeLoad より先に呼ぶ）

    /** ggml バックエンド .so をネイティブライブラリディレクトリからロードし、バックエンドを初期化する */
    external fun nativeInitBackend(nativeLibDir: String)

    // テキストモデル初期化

    /** モデルファイルをロードする。戻り値: 0=OK, 1=失敗 */
    external fun nativeLoad(modelPath: String): Int

    /** KVキャッシュとスレッド数を設定して推論コンテキストを準備する。戻り値: 0=OK, 1=失敗 */
    external fun nativePrepare(nCtx: Int, nThreads: Int): Int

    /** llama.cppのシステム情報文字列を返す */
    external fun nativeSystemInfo(): String

    // マルチモーダル初期化

    /** マルチモーダル射影モデル(mmproj)をロードする。戻り値: 0=OK, 1=失敗 */
    external fun nativeLoadMmproj(mmprojPath: String): Int

    // 推論

    /**
     * RGBバイト配列の画像とプロンプトをKVキャッシュに書き込む。
     * 戻り値: 0=OK, 1=失敗
     */
    external fun nativeProcessImageTurn(
        rgbBytes: ByteArray,
        width: Int,
        height: Int,
        prompt: String
    ): Int

    /** 次のトークンを1つサンプリングして返す。EOS時は空文字列を返す */
    external fun nativeGenerateNextToken(): String

    // クリーンアップ

    /** モデル・コンテキスト・サンプラーを解放する */
    external fun nativeUnload()
}
