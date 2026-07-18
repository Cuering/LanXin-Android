@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.kotlin.dsl.aboutLibraries
import org.gradle.kotlin.dsl.configure
import java.net.URI
import java.nio.file.Files

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
    // ObjectBox 代码生成依赖 kapt（官方示例要求 kapt + objectbox 插件）
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.auto.license)
    alias(libs.plugins.objectbox)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21"
}

// ---------------------------------------------------------------------------
// P0: sherpa-onnx Android 运行时 AAR（构建期下载，不进 git）
// 默认 static-link-onnxruntime，减少与 ORT Mobile 的 so 分片依赖。
// 覆盖: SHERPA_ONNX_AAR=本地路径 或 SHERPA_ONNX_AAR_URL=下载 URL
// ---------------------------------------------------------------------------
val sherpaOnnxVersion = "1.13.4"
val sherpaAarFileName = "sherpa-onnx-static-link-onnxruntime-$sherpaOnnxVersion.aar"
val sherpaAarLocal = layout.projectDirectory.file("libs/$sherpaAarFileName").asFile
val sherpaDefaultUrl =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion/$sherpaAarFileName"
// 国内 / 弱网 CI 可走镜像；官方 URL 优先，失败回退
val sherpaMirrorUrl =
    "https://ghfast.top/https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion/$sherpaAarFileName"

val downloadSherpaOnnxAar by tasks.registering {
    group = "lanxin"
    description = "Download sherpa-onnx Android AAR into app/libs (not committed)"
    outputs.file(sherpaAarLocal)
    onlyIf {
        val override = System.getenv("SHERPA_ONNX_AAR")
        if (!override.isNullOrBlank()) {
            val src = file(override)
            if (src.isFile && src.length() > 1_000_000L) {
                if (src.absolutePath != sherpaAarLocal.absolutePath) {
                    sherpaAarLocal.parentFile.mkdirs()
                    src.copyTo(sherpaAarLocal, overwrite = true)
                }
                return@onlyIf false
            }
        }
        !(sherpaAarLocal.isFile && sherpaAarLocal.length() > 1_000_000L)
    }
    doLast {
        sherpaAarLocal.parentFile.mkdirs()
        val envUrl = System.getenv("SHERPA_ONNX_AAR_URL")
        val urls = buildList {
            if (!envUrl.isNullOrBlank()) add(envUrl)
            add(sherpaDefaultUrl)
            add(sherpaMirrorUrl)
        }
        var lastError: Exception? = null
        for (url in urls) {
            try {
                logger.lifecycle("Downloading sherpa-onnx AAR from $url")
                URI(url).toURL().openStream().use { input ->
                    Files.copy(input, sherpaAarLocal.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
                require(sherpaAarLocal.isFile && sherpaAarLocal.length() > 1_000_000L) {
                    "Downloaded AAR too small: ${sherpaAarLocal.length()}"
                }
                logger.lifecycle("sherpa-onnx AAR ready: ${sherpaAarLocal} (${sherpaAarLocal.length()} bytes)")
                return@doLast
            } catch (e: Exception) {
                lastError = e
                logger.warn("Failed $url: ${e.message}")
                sherpaAarLocal.delete()
            }
        }
        throw GradleException(
            "Unable to download sherpa-onnx AAR. Set SHERPA_ONNX_AAR or SHERPA_ONNX_AAR_URL. Last error: ${lastError?.message}"
        )
    }
}

// 在配置期若本地已有 AAR 则直接用；否则 preBuild 会下载
val sherpaAarForCompile: File = when {
    !System.getenv("SHERPA_ONNX_AAR").isNullOrBlank() &&
        file(System.getenv("SHERPA_ONNX_AAR")!!).isFile ->
        file(System.getenv("SHERPA_ONNX_AAR")!!)
    sherpaAarLocal.isFile && sherpaAarLocal.length() > 1_000_000L -> sherpaAarLocal
    else -> sherpaAarLocal
}

extensions.configure<ApplicationExtension> {
    namespace = "com.lanxin.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lanxin.android"
        minSdk = 31
        targetSdk = 37
        versionCode = 22
        versionName = "0.7.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // 真机为主；保留 x86_64 供模拟器 / CI 体积可控
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    lint {
        disable += "MissingTranslation"
    }

    signingConfigs {
        getByName("debug") {
            val debugKeystore = rootProject.file("debug-keystore.jks")
            if (debugKeystore.exists()) {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
        jniLibs {
            // onnxruntime-android 与 sherpa AAR 可能各带 libonnxruntime.so
            pickFirsts += setOf(
                "lib/arm64-v8a/libonnxruntime.so",
                "lib/x86_64/libonnxruntime.so",
                "lib/armeabi-v7a/libonnxruntime.so",
                "lib/x86/libonnxruntime.so"
            )
        }
    }
    testOptions {
        // JVM 单测对 android.* 方法返回默认值，避免 Log 等未 mock 导致失败
        unitTests.isReturnDefaultValues = true
    }
}

// 保证 preBuild / 编译前 AAR 就绪
tasks.named("preBuild").configure { dependsOn(downloadSherpaOnnxAar) }
afterEvaluate {
    listOf(
        "compileDebugKotlin",
        "compileReleaseKotlin",
        "compileDebugUnitTestKotlin",
        "compileReleaseUnitTestKotlin"
    ).forEach { name ->
        tasks.findByName(name)?.dependsOn(downloadSherpaOnnxAar)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// kapt 与 KSP 共存：Room/Hilt 用 KSP，ObjectBox 用 kapt
// markdown-renderer / highlights 为 Java 21 字节码，kapt stub 需用 21 解析
kapt {
    correctErrorTypes = true
    javacOptions {
        option("-source", "21")
        option("-target", "21")
    }
}

dependencies {
    // Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // SplashScreen
    implementation(libs.splashscreen)

    // DataStore
    implementation(libs.androidx.datastore)

    // Dependency Injection
    implementation(libs.hilt)
    implementation(libs.androidx.lifecycle.runtime.compose.android)
    ksp(libs.hilt.compiler)

    // Ktor (SSE is built into ktor-client-core 3.x, no separate artifact)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.logging)
    implementation(libs.ktor.serialization)

    // License page UI
    implementation(libs.auto.license.core)
    implementation(libs.auto.license.ui)

    // Markdown
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.code)

    // Navigation
    implementation(libs.hilt.navigation)
    implementation(libs.androidx.navigation)

    // Room
    implementation(libs.room)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    // WorkManager + Hilt Worker（scheduler 模块）
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // ONNX Runtime Mobile（GTE-small 推理）
    implementation(libs.onnxruntime.android)

    // sherpa-onnx ASR 运行时（AAR 构建期下载到 app/libs）
    if (sherpaAarForCompile.isFile && sherpaAarForCompile.length() > 1_000_000L) {
        implementation(files(sherpaAarForCompile))
    } else {
        // 配置期尚无文件时仍声明 files 路径；download 任务在 preBuild 填充
        implementation(files(sherpaAarLocal))
    }

    // ObjectBox VectorDB（HNSW 向量检索）
    // objectbox 插件会自动添加 objectbox-processor 到 kapt
    implementation(libs.objectbox.android)
    implementation(libs.objectbox.kotlin)

    // Serialization
    implementation(libs.kotlin.serialization)

    // Debug asset archive extract (tar.bz2 / xz)
    implementation(libs.commons.compress)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.00"))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

aboutLibraries {
    // Remove the "generated" timestamp to allow for reproducible builds
    export {
        excludeFields.add("generated")
    }
}
