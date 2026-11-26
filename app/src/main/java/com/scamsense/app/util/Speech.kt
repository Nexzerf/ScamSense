package com.scamsense.app.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Speech-to-text through the system recogniser Activity: the OS shows its own mic UI,
 * so ScamSense never needs the RECORD_AUDIO permission.
 */
class SpeechInput(private val launch: () -> Boolean) {
    /** @return false if the device has no speech recogniser. */
    fun start(): Boolean = launch()
}

@Composable
fun rememberSpeechInput(onResult: (String) -> Unit): SpeechInput {
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { callback(it) }
        }
    }
    return remember(launcher) {
        SpeechInput {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "th-TH")
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "th-TH")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "พูดข้อความที่ได้รับ หรือสิ่งที่ปลายสายบอก")
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            try {
                launcher.launch(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }
}

/** Reads results aloud in Thai, for users who find small text hard to read. */
class Speaker internal constructor() {
    internal var tts: TextToSpeech? = null
    var available by mutableStateOf(false)
        internal set
    var speaking by mutableStateOf(false)
        internal set

    fun speak(text: String) {
        val engine = tts ?: return
        if (!available) return
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "scamsense-result")
        speaking = true
    }

    fun stop() {
        tts?.stop()
        speaking = false
    }
}

@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current.applicationContext
    val speaker = remember { Speaker() }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            val e = engine ?: return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                val thai = Locale("th", "TH")
                val res = e.setLanguage(thai)
                speaker.available = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                e.setSpeechRate(0.9f) // a little slower for clarity
                e.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { speaker.speaking = false }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { speaker.speaking = false }
                })
            }
        }
        speaker.tts = engine
        onDispose {
            speaker.available = false
            speaker.tts = null
            engine?.stop()
            engine?.shutdown()
        }
    }
    return speaker
}
