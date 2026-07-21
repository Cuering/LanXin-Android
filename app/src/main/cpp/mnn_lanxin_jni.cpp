/*
 * Thin JNI bridge: MNN::Transformer::Llm (libllm.so).
 * Package: com.lanxin.android.builtin.localinference.data.MnnNativeBridge
 */

#include <android/log.h>
#include <jni.h>

#include <memory>
#include <mutex>
#include <sstream>
#include <string>

#include "llm/llm.hpp"

#define LOG_TAG "MnnLanxinJni"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using MNN::Transformer::Llm;

namespace {

std::mutex g_mutex;
Llm* g_llm = nullptr;
std::string g_last_error;

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
}

/** Resolve model dir / config.json / llm.mnn → path expected by createLLM. */
std::string resolveConfigPath(const std::string& path) {
    if (path.empty()) return path;
    // If path ends with .json or .mnn, createLLM accepts both
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
    // Prefer explicit config; else pass directory (LlmConfig defaults llm.mnn)
    const std::string candidates[] = {
        base + "/config.json",
        base + "/llm_config.json",
        base + "/llm.mnn.json",
        base + "/llm.mnn",
        base // directory → LlmConfig uses base_dir + default filenames
    };
    for (const auto& c : candidates) {
        if (c == base) {
            // Directory must contain at least llm.mnn or config
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

} // namespace

extern "C" {

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

    // Fast path for Adreno (e.g. Redmi K70): OpenCL + multi-thread + low precision.
    // Fall back to CPU if OpenCL load fails (missing so / driver / unsupported op).
    static const char* kGpuRuntimeCfg =
            R"({"backend_type":"opencl","thread_num":6,"precision":"low","memory":"low","reuse_kv":true})";
    static const char* kCpuRuntimeCfg =
            R"({"backend_type":"cpu","thread_num":6,"precision":"low","memory":"low","reuse_kv":true})";

    auto tryCreateAndLoad = [&](const char* runtimeCfg, const char* label) -> Llm* {
        Llm* llm = nullptr;
        try {
            llm = Llm::createLLM(configPath);
            if (llm == nullptr) {
                ALOGE("createLLM_null:%s label=%s", configPath.c_str(), label);
                return nullptr;
            }
            // Best-effort: merge runtime knobs (model config.json still provides weights/paths).
            if (!llm->set_config(runtimeCfg)) {
                ALOGI("set_config soft-fail label=%s (continue load)", label);
            }
            if (!llm->load()) {
                ALOGE("llm_load_failed label=%s path=%s", label, configPath.c_str());
                Llm::destroy(llm);
                return nullptr;
            }
            ALOGI("nativeLoadModel ok label=%s", label);
            return llm;
        } catch (const std::exception& e) {
            ALOGE("load_exception label=%s: %s", label, e.what());
            if (llm != nullptr) {
                Llm::destroy(llm);
            }
            return nullptr;
        } catch (...) {
            ALOGE("load_exception label=%s: unknown", label);
            if (llm != nullptr) {
                Llm::destroy(llm);
            }
            return nullptr;
        }
    };

    try {
        Llm* llm = tryCreateAndLoad(kGpuRuntimeCfg, "opencl");
        if (llm == nullptr) {
            ALOGI("opencl path failed, fallback cpu");
            llm = tryCreateAndLoad(kCpuRuntimeCfg, "cpu");
        }
        if (llm == nullptr) {
            setError("llm_load_failed:" + configPath);
            return JNI_FALSE;
        }
        g_llm = llm;
        return JNI_TRUE;
    } catch (const std::exception& e) {
        setError(std::string("load_exception:") + e.what());
        return JNI_FALSE;
    } catch (...) {
        setError("load_exception:unknown");
        return JNI_FALSE;
    }
}

JNIEXPORT jstring JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeGenerate(
        JNIEnv* env,
        jobject /* thiz */,
        jstring promptJ,
        jint maxTokens) {
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

    try {
        // Prefer chat-template aware response when config has template
        std::ostringstream os;
        g_llm->response(prompt, &os, nullptr, maxNew);
        std::string out = os.str();
        // Fallback: context generate_str if stream empty
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

JNIEXPORT void JNICALL
Java_com_lanxin_android_builtin_localinference_data_MnnNativeBridge_nativeUnload(
        JNIEnv* /* env */,
        jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(g_mutex);
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
