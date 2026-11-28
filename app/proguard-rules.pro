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

# The call-audio helper is started by class name (app_process main) in a shell-uid process.
-keep class com.scamsense.app.livecall.shell.** { *; }
# Shizuku.newProcess is private in Shizuku 13 and reached by reflection (see ShellAudio).
-keep class rikka.shizuku.Shizuku { private static *** newProcess(...); }
-keep class rikka.shizuku.ShizukuRemoteProcess { *; }
# JNI entry points.
-keep class com.scamsense.app.livecall.WhisperNative { native <methods>; *; }
