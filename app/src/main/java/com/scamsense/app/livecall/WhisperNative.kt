package com.scamsense.app.livecall

import java.io.Closeable

/** Thin wrapper over whisper.cpp. Not available on every ABI/device: check [isAvailable]. */
class WhisperNative private constructor(private var ctx: Long) : Closeable {

    /** Transcribes one utterance (16 kHz mono float PCM). Blocking; call off the main thread. */
    fun transcribe(pcm: FloatArray, language: String = "th", threads: Int = defaultThreads()): String {
        val c = ctx
        if (c == 0L) return ""
        return nativeTranscribe(c, pcm, language, threads).trim()
    }

    override fun close() {
        val c = ctx
        ctx = 0
        if (c != 0L) nativeFree(c)
    }

    private external fun nativeTranscribe(ptr: Long, pcm: FloatArray, language: String, threads: Int): String
    private external fun nativeFree(ptr: Long)

    companion object {
        val isAvailable: Boolean = runCatching { System.loadLibrary("scamsense_whisper"); true }.getOrDefault(false)

        fun load(modelPath: String): WhisperNative? {
            if (!isAvailable) return null
            val ptr = nativeInit(modelPath)
            return if (ptr != 0L) WhisperNative(ptr) else null
        }

        /** Leave a couple of cores for the phone call itself and the UI. */
        fun defaultThreads(): Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)

        /** Static JNI symbol: Java_com_scamsense_app_livecall_WhisperNative_nativeInit */
        @JvmStatic external fun nativeInit(path: String): Long
    }
}
