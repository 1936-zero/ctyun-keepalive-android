# Chaquopy / Python bridge
-keep class com.chaquo.python.** { *; }
-keep class com.monkeycode.ctyunkeepalive.ocr.** { *; }

# Gson model serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.monkeycode.ctyunkeepalive.core.** { *; }

# Retrofit annotations and interfaces
-keep interface com.monkeycode.ctyunkeepalive.network.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ONNX Runtime and JNI entrypoints
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }

# MMKV
-keep class com.tencent.mmkv.** { *; }
