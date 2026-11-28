// On-device model shrinking: converts an imported f16 Whisper model (for example a
// Thonburian Whisper conversion) to q5_1, which is ~2.5x smaller and much faster on phones
// whose CPUs have no FP16 arithmetic. Reuses whisper.cpp's own quantizer (MIT) verbatim:
// the example's main() is renamed so only whisper_model_quantize() is used.
#include <jni.h>
#include <android/log.h>
#include <string>

#define main ss_whisper_quantize_example_main
#include "quantize/quantize.cpp"
#undef main

extern "C" JNIEXPORT jboolean JNICALL
Java_com_scamsense_app_livecall_WhisperNative_nativeQuantize(JNIEnv *env, jclass, jstring jin, jstring jout) {
    const char *in = env->GetStringUTFChars(jin, nullptr);
    const char *out = env->GetStringUTFChars(jout, nullptr);
    std::string fin(in), fout(out);
    env->ReleaseStringUTFChars(jin, in);
    env->ReleaseStringUTFChars(jout, out);

    // needed to initialize the f16 conversion tables
    {
        struct ggml_init_params params = { 0, NULL, false };
        struct ggml_context *ctx = ggml_init(params);
        ggml_free(ctx);
    }
    const bool ok = whisper_model_quantize(fin, fout, GGML_FTYPE_MOSTLY_Q5_1);
    if (!ok) __android_log_print(ANDROID_LOG_WARN, "SS:Whisper", "quantize failed");
    return ok ? JNI_TRUE : JNI_FALSE;
}
