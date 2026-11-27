# ScamSense R8 rules.
# The app uses no reflection-based serialization; default Compose/AndroidX consumer
# rules are bundled with their libraries.

# Strip verbose/debug logging from release builds so no user content can leak to logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep line numbers for readable crash traces, but hide original source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Shizuku starts the call-audio helper by class name in a separate shell-uid process.
-keep class com.scamsense.app.livecall.shell.** { *; }
-keep class com.scamsense.app.livecall.ICallAudioService* { *; }
# JNI entry points.
-keep class com.scamsense.app.livecall.WhisperNative { native <methods>; *; }
