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
#include <cstdint>
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
 * 对齐 MNN Chat 运行时：
 * - 硬件：多线程 CPU、低精度、低内存
 * - 采样：mixed + penalty 前置，抑制 phrase loop（官方默认 mixed 不含 penalty）
 * 失败不致命（旧 so / 旧 config 可能不认部分键）。
 *
 * 注意：set_config 会 merge 进当前配置；模型包 config.json 的路径类字段仍由 createLLM 读取。
 */
void applyRuntimeConfigLocked(Llm* llm) {
    if (llm == nullptr) return;
    // thread_num=4 与官方 Android 默认接近
    // mixed_samplers 把 penalty 放最前；repetition_penalty≈1.15 是小模型防复读常用值
    const char* cfg =
        R"({"thread_num":4,"backend_type":"cpu","precision":"low","memory":"low","tmp_path":"",)"
        R"("sampler_type":"mixed",)"
        R"("mixed_samplers":["penalty","topK","topP","temperature"],)"
        R"("temperature":0.7,"top_k":40,"top_p":0.9,)"
        R"("repetition_penalty":1.15,"presence_penalty":0.1,"frequency_penalty":0.1})";
    try {
        bool ok = llm->set_config(cfg);
        ALOGI("set_config ok=%d (hw+sampler+penalty)", ok ? 1 : 0);
    } catch (...) {
        ALOGW("set_config threw; continue with model defaults");
    }
}

/**
 * 返回从 pos 开始的 UTF-8 码点需要的字节数；非法首字节返回 -1。
 * 仅看首字节期望长度，不验证后续字节。
 */
int utf8ExpectedLen(unsigned char lead) {
    if (lead <= 0x7F) return 1;
    if ((lead & 0xE0) == 0xC0) return 2;
    if ((lead & 0xF0) == 0xE0) return 3;
    if ((lead & 0xF8) == 0xF0) return 4;
    return -1;
}

/** 是否为合法 UTF-8 续字节 (10xxxxxx)。 */
bool isUtf8Cont(unsigned char b) {
    return (b & 0xC0) == 0x80;
}

/**
 * 校验 [data, data+len) 是否为合法 UTF-8（标准，含 4 字节 emoji）。
 * 裸 0 视为非法（后续转 jstring 时替换为空格）。
 */
bool isValidUtf8Sequence(const char* data, size_t len) {
    if (len == 0) return true;
    size_t i = 0;
    while (i < len) {
        auto lead = static_cast<unsigned char>(data[i]);
        if (lead == 0x00) return false;
        int need = utf8ExpectedLen(lead);
        if (need < 0) return false;
        if (i + static_cast<size_t>(need) > len) return false;
        for (int k = 1; k < need; ++k) {
            if (!isUtf8Cont(static_cast<unsigned char>(data[i + static_cast<size_t>(k)]))) {
                return false;
            }
        }
        // 过度长编码 / 超范围简单拒绝
        if (need == 2 && lead < 0xC2) return false;
        if (need == 4 && lead > 0xF4) return false;
        i += static_cast<size_t>(need);
    }
    return true;
}

/**
 * 把任意字节串清洗为合法 UTF-8：
 * - 截断/非法序列替换为 U+FFFD (EF BF BD)
 * - 裸 0 替换为空格
 */
std::string sanitizeToValidUtf8(const std::string& in) {
    std::string out;
    out.reserve(in.size());
    size_t i = 0;
    const size_t n = in.size();
    while (i < n) {
        auto lead = static_cast<unsigned char>(in[i]);
        if (lead == 0x00) {
            out.push_back(' ');
            ++i;
            continue;
        }
        int need = utf8ExpectedLen(lead);
        if (need < 0) {
            out.append("\xEF\xBF\xBD");
            ++i;
            continue;
        }
        if (i + static_cast<size_t>(need) > n) {
            out.append("\xEF\xBF\xBD");
            break;
        }
        bool ok = true;
        for (int k = 1; k < need; ++k) {
            if (!isUtf8Cont(static_cast<unsigned char>(in[i + static_cast<size_t>(k)]))) {
                ok = false;
                break;
            }
        }
        if (ok && need == 2 && lead < 0xC2) ok = false;
        if (ok && need == 4 && lead > 0xF4) ok = false;
        if (!ok) {
            out.append("\xEF\xBF\xBD");
            ++i;
            continue;
        }
        out.append(in, i, static_cast<size_t>(need));
        i += static_cast<size_t>(need);
    }
    return out;
}

/**
 * UTF-8 → UTF-16，再 NewString。
 *
 * 关键：绝不用 NewStringUTF。
 * NewStringUTF 只接受 Modified UTF-8，**禁止 4 字节序列（emoji 等 U+10000+）**，
 * 合法标准 UTF-8 也会直接 JNI Abort / SIGABRT，Java CrashHandler 捕不到。
 */
jstring newStringUtfSafe(JNIEnv* env, const std::string& s) {
    if (env == nullptr) return nullptr;
    const std::string& utf8 =
        isValidUtf8Sequence(s.data(), s.size()) ? s : sanitizeToValidUtf8(s);

    std::vector<jchar> utf16;
    utf16.reserve(utf8.size() + 1);
    size_t i = 0;
    const size_t n = utf8.size();
    while (i < n) {
        auto lead = static_cast<unsigned char>(utf8[i]);
        if (lead == 0x00) {
            utf16.push_back(static_cast<jchar>(' '));
            ++i;
            continue;
        }
        int need = utf8ExpectedLen(lead);
        if (need < 0 || i + static_cast<size_t>(need) > n) {
            utf16.push_back(static_cast<jchar>(0xFFFD));
            ++i;
            continue;
        }
        bool contOk = true;
        for (int k = 1; k < need; ++k) {
            if (!isUtf8Cont(static_cast<unsigned char>(utf8[i + static_cast<size_t>(k)]))) {
                contOk = false;
                break;
            }
        }
        if (!contOk) {
            utf16.push_back(static_cast<jchar>(0xFFFD));
            ++i;
            continue;
        }

        uint32_t cp = 0;
        if (need == 1) {
            cp = lead;
        } else if (need == 2) {
            cp = (static_cast<uint32_t>(lead & 0x1F) << 6) |
                 (static_cast<unsigned char>(utf8[i + 1]) & 0x3F);
        } else if (need == 3) {
            cp = (static_cast<uint32_t>(lead & 0x0F) << 12) |
                 (static_cast<uint32_t>(static_cast<unsigned char>(utf8[i + 1]) & 0x3F) << 6) |
                 (static_cast<unsigned char>(utf8[i + 2]) & 0x3F);
        } else {
            cp = (static_cast<uint32_t>(lead & 0x07) << 18) |
                 (static_cast<uint32_t>(static_cast<unsigned char>(utf8[i + 1]) & 0x3F) << 12) |
                 (static_cast<uint32_t>(static_cast<unsigned char>(utf8[i + 2]) & 0x3F) << 6) |
                 (static_cast<unsigned char>(utf8[i + 3]) & 0x3F);
        }

        if (cp <= 0xFFFF) {
            // 单独的代理区码点非法 → 替换
            if (cp >= 0xD800 && cp <= 0xDFFF) {
                utf16.push_back(static_cast<jchar>(0xFFFD));
            } else {
                utf16.push_back(static_cast<jchar>(cp));
            }
        } else if (cp <= 0x10FFFF) {
            // 代理对（emoji 等）
            uint32_t v = cp - 0x10000;
            utf16.push_back(static_cast<jchar>(0xD800 + (v >> 10)));
            utf16.push_back(static_cast<jchar>(0xDC00 + (v & 0x3FF)));
        } else {
            utf16.push_back(static_cast<jchar>(0xFFFD));
        }
        i += static_cast<size_t>(need);
    }

    if (utf16.empty()) {
        // 空串
        return env->NewString(nullptr, 0);
    }
    return env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
}

/**
 * ostream 适配：每次写入（token/片段）回调 Java onToken。
 * 关键：按 UTF-8 码点边界缓冲；回调用 NewString(UTF-16)，可安全传 emoji。
 * 返回 true 表示用户请求停止。
 */
class JavaStreamBuf : public std::streambuf {
public:
    explicit JavaStreamBuf(JNIEnv* env) : env_(env) {}

    ~JavaStreamBuf() override {
        // 析构时尽量把剩余完整序列吐出；残缺字节丢弃（避免 abort）
        flushComplete(true);
    }

protected:
    int_type overflow(int_type ch) override {
        if (ch != traits_type::eof()) {
            char c = static_cast<char>(ch);
            pending_.push_back(c);
            if (flushComplete(false)) {
                return traits_type::eof();
            }
        }
        return ch;
    }

    std::streamsize xsputn(const char* s, std::streamsize n) override {
        if (n <= 0) return n;
        pending_.append(s, static_cast<size_t>(n));
        if (flushComplete(false)) {
            return 0;
        }
        return n;
    }

private:
    /**
     * 从 pending_ 取出尽可能多的完整 UTF-8 码点回调。
     * @param endOfStream true 时丢弃尾部残缺字节
     * @return true = 用户请求停止
     */
    bool flushComplete(bool endOfStream) {
        if (pending_.empty()) return false;
        if (g_cancel.load()) {
            pending_.clear();
            return true;
        }

        size_t i = 0;
        const size_t n = pending_.size();
        size_t emitEnd = 0; // [0, emitEnd) 可安全 emit

        while (i < n) {
            auto lead = static_cast<unsigned char>(pending_[i]);
            if (lead == 0x00) {
                // 裸 0 替换为空格后计入可 emit
                pending_[i] = ' ';
                ++i;
                emitEnd = i;
                continue;
            }
            int need = utf8ExpectedLen(lead);
            if (need < 0) {
                // 非法首字节：替换为 U+FFFD 三字节，就地改写较复杂，直接跳过 1 字节不 emit
                // 为简单：把该字节替换成 '?' 后 emit
                pending_[i] = '?';
                ++i;
                emitEnd = i;
                continue;
            }
            if (i + static_cast<size_t>(need) > n) {
                // 不完整：保留 [i, n) 等待后续字节
                break;
            }
            bool ok = true;
            for (int k = 1; k < need; ++k) {
                if (!isUtf8Cont(static_cast<unsigned char>(pending_[i + static_cast<size_t>(k)]))) {
                    ok = false;
                    break;
                }
            }
            if (ok && need == 2 && lead < 0xC2) ok = false;
            if (ok && need == 4 && lead > 0xF4) ok = false;
            if (!ok) {
                pending_[i] = '?';
                ++i;
                emitEnd = i;
                continue;
            }
            i += static_cast<size_t>(need);
            emitEnd = i;
        }

        if (emitEnd > 0) {
            std::string piece = pending_.substr(0, emitEnd);
            pending_.erase(0, emitEnd);
            if (emitToJava(piece)) {
                pending_.clear();
                return true;
            }
        }

        if (endOfStream && !pending_.empty()) {
            // 尾部残缺（如半个 emoji）：丢弃，避免半码点
            ALOGW("drop incomplete utf8 tail bytes=%zu", pending_.size());
            pending_.clear();
        }
        return false;
    }

    bool emitToJava(const std::string& piece) {
        if (piece.empty()) return false;
        if (g_stream_listener == nullptr || g_on_token_mid == nullptr || env_ == nullptr) {
            return false;
        }
        jstring j = newStringUtfSafe(env_, piece);
        if (j == nullptr) {
            if (env_->ExceptionCheck()) env_->ExceptionClear();
            return false;
        }
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
    std::string pending_;
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
        return newStringUtfSafe(env, out);
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
        return newStringUtfSafe(env, out);
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
        return newStringUtfSafe(env, out);
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
    return newStringUtfSafe(env, g_last_error);
}

JNIEXPORT jboolean JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeIsLoaded(
        JNIEnv* /* env */,
        jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_llm != nullptr ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
