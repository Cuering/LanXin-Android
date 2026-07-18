# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep reactive streams dependencies
-keep class reactor.** { *; }
-keep class io.micrometer.** { *; }
-dontwarn io.micrometer.**
-dontwarn reactor.**

# Ignore missing optional logging dependencies used by Netty
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# sherpa-onnx (JNI + Kotlin API in official AAR)
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# MNN native
-keep class com.lanxin.android.builtin.localinference.data.MnnNativeBridge { *; }
-keepclassmembers class com.lanxin.android.builtin.localinference.data.MnnNativeBridge {
    native <methods>;
}
-keep class MNN.** { *; }
-dontwarn MNN.**
-keep class MNNExpress.** { *; }
-dontwarn MNNExpress.**

# ObjectBox
-keep class io.objectbox.** { *; }
-keep class com.lanxin.android.builtin.knowledge.data.** { *; }
-keepclassmembers class * {
    @io.objectbox.annotation.* <fields>;
}
-dontwarn io.objectbox.**

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
