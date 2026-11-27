// JNI bridge for live call transcription. Short utterances in, text out.
// whisper.cpp is MIT-licensed (third_party/whisper.cpp/LICENSE).
#include <jni.h>
#include <android/log.h>
#include <string>
#include <mutex>
#include "whisper.h"

#define TAG "SS:Whisper"

static std::mutex g_lock;  // one transcription at a time per process

extern "C" JNIEXPORT jlong JNICALL
Java_com_scamsense_app_livecall_WhisperNative_nativeInit(JNIEnv *env, jobject, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(jpath, path);
    if (!ctx) __android_log_print(ANDROID_LOG_WARN, TAG, "model load failed");
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_scamsense_app_livecall_WhisperNative_nativeFree(JNIEnv *, jobject, jlong ptr) {
    std::lock_guard<std::mutex> g(g_lock);
    if (ptr) whisper_free(reinterpret_cast<whisper_context *>(ptr));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_scamsense_app_livecall_WhisperNative_nativeTranscribe(
        JNIEnv *env, jobject, jlong ptr, jfloatArray jpcm, jstring jlang, jint threads) {
    if (!ptr || !jpcm) return env->NewStringUTF("");
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    const jsize n = env->GetArrayLength(jpcm);
    if (n <= 0 || n > 16000 * 30) return env->NewStringUTF("");  // ≤ 30 s per call

    const char *lang = env->GetStringUTFChars(jlang, nullptr);
    std::string language(lang ? lang : "th");
    env->ReleaseStringUTFChars(jlang, lang);

    jfloat *pcm = env->GetFloatArrayElements(jpcm, nullptr);

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.language = language.c_str();
    p.translate = false;
    p.n_threads = threads > 0 ? threads : 4;
    p.no_context = true;        // each utterance stands alone: no drift, less hallucination
    p.no_timestamps = true;
    p.single_segment = false;
    p.suppress_blank = true;
    p.suppress_nst = true;      // drop non-speech tokens ([music], etc.)
    p.temperature = 0.0f;
    p.print_progress = false;
    p.print_realtime = false;
    p.print_special = false;
    p.print_timestamps = false;

    std::string text;
    {
        std::lock_guard<std::mutex> g(g_lock);
        if (whisper_full(ctx, p, pcm, n) == 0) {
            const int segs = whisper_full_n_segments(ctx);
            for (int i = 0; i < segs; ++i) {
                const char *t = whisper_full_get_segment_text(ctx, i);
                if (t) text += t;
            }
        }
    }
    env->ReleaseFloatArrayElements(jpcm, pcm, JNI_ABORT);
    return env->NewStringUTF(text.c_str());
}
