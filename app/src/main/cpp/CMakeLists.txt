# LanXin thin JNI over prebuilt MNN LLM (libllm.so + libMNN*.so).
# Prebuilts are downloaded into app/src/main/jniLibs/<abi>/ by Gradle.

cmake_minimum_required(VERSION 3.22.1)
project(mnn_lanxin)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(MNN_HEADERS "${CMAKE_CURRENT_SOURCE_DIR}/../../../third_party/mnn/include")
set(MNN_JNI_LIBS "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

if (ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "armeabi-v7a")
    if (NOT EXISTS "${MNN_JNI_LIBS}/libllm.so")
        message(FATAL_ERROR "Missing prebuilt libllm.so under ${MNN_JNI_LIBS}. Run :app:downloadMnnNative first.")
    endif()

    add_library(mnn_lanxin SHARED mnn_lanxin_jni.cpp)

    target_include_directories(mnn_lanxin PRIVATE
        ${MNN_HEADERS}
    )

    # Prebuilt shared libs (also packaged via jniLibs)
    foreach(lib IN ITEMS llm MNN MNN_Express MNN_CL MNN_Vulkan MNNOpenCV MNNAudio)
        add_library(${lib} SHARED IMPORTED)
        set_target_properties(${lib} PROPERTIES
            IMPORTED_LOCATION "${MNN_JNI_LIBS}/lib${lib}.so"
        )
    endforeach()

    find_library(log-lib log)
    find_library(android-lib android)

    target_link_libraries(mnn_lanxin
        llm
        MNN_Express
        MNN
        MNN_CL
        MNN_Vulkan
        MNNOpenCV
        MNNAudio
        ${log-lib}
        ${android-lib}
    )
else()
    # x86_64 / others: stub .so so APK still builds; Kotlin degrades safely
    add_library(mnn_lanxin SHARED mnn_lanxin_jni_stub.cpp)
    find_library(log-lib log)
    target_link_libraries(mnn_lanxin ${log-lib})
endif()
