// Placeholder JNI implementation — will be replaced with full llama.cpp JNI bridge in task 1.3
#include <jni.h>
#include <android/log.h>

#define LOG_TAG "llama-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jint JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeLoad(JNIEnv *env, jobject thiz, jstring model_path) {
    LOGI("nativeLoad called (placeholder)");
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativePrepare(JNIEnv *env, jobject thiz, jint n_ctx, jint n_threads) {
    LOGI("nativePrepare called (placeholder)");
    return 0;
}

JNIEXPORT jstring JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeSystemInfo(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("placeholder");
}

JNIEXPORT jint JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeLoadMmproj(JNIEnv *env, jobject thiz, jstring mmproj_path) {
    LOGI("nativeLoadMmproj called (placeholder)");
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeProcessImageTurn(JNIEnv *env, jobject thiz, jbyteArray rgb_bytes, jint width, jint height, jstring prompt) {
    LOGI("nativeProcessImageTurn called (placeholder)");
    return 0;
}

JNIEXPORT jstring JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeGenerateNextToken(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("");
}

JNIEXPORT void JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeUnload(JNIEnv *env, jobject thiz) {
    LOGI("nativeUnload called (placeholder)");
}

} // extern "C"
