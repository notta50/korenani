#include <jni.h>
#include <android/log.h>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jint JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeLoad(
    JNIEnv *env, jobject thiz, jstring model_path) {
    return 0;  // stub
}

JNIEXPORT jint JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativePrepare(
    JNIEnv *env, jobject thiz, jint n_ctx, jint n_threads) {
    return 0;  // stub
}

JNIEXPORT jstring JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeSystemInfo(
    JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("stub");
}

JNIEXPORT jint JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeLoadMmproj(
    JNIEnv *env, jobject thiz, jstring mmproj_path) {
    return 0;  // stub
}

JNIEXPORT jint JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeProcessImageTurn(
    JNIEnv *env, jobject thiz, jbyteArray rgb_bytes, jint width, jint height, jstring prompt) {
    return 0;  // stub
}

JNIEXPORT jstring JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeGenerateNextToken(
    JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("");  // EOS stub
}

JNIEXPORT void JNICALL
Java_com_example_gemma4viewer_engine_LlamaEngine_nativeUnload(
    JNIEnv *env, jobject thiz) {
    // stub
}

} // extern "C"
