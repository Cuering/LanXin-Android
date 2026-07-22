/*
 * Thin JNI bridge: MNN::Transformer::Llm (libllm.so).
 * Package: com.lanxin.android.builtin.localinference.data.MnnNativeBridge
 *
 * 对齐 MNN Chat 流畅路径：
 * - load 后 set_config(thread_num / backend / precision)
 * - ChatMessages 多轮（apply_chat_template）
 * - response 写 ostream → 可选 Java 流式回调（token 级 UI）
 * - 会话常驻，不每轮 create/destroy
 */

#include <android/log.h>
#include <jni.h>

#include <atomic>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include "llm/llm.hpp"

#define LOG_TAG "MnnLanxinJni"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

using MNN::Transformer::ChatMessage;
using MNN::Transformer::ChatMessages;
using MNN::Transformer::Llm;

namespace {

std::mutex g_mutex;
Llm* g_llm = nullptr;
std::string g_last_error;
std::atomic<bool> g_cancel{false};

// 流式回调：Java 全局引用 + method
JavaVM* g_jvm = nullptr;
jobject g_stream_listener = nullptr; // GlobalRef of ProgressListener
jmethodID g_on_token_mid = nullptr;  // boolean onToken(String)

void setError(const std::string& msg) {
    g_last_error = msg;
    ALOGE("%s", msg.c_str());
}

void clearError() { g_last_error.clear(); }

void unloadLocked() {
    if (g_llm != nullptr) {
        Llm::destroy(g_llm);
        g_llm = nullptr;
    }
    g_cancel.store(false);
}

/** Resolve model dir / config.json / llm.mnn → path expected by createLLM. */
std::string resolveConfigPath(const std::string& path) {
    if (path.empty()) return path;
    if (path.size() > 5 && path.substr(path.size() - 5) == ".json") {
        return path;
    }
    if (path.size() > 4 && path.substr(path.size() - 4) == ".mnn") {
        return path;
    }
    std::string base = path;
    while (!base.empty() && (base.back() == '/' || base.back() == '\\')) {
        base.pop_back();
    }
    const std::string candidates[] = {
        base + "/config.json",
        base + "/llm_config.json",
        base + "/llm.mnn.json",
        base + "/llm.mnn",
        base
    };
    for (const auto& c : candidates) {
        if (c == base) {
            FILE* m = fopen((base + "/llm.mnn").c_str(), "rb");
            if (m != nullptr) {
                fclose(m);
                return base;
            }
            continue;
        }
        FILE* f = fopen(c.c_str(), "rb");
        if (f != nullptr) {
            fclose(f);
            return c;
        }
    }
    return base + "/config.json";
}

/**
 * 对齐 MNN Chat 默认运行时：多线程 CPU、低精度、低内存。
 * 失败不致命（旧 so / 旧 config 可能不认部分键）。
 */
void applyRuntimeConfigLocked(Llm* llm) {
    if (llm == nullptr) return;
    // thread_num=4 与官方 Android 默认接近；backend_type=0 → CPU
    const char* cfg =
        R"({"thread_num":4,"backend_type":"cpu","precision":"low","memory":"low","tmp_path":""})";
    try {
        bool ok = llm->set_config(cfg);
        ALOGI("set_config ok=%d", ok ? 1 : 0);
    } catch (...) {
        ALOGW("set_config threw; continue with model defaults");
    }
}

/**
 * ostream 适配：每次写入（token/片段）回调 Java onToken。
 * 返回 true 表示用户请求停止。
 */
class JavaStreamBuf : public std::streambuf {
public:
    explicit JavaStreamBuf(JNIEnv* env) : env_(env) {}

protected:
    int_type overflow(int_type ch) override {
        if (ch != traits_type::eof()) {
            char c = static_cast<char>(ch);
            if (flushPiece(std::string(1, c))) {
                return traits_type::eof();
            }
        }
        return ch;
    }

    std::streamsize xsputn(const char* s, std::streamsize n) override {
        if (n <= 0) return n;
        if (flushPiece(std::string(s, static_cast<size_t>(n)))) {
            return 0;
        }
        return n;
    }

private:
    bool flushPiece(const std::string& piece) {
        if (piece.empty()) return false;
        if (g_cancel.load()) return true;
        if (g_stream_listener == nullptr || g_on_token_mid == nullptr || env_ == nullptr) {
            acc_ += piece;
            return false;
        }
        // 合并到缓冲，按 UTF-8 安全边界尽量吐出（简单：每次都回调，Kotlin 侧拼）
        jstring j = env_->NewStringUTF(piece.c_str());
        if (j == nullptr) return false;
        jboolean stop = env_->CallBooleanMethod(g_stream_listener, g_on_token_mid, j);
        env_->DeleteLocalRef(j);
        if (env_->ExceptionCheck()) {
            env_->ExceptionClear();
            return true;
        }
        if (stop == JNI_TRUE) {
            g_cancel.store(true);
            return true;
        }
        return false;
    }

    JNIEnv* env_;
    std::string acc_;
};

JNIEnv* getEnv(bool* attached) {
    *attached = false;
    if (g_jvm == nullptr) return nullptr;
    JNIEnv* env = nullptr;
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            return nullptr;
        }
        *attached = true;
    }
    return env;
}

void clearStreamListener(JNIEnv* env) {
    if (g_stream_listener != nullptr && env != nullptr) {
        env->DeleteGlobalRef(g_stream_listener);
    }
    g_stream_listener = nullptr;
    g_on_token_mid = nullptr;
}

} // namespace

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM* /* vm */, void* /* reserved */) {
    g_jvm = nullptr;
}

JNIEXPORT jboolean JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeLoadModel(
        JNIEnv* env,
        jobject /* thiz */,
        jstring pathJ) {
    if (pathJ == nullptr) {
        setError("path_null");
        return JNI_FALSE;
    }
    const char* pathC = env->GetStringUTFChars(pathJ, nullptr);
    if (pathC == nullptr) {
        setError("path_utf_null");
        return JNI_FALSE;
    }
    std::string path(pathC);
    env->ReleaseStringUTFChars(pathJ, pathC);

    std::lock_guard<std::mutex> lock(g_mutex);
    unloadLocked();
    clearError();

    const std::string configPath = resolveConfigPath(path);
    ALOGI("nativeLoadModel path=%s config=%s", path.c_str(), configPath.c_str());

    Llm* llm = nullptr;
    try {
        llm = Llm::createLLM(configPath);
        if (llm == nullptr) {
            setError("createLLM_null:" + configPath);
            return JNI_FALSE;
        }
        // 先 set_config 再 load（与 MNN Chat 一致：runtime 在 load 前/后都可调；
        // 官方 App 在 Load 内 merge config；这里 load 前后各一次，兼容性更好）
        applyRuntimeConfigLocked(llm);
        if (!llm->load()) {
            Llm::destroy(llm);
            setError("llm_load_failed:" + configPath);
            return JNI_FALSE;
        }
        applyRuntimeConfigLocked(llm);
        // 预热 tuning 可显著降低首 token（MNN Chat 会做）；失败忽略
        try {
            llm->tuning(MNN::Transformer::OP_ENCODER_NUMBER, {1, 5, 10, 20});
        } catch (...) {
            ALOGW("tuning skipped");
        }
        g_llm = llm;
        ALOGI("nativeLoadModel ok");
        return JNI_TRUE;
    } catch (const std::exception& e) {
        if (llm != nullptr) {
            Llm::destroy(llm);
        }
        setError(std::string("load_exception:") + e.what());
        return JNI_FALSE;
    } catch (...) {
        if (llm != nullptr) {
            Llm::destroy(llm);
        }
        setError("load_exception:unknown");
        return JNI_FALSE;
    }
}

/**
 * 整段生成（兼容旧路径）。
 * systemPrompt 可空；userPrompt 必填。
 * 使用 ChatMessages + apply_chat_template，避免裸拼接破坏模板。
 */
JNIEXPORT jstring JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeGenerate(
        JNIEnv* env,
        jobject /* thiz */,
        jstring promptJ,
        jint maxTokens) {
    // 兼容旧签名：prompt = 已拼好的全文
    if (promptJ == nullptr) {
        return nullptr;
    }
    const char* promptC = env->GetStringUTFChars(promptJ, nullptr);
    if (promptC == nullptr) {
        return nullptr;
    }
    std::string prompt(promptC);
    env->ReleaseStringUTFChars(promptJ, promptC);

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_llm == nullptr) {
        setError("not_loaded");
        return nullptr;
    }

    int maxNew = static_cast<int>(maxTokens);
    if (maxNew < 1) maxNew = 1;
    if (maxNew > 4096) maxNew = 4096;

    g_cancel.store(false);
    try {
        std::ostringstream os;
        // 单轮 user；若 prompt 已含 System: 前缀则仍走 string response
        //（Kotlin 侧会改走 nativeGenerateChat）
        g_llm->response(prompt, &os, nullptr, maxNew);
        std::string out = os.str();
        if (out.empty() && g_llm->getContext() != nullptr) {
            out = g_llm->getContext()->generate_str;
        }
        clearError();
        return env->NewStringUTF(out.c_str());
    } catch (const std::exception& e) {
        setError(std::string("generate_exception:") + e.what());
        return nullptr;
    } catch (...) {
        setError("generate_exception:unknown");
        return nullptr;
    }
}

/**
 * ChatMessages 多轮生成（对齐 MNN Chat submitFullHistory）。
 * roles/contents 等长；role 如 system/user/assistant。
 */
JNIEXPORT jstring JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeGenerateChat(
        JNIEnv* env,
        jobject /* thiz */,
        jobjectArray rolesJ,
        jobjectArray contentsJ,
        jint maxTokens) {
    if (rolesJ == nullptr || contentsJ == nullptr) {
        setError("chat_args_null");
        return nullptr;
    }
    const jsize nRoles = env->GetArrayLength(rolesJ);
    const jsize nContents = env->GetArrayLength(contentsJ);
    if (nRoles != nContents || nRoles <= 0) {
        setError("chat_args_mismatch");
        return nullptr;
    }

    ChatMessages messages;
    messages.reserve(static_cast<size_t>(nRoles));
    for (jsize i = 0; i < nRoles; ++i) {
        auto roleObj = (jstring) env->GetObjectArrayElement(rolesJ, i);
        auto contentObj = (jstring) env->GetObjectArrayElement(contentsJ, i);
        if (roleObj == nullptr || contentObj == nullptr) {
            if (roleObj) env->DeleteLocalRef(roleObj);
            if (contentObj) env->DeleteLocalRef(contentObj);
            continue;
        }
        const char* r = env->GetStringUTFChars(roleObj, nullptr);
        const char* c = env->GetStringUTFChars(contentObj, nullptr);
        if (r && c) {
            messages.emplace_back(std::string(r), std::string(c));
        }
        if (r) env->ReleaseStringUTFChars(roleObj, r);
        if (c) env->ReleaseStringUTFChars(contentObj, c);
        env->DeleteLocalRef(roleObj);
        env->DeleteLocalRef(contentObj);
    }
    if (messages.empty()) {
        setError("chat_empty");
        return nullptr;
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_llm == nullptr) {
        setError("not_loaded");
        return nullptr;
    }
    int maxNew = static_cast<int>(maxTokens);
    if (maxNew < 1) maxNew = 1;
    if (maxNew > 4096) maxNew = 4096;

    g_cancel.store(false);
    try {
        std::ostringstream os;
        g_llm->response(messages, &os, nullptr, maxNew);
        std::string out = os.str();
        if (out.empty() && g_llm->getContext() != nullptr) {
            out = g_llm->getContext()->generate_str;
        }
        clearError();
        return env->NewStringUTF(out.c_str());
    } catch (const std::exception& e) {
        setError(std::string("generate_chat_exception:") + e.what());
        return nullptr;
    } catch (...) {
        setError("generate_chat_exception:unknown");
        return nullptr;
    }
}

/**
 * 流式 Chat 生成：listener.onToken(String) → boolean stop。
 * 返回完整文本；listener 可为 null（等同 nativeGenerateChat）。
 */
JNIEXPORT jstring JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeGenerateChatStream(
        JNIEnv* env,
        jobject /* thiz */,
        jobjectArray rolesJ,
        jobjectArray contentsJ,
        jint maxTokens,
        jobject listener) {
    if (rolesJ == nullptr || contentsJ == nullptr) {
        setError("chat_args_null");
        return nullptr;
    }
    const jsize nRoles = env->GetArrayLength(rolesJ);
    const jsize nContents = env->GetArrayLength(contentsJ);
    if (nRoles != nContents || nRoles <= 0) {
        setError("chat_args_mismatch");
        return nullptr;
    }

    ChatMessages messages;
    messages.reserve(static_cast<size_t>(nRoles));
    for (jsize i = 0; i < nRoles; ++i) {
        auto roleObj = (jstring) env->GetObjectArrayElement(rolesJ, i);
        auto contentObj = (jstring) env->GetObjectArrayElement(contentsJ, i);
        if (roleObj == nullptr || contentObj == nullptr) {
            if (roleObj) env->DeleteLocalRef(roleObj);
            if (contentObj) env->DeleteLocalRef(contentObj);
            continue;
        }
        const char* r = env->GetStringUTFChars(roleObj, nullptr);
        const char* c = env->GetStringUTFChars(contentObj, nullptr);
        if (r && c) {
            messages.emplace_back(std::string(r), std::string(c));
        }
        if (r) env->ReleaseStringUTFChars(roleObj, r);
        if (c) env->ReleaseStringUTFChars(contentObj, c);
        env->DeleteLocalRef(roleObj);
        env->DeleteLocalRef(contentObj);
    }
    if (messages.empty()) {
        setError("chat_empty");
        return nullptr;
    }

    // 绑定 listener
    clearStreamListener(env);
    if (listener != nullptr) {
        g_stream_listener = env->NewGlobalRef(listener);
        jclass cls = env->GetObjectClass(listener);
        g_on_token_mid = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)Z");
        env->DeleteLocalRef(cls);
        if (g_on_token_mid == nullptr) {
            ALOGW("onToken method missing; stream disabled");
            clearStreamListener(env);
        }
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_llm == nullptr) {
        clearStreamListener(env);
        setError("not_loaded");
        return nullptr;
    }
    int maxNew = static_cast<int>(maxTokens);
    if (maxNew < 1) maxNew = 1;
    if (maxNew > 4096) maxNew = 4096;

    g_cancel.store(false);
    try {
        JavaStreamBuf sbuf(env);
        std::ostream os(&sbuf);
        // 同时收集完整输出
        std::ostringstream acc;
        // tee: 用自定义 ostream 写 Java；再读 context
        g_llm->response(messages, &os, nullptr, maxNew);
        std::string out;
        if (g_llm->getContext() != nullptr) {
            out = g_llm->getContext()->generate_str;
        }
        // 若 context 空，acc 可能也空（片段已回调）；用空串合法
        clearError();
        clearStreamListener(env);
        return env->NewStringUTF(out.c_str());
    } catch (const std::exception& e) {
        clearStreamListener(env);
        setError(std::string("generate_stream_exception:") + e.what());
        return nullptr;
    } catch (...) {
        clearStreamListener(env);
        setError("generate_stream_exception:unknown");
        return nullptr;
    }
}

JNIEXPORT void JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeCancel(
        JNIEnv* /* env */,
        jobject /* thiz */) {
    g_cancel.store(true);
}

JNIEXPORT void JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeReset(
        JNIEnv* /* env */,
        jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_llm != nullptr) {
        try {
            g_llm->reset();
        } catch (...) {
        }
    }
    g_cancel.store(false);
}

JNIEXPORT void JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeUnload(
        JNIEnv* env,
        jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    clearStreamListener(env);
    unloadLocked();
    clearError();
}

JNIEXPORT jstring JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeLastError(
        JNIEnv* env,
        jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_last_error.empty()) {
        return nullptr;
    }
    return env->NewStringUTF(g_last_error.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeIsLoaded(
        JNIEnv* /* env */,
        jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_llm != nullptr ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
