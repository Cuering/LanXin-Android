package com.lanxin.android.builtin.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lanxin.android.builtin.voice.data.SherpaOnnxBridge
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented 说明性测试（非强制 CI 设备任务）。
 *
 * 真机/模拟器上若 APK 含 libsherpa-onnx-jni.so，则 [SherpaOnnxBridge.isNativeAvailable]
 * 应为 true。完整 load 需外置模型目录（LanXin/asr/...），不在此包内。
 *
 * 运行：
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=\
 *     com.lanxin.android.builtin.voice.SherpaNativeInstrumentedNote
 */
@RunWith(AndroidJUnit4::class)
class SherpaNativeInstrumentedNote {

    @Test
    fun nativeLibraryProbeDoesNotCrash() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(appContext.packageName)
        val bridge = SherpaOnnxBridge()
        // 仅探测；结果取决于 APK 是否打入 so
        val available = bridge.isNativeAvailable()
        // 打印到 logcat 便于手工验收
        android.util.Log.i(
            "SherpaNativeNote",
            "isNativeAvailable=$available err=${bridge.nativeLoadError()}"
        )
    }
}
