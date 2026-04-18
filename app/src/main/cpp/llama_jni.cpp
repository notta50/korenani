#include <jni.h>
#include <string>
#include <android/log.h>
#include "llama.h"

#define TAG "llama-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model*   g_model   = nullptr;
static llama_context* g_ctx     = nullptr;
static llama_sampler* g_sampler = nullptr;
static llama_batch    g_batch   = {};

extern "C" {

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

JNIEXPORT jint JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeLoadMmproj(
        JNIEnv* env, jobject /* thiz */, jstring mmprojPath) {
    (void)env;
    (void)mmprojPath;
    return 0;  // stub — Task 6.2で実装
}

JNIEXPORT jint JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeProcessImageTurn(
        JNIEnv* env, jobject /* thiz */,
        jbyteArray rgbBytes, jint width, jint height, jstring prompt) {
    (void)env;
    (void)rgbBytes;
    (void)width;
    (void)height;
    (void)prompt;
    return 0;  // stub — Task 6.3で実装
}

JNIEXPORT jstring JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeGenerateNextToken(
        JNIEnv* env, jobject /* thiz */) {
    return env->NewStringUTF("");  // stub — Task 5.3で実装（EOS相当）
}

} // extern "C"
