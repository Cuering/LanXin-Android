# LanXin thin JNI over prebuilt MNN LLM (libllm.so + libMNN*.so).
# Prebuilts are downloaded into app/src/main/jniLibs/<abi>/ by Gradle.

cmake_minimum_required(VERSION 3.22.1)
project(mnn_lanxin)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# app/src/main/cpp → main → src → app → repo root
set(MNN_HEADERS "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/mnn/include")
set(MNN_JNI_LIBS "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

if (NOT EXISTS "${MNN_HEADERS}/llm/llm.hpp")
    message(FATAL_ERROR
        "Missing MNN LLM headers at ${MNN_HEADERS}/llm/llm.hpp "
        "(expected third_party/mnn/include from repo root).")
endif()

if (ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "armeabi-v7a")
    if (NOT EXISTS "${MNN_JNI_LIBS}/libllm.so")
        message(FATAL_ERROR
            "Missing prebuilt libllm.so under ${MNN_JNI_LIBS}. "
            "Run :app:downloadMnnNative first.")
    endif()

    add_library(mnn_lanxin SHARED mnn_lanxin_jni.cpp)

    target_include_directories(mnn_lanxin PRIVATE
        ${MNN_HEADERS}
    )

    # Core + Express + llm always required for Transformer::Llm
    set(MNN_CORE_LIBS llm MNN MNN_Express)
    # Official android zip may ship OpenCL/Vulkan/OpenCV/Audio; link if present
    set(MNN_OPTIONAL_LIBS MNN_CL MNN_Vulkan MNNOpenCV MNNAudio)

    set(MNN_LINK_LIBS "")
    foreach(lib IN LISTS MNN_CORE_LIBS)
        if (NOT EXISTS "${MNN_JNI_LIBS}/lib${lib}.so")
            message(FATAL_ERROR "Missing required prebuilt lib${lib}.so under ${MNN_JNI_LIBS}")
        endif()
        add_library(${lib} SHARED IMPORTED)
        set_target_properties(${lib} PROPERTIES
            IMPORTED_LOCATION "${MNN_JNI_LIBS}/lib${lib}.so"
        )
        list(APPEND MNN_LINK_LIBS ${lib})
    endforeach()

    foreach(lib IN LISTS MNN_OPTIONAL_LIBS)
        if (EXISTS "${MNN_JNI_LIBS}/lib${lib}.so")
            add_library(${lib} SHARED IMPORTED)
            set_target_properties(${lib} PROPERTIES
                IMPORTED_LOCATION "${MNN_JNI_LIBS}/lib${lib}.so"
            )
            list(APPEND MNN_LINK_LIBS ${lib})
        else()
            message(STATUS "Optional prebuilt lib${lib}.so not found; skip link")
        endif()
    endforeach()

    find_library(log-lib log)
    find_library(android-lib android)

    target_link_libraries(mnn_lanxin
        ${MNN_LINK_LIBS}
        ${log-lib}
        ${android-lib}
    )
else()
    # x86_64 / others: stub .so so APK still builds; Kotlin degrades safely
    add_library(mnn_lanxin SHARED mnn_lanxin_jni_stub.cpp)
    find_library(log-lib log)
    target_link_libraries(mnn_lanxin ${log-lib})
endif()
