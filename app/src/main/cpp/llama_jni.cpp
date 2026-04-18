#include <jni.h>
#include <string>
#include <android/log.h>
#include <chrono>
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define TAG "llama-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model*   g_model    = nullptr;
static llama_context* g_ctx      = nullptr;
static llama_sampler* g_sampler  = nullptr;
static llama_batch    g_batch    = {};
static mtmd_context*  g_mtmd_ctx = nullptr;
static int            g_token_count  = 0;
static int64_t        g_gen_start_ms = 0;

static int64_t now_ms() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

static void llama_log_callback(ggml_log_level level, const char* text, void* /*user_data*/) {
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: LOGE("[llama] %s", text); break;
        case GGML_LOG_LEVEL_WARN:  LOGI("[llama-warn] %s", text); break;
        default:                   LOGI("[llama] %s", text); break;
    }
}

extern "C" {

// ---------------------------------------------------------------------------
// nativeInitBackend — ggmlバックエンドをネイティブライブラリディレクトリからロード
// nativeLoad より先に呼ぶこと
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeInitBackend(
        JNIEnv* env, jobject /* thiz */, jstring /* nativeLibDir */) {
    llama_log_set(llama_log_callback, nullptr);
    llama_backend_init();
    LOGI("nativeInitBackend: done (static backends)");
}

// ---------------------------------------------------------------------------
// nativeLoad — GGUFモデルをファイルパスからロードする
// 戻り値: 0=成功, 1=失敗
// ---------------------------------------------------------------------------
JNIEXPORT jint JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeLoad(
        JNIEnv* env, jobject /* thiz */, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) {
        LOGE("nativeLoad: GetStringUTFChars failed");
        return 1;
    }
    LOGI("nativeLoad: loading model from %s", path);

    // ファイルサイズ確認（ゼロや小さすぎる場合はファイル破損）
    FILE* f = fopen(path, "rb");
    if (f) {
        fseek(f, 0, SEEK_END);
        long sz = ftell(f);
        fclose(f);
        LOGI("nativeLoad: model file size = %ld bytes (%.1f MB)", sz, sz / 1048576.0f);
        if (sz < 100 * 1024 * 1024) {
            LOGE("nativeLoad: model file too small (%ld bytes) — possibly corrupted", sz);
            env->ReleaseStringUTFChars(modelPath, path);
            return 1;
        }
    }

    llama_model_params params = llama_model_default_params();
    g_model = llama_model_load_from_file(path, params);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!g_model) {
        LOGE("nativeLoad: llama_model_load_from_file failed");
        return 1;
    }
    LOGI("nativeLoad: model loaded successfully");
    return 0;
}

// ---------------------------------------------------------------------------
// nativePrepare — コンテキスト・サンプラーを初期化する
// 戻り値: 0=成功, 1=失敗
// ---------------------------------------------------------------------------
JNIEXPORT jint JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativePrepare(
        JNIEnv* /* env */, jobject /* thiz */, jint nCtx, jint nThreads) {
    if (!g_model) {
        LOGE("nativePrepare: g_model is null — call nativeLoad first");
        return 1;
    }
    LOGI("nativePrepare: n_ctx=%d n_threads=%d", (int)nCtx, (int)nThreads);

    llama_context_params ctx_params  = llama_context_default_params();
    ctx_params.n_ctx                 = (uint32_t)nCtx;
    ctx_params.n_threads             = (uint32_t)nThreads;
    ctx_params.n_threads_batch       = (uint32_t)nThreads;

    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("nativePrepare: llama_new_context_with_model failed");
        return 1;
    }

    // グリーディサンプラーチェーンを設定
    g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(g_sampler, llama_sampler_init_greedy());

    // g_batch はプレースホルダーとして初期化（実際のトークンはTask 5.3で設定）
    g_batch = llama_batch_get_one(nullptr, 0);

    LOGI("nativePrepare: context and sampler initialized");
    return 0;
}

// ---------------------------------------------------------------------------
// nativeSystemInfo — llama.cppシステム情報文字列を返す
// ---------------------------------------------------------------------------
JNIEXPORT jstring JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeSystemInfo(
        JNIEnv* env, jobject /* thiz */) {
    const char* info = llama_print_system_info();
    return env->NewStringUTF(info ? info : "");
}

// ---------------------------------------------------------------------------
// nativeUnload — サンプラー・コンテキスト・モデルをこの順に解放する
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeUnload(
        JNIEnv* /* env */, jobject /* thiz */) {
    LOGI("nativeUnload: releasing resources");
    if (g_mtmd_ctx) {
        mtmd_free(g_mtmd_ctx);
        g_mtmd_ctx = nullptr;
    }
    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_batch = {};
    LOGI("nativeUnload: done");
}

// ---------------------------------------------------------------------------
// 以下はスタブ — Task 5.3 / Task 6 で実装される
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// nativeLoadMmproj — mmprojファイルからmtmdコンテキストを初期化する
// Gemma 4クラッシュ回避: image_min_tokens = 0 を必ず設定する
// 戻り値: 0=成功, 1=失敗
// ---------------------------------------------------------------------------
JNIEXPORT jint JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeLoadMmproj(
        JNIEnv* env, jobject /* thiz */, jstring mmprojPath) {
    if (!g_model) {
        LOGE("nativeLoadMmproj: g_model is null — call nativeLoad first");
        return 1;
    }
    const char* path = env->GetStringUTFChars(mmprojPath, nullptr);
    if (!path) {
        LOGE("nativeLoadMmproj: GetStringUTFChars failed");
        return 1;
    }
    LOGI("nativeLoadMmproj: loading mmproj from %s", path);

    mtmd_context_params params = mtmd_context_params_default();
    params.image_min_tokens = 0;  // Gemma 4: 0以外だとクラッシュする

    g_mtmd_ctx = mtmd_init_from_file(path, g_model, params);
    env->ReleaseStringUTFChars(mmprojPath, path);

    if (!g_mtmd_ctx) {
        LOGE("nativeLoadMmproj: mtmd_init_from_file failed");
        return 1;
    }
    LOGI("nativeLoadMmproj: mtmd context initialized successfully");
    return 0;
}

// ---------------------------------------------------------------------------
// nativeProcessImageTurn — RGB画像+プロンプトをKVキャッシュに書き込む
// promptにはmtmd_default_marker()を含む完全なテキストを渡すこと
// M-RoPE処理はmtmd_helper_eval_chunksが自動処理する
// 戻り値: 0=成功, 1=失敗
// ---------------------------------------------------------------------------
JNIEXPORT jint JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeProcessImageTurn(
        JNIEnv* env, jobject /* thiz */,
        jbyteArray rgbBytes, jint width, jint height, jstring prompt) {
    if (!g_mtmd_ctx || !g_ctx || !g_model) {
        LOGE("nativeProcessImageTurn: uninitialized state");
        return 1;
    }
    g_token_count = 0;
    int64_t t_start = now_ms();
    LOGI("nativeProcessImageTurn: START image=%dx%d", (int)width, (int)height);

    // 会話履歴を保持しないため毎回KVキャッシュをクリアする
    llama_memory_clear(llama_get_memory(g_ctx), false);
    LOGI("nativeProcessImageTurn: KV cache cleared (+%lldms)", (long long)(now_ms()-t_start));

    // JNIバイト配列をCポインタに変換
    jbyte* rgb_data = env->GetByteArrayElements(rgbBytes, nullptr);
    if (!rgb_data) {
        LOGE("nativeProcessImageTurn: GetByteArrayElements failed");
        return 1;
    }

    mtmd_bitmap* bitmap = mtmd_bitmap_init(
        (uint32_t)width, (uint32_t)height,
        reinterpret_cast<const unsigned char*>(rgb_data)
    );
    env->ReleaseByteArrayElements(rgbBytes, rgb_data, JNI_ABORT);

    if (!bitmap) {
        LOGE("nativeProcessImageTurn: mtmd_bitmap_init failed");
        return 1;
    }

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_str) {
        mtmd_bitmap_free(bitmap);
        LOGE("nativeProcessImageTurn: GetStringUTFChars failed");
        return 1;
    }

    mtmd_input_chunks* chunks = mtmd_input_chunks_init();
    if (!chunks) {
        env->ReleaseStringUTFChars(prompt, prompt_str);
        mtmd_bitmap_free(bitmap);
        LOGE("nativeProcessImageTurn: mtmd_input_chunks_init failed");
        return 1;
    }

    mtmd_input_text input_text = {
        prompt_str,
        /*add_special=*/true,
        /*parse_special=*/true
    };
    const mtmd_bitmap* bitmaps[] = { bitmap };
    int32_t ret = mtmd_tokenize(g_mtmd_ctx, chunks, &input_text, bitmaps, 1);
    env->ReleaseStringUTFChars(prompt, prompt_str);
    mtmd_bitmap_free(bitmap);

    if (ret != 0) {
        mtmd_input_chunks_free(chunks);
        LOGE("nativeProcessImageTurn: mtmd_tokenize failed (ret=%d)", ret);
        return 1;
    }
    LOGI("nativeProcessImageTurn: tokenize done (+%lldms)", (long long)(now_ms()-t_start));

    // mtmd_helper_eval_chunksがM-RoPEを含む全チャンク処理を自動で行う
    LOGI("nativeProcessImageTurn: eval_chunks START (image encoding + prefill — 数十秒かかる場合あり)");
    llama_pos new_n_past = 0;
    ret = mtmd_helper_eval_chunks(
        g_mtmd_ctx, g_ctx, chunks,
        /*n_past=*/0, /*seq_id=*/0, /*n_batch=*/2048, /*logits_last=*/true,
        &new_n_past
    );
    mtmd_input_chunks_free(chunks);

    if (ret != 0) {
        LOGE("nativeProcessImageTurn: mtmd_helper_eval_chunks failed (ret=%d)", ret);
        return 1;
    }

    LOGI("nativeProcessImageTurn: DONE new_n_past=%d total=%lldms", (int)new_n_past, (long long)(now_ms()-t_start));
    return 0;
}

JNIEXPORT jstring JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeGenerateNextToken(
        JNIEnv* env, jobject /* thiz */) {
    if (!g_ctx || !g_model || !g_sampler) {
        LOGE("nativeGenerateNextToken: uninitialized state (ctx=%p model=%p sampler=%p)",
             (void*)g_ctx, (void*)g_model, (void*)g_sampler);
        return env->NewStringUTF("");
    }

    if (g_token_count == 0) {
        g_gen_start_ms = now_ms();
        LOGI("nativeGenerateNextToken: generation START");
    }

    // サンプリング: バッチの最後のlogits位置から次トークンを選択
    llama_token token = llama_sampler_sample(g_sampler, g_ctx, -1);

    // EOGトークン（EOS/EOT）検出時は空文字を返す
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    if (llama_vocab_is_eog(vocab, token)) {
        int64_t elapsed = now_ms() - g_gen_start_ms;
        float tps = g_token_count > 0 ? g_token_count * 1000.0f / elapsed : 0;
        LOGI("nativeGenerateNextToken: EOG — %d tokens in %lldms (%.1f tok/s)", g_token_count, (long long)elapsed, tps);
        g_token_count = 0;
        return env->NewStringUTF("");
    }

    // サンプラーの内部状態（repetition penaltyなど）を更新
    llama_sampler_accept(g_sampler, token);

    // 1トークンのバッチを作成してデコード（KVキャッシュに積む）
    g_batch = llama_batch_get_one(&token, 1);
    if (llama_decode(g_ctx, g_batch) != 0) {
        LOGE("nativeGenerateNextToken: llama_decode failed");
        return env->NewStringUTF("");
    }

    // トークンIDをテキスト（UTF-8断片）に変換
    char buf[256] = {};
    int n = llama_token_to_piece(vocab, token, buf, (int)sizeof(buf) - 1, 0, true);
    if (n < 0) {
        LOGE("nativeGenerateNextToken: llama_token_to_piece failed (n=%d)", n);
        return env->NewStringUTF("");
    }
    buf[n] = '\0';

    g_token_count++;
    if (g_token_count % 10 == 0) {
        int64_t elapsed = now_ms() - g_gen_start_ms;
        float tps = g_token_count * 1000.0f / elapsed;
        LOGI("nativeGenerateNextToken: token #%d (%.1f tok/s) last=\"%s\"", g_token_count, tps, buf);
    }

    return env->NewStringUTF(buf);
}

} // extern "C"
