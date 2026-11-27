package com.scamsense.core.live

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Splits a live 16 kHz mono PCM stream (the caller's side of a phone call) into short
 * utterances for speech-to-text, so transcription can run *during* the call.
 *
 * A simple energy voice-activity detector with an adaptive noise floor:
 *  - an utterance ends after [endSilenceMs] of silence, or is cut at [maxSegmentMs]
 *    (keeping [overlapMs] of audio so a word on the boundary is not lost);
 *  - utterances with less than [minSpeechMs] of speech are dropped (clicks, breaths).
 *
 * Pure Kotlin, allocation-light, unit-tested on the JVM.
 */
class SpeechChunker(
    private val sampleRate: Int = 16_000,
    private val frameMs: Int = 20,
    private val endSilenceMs: Int = 650,
    private val maxSegmentMs: Int = 7_000,
    private val minSpeechMs: Int = 500,
    private val overlapMs: Int = 400,
) {
    private val frameLen = sampleRate * frameMs / 1000
    private val pending = ShortArray(frameLen)
    private var pendingLen = 0

    private val segment = ArrayList<ShortArray>()
    private var speechFrames = 0
    private var silenceFrames = 0
    private var inSpeech = false
    private var noiseFloor = 300.0

    /** Feed raw samples; returns finished utterances as float PCM in [-1, 1]. */
    fun feed(samples: ShortArray, length: Int = samples.size): List<FloatArray> {
        val out = ArrayList<FloatArray>(1)
        var i = 0
        while (i < length) {
            val n = minOf(frameLen - pendingLen, length - i)
            System.arraycopy(samples, i, pending, pendingLen, n)
            pendingLen += n
            i += n
            if (pendingLen == frameLen) {
                onFrame(pending.copyOf())?.let(out::add)
                pendingLen = 0
            }
        }
        return out
    }

    /** Call when the stream ends (call hung up) to flush a final utterance. */
    fun flush(): FloatArray? = emit(force = true)

    private fun onFrame(frame: ShortArray): FloatArray? {
        val e = rms(frame)
        val speech = e > max(noiseFloor * 3.0, MIN_SPEECH_RMS)
        if (!speech) noiseFloor = noiseFloor * 0.95 + e * 0.05 // track background noise
        if (speech) {
            inSpeech = true
            speechFrames++
            silenceFrames = 0
            segment += frame
        } else if (inSpeech) {
            silenceFrames++
            segment += frame
            if (silenceFrames * frameMs >= endSilenceMs) return emit(force = false)
        }
        if (segment.size * frameMs >= maxSegmentMs) return emit(force = false, keepOverlap = true)
        return null
    }

    @Suppress("UNUSED_PARAMETER")
    private fun emit(force: Boolean, keepOverlap: Boolean = false): FloatArray? {
        val enoughSpeech = speechFrames * frameMs >= minSpeechMs
        val result = if (segment.isNotEmpty() && enoughSpeech) {
            val total = segment.sumOf { it.size }
            FloatArray(total).also { f ->
                var p = 0
                for (fr in segment) for (s in fr) f[p++] = s / 32768f
            }
        } else null
        val keep = if (keepOverlap) segment.takeLast(overlapMs / frameMs) else emptyList()
        segment.clear()
        segment.addAll(keep)
        speechFrames = 0
        silenceFrames = 0
        inSpeech = keepOverlap && keep.isNotEmpty()
        return result
    }

    private fun rms(frame: ShortArray): Double {
        var sum = 0.0
        for (s in frame) sum += s.toDouble() * s
        return sqrt(sum / frame.size)
    }

    private companion object {
        /** Below this RMS (≈ -50 dBFS) nothing is treated as speech, whatever the noise floor. */
        const val MIN_SPEECH_RMS = 100.0
    }
}
