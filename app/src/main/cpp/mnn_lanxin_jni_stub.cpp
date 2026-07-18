/*
 * ABI stub (e.g. x86_64): no prebuilt MNN LLM. Always report unavailable.
 */

#include <jni.h>

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeLoadModel(
        JNIEnv* /* env */, jobject /* thiz */, jstring /* pathJ */) {
    return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeGenerate(
        JNIEnv* /* env */, jobject /* thiz */, jstring /* promptJ */, jint /* maxTokens */) {
    return nullptr;
}

JNIEXPORT void JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeUnload(
        JNIEnv* /* env */, jobject /* thiz */) {}

JNIEXPORT jstring JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeLastError(
        JNIEnv* env, jobject /* thiz */) {
    return env->NewStringUTF("abi_stub_no_mnn_prebuilt");
}

JNIEXPORT jboolean JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeIsLoaded(
        JNIEnv* /* env */, jobject /* thiz */) {
    return JNI_FALSE;
}

} // extern "C"
