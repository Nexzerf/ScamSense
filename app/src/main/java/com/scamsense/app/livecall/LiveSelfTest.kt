package com.scamsense.app.livecall

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.scamsense.core.engine.AnalysisOutcome
import com.scamsense.core.engine.CheckType
import com.scamsense.core.engine.RiskResult
import com.scamsense.core.engine.ScamAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Speak into the phone" check of the whole listening chain without a phone call:
 * shell-uid helper process (via Shizuku) → audio pipe → whisper.cpp → scam engine. It uses the microphone through
 * the same shell helper, so it proves Shizuku, the model and the speed on this phone.
 */
@RequiresApi(Build.VERSION_CODES.R)
object LiveSelfTest {

    sealed interface Outcome {
        data class Heard(val text: String, val seconds: Float, val audioSeconds: Float, val result: RiskResult?) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    private const val RATE = 16_000

    suspend fun run(context: Context, model: WhisperModel, recordSeconds: Int = 6, onPhase: (String) -> Unit = {}): Outcome {
        if (!WhisperNative.isAvailable) return Outcome.Failed("เครื่องนี้ไม่รองรับตัวแปลงเสียง")
        if (!WhisperModels.isInstalled(context, model)) return Outcome.Failed("ยังไม่ได้ดาวน์โหลดโมเดล")
        if (!ShellAudio.isAvailable(context)) return Outcome.Failed("ยังไม่ได้จับคู่ (ทำข้อ \"จับคู่ในแอป\" ด้านบนก่อน)")

        onPhase("กำลังเชื่อมต่อตัวดึงเสียง… (ยังไม่ต้องพูด)")
        val opened = withContext(Dispatchers.IO) { ShellAudio.open(context, MediaRecorder.AudioSource.MIC, RATE) }
        val stream = when (opened) {
            is ShellAudio.Opened.Failed -> return Outcome.Failed(
                "เชื่อมต่อตัวดึงเสียงผ่าน Shizuku ไม่สำเร็จ (รหัส: ${opened.reason}) ถ้ายังไม่ได้ ให้ส่งรหัสนี้ให้ผู้พัฒนา",
            )
            is ShellAudio.Opened.Ok -> opened.stream
        }
        try {
            onPhase("พูดเลย! กำลังฟัง $recordSeconds วินาที")
            val pcm = withContext(Dispatchers.IO) { record(stream, recordSeconds) }
            var peak = 0
            for (v in pcm) { val a = kotlin.math.abs(v.toInt()); if (a > peak) peak = a }
            if (peak < 200) return Outcome.Failed("ไม่ได้ยินเสียงเลย ลองพูดดังขึ้นใกล้ไมโครโฟน")

            onPhase("กำลังแปลงเสียงเป็นข้อความ…")
            val floats = FloatArray(pcm.size) { pcm[it] / 32768f }
            return withContext(Dispatchers.Default) {
                val started = SystemClock.elapsedRealtime()
                val text = WhisperNative.load(WhisperModels.file(context, model).path)?.use { it.transcribe(floats, "th") }
                    ?: return@withContext Outcome.Failed("โหลดโมเดลแปลงเสียงไม่สำเร็จ ลองลบแล้วดาวน์โหลดใหม่")
                val secs = (SystemClock.elapsedRealtime() - started) / 1000f
                val result = (ScamAnalyzer().analyze(CheckType.MESSAGE, text, speech = true) as? AnalysisOutcome.Success)?.result
                Outcome.Heard(text, secs, pcm.size / RATE.toFloat(), result)
            }
        } finally {
            withContext(Dispatchers.IO) { stream.close() }
        }
    }

    private fun record(stream: ShellAudio.Stream, seconds: Int): ShortArray {
        val want = RATE * 2 * seconds
        val bytes = ByteArray(want)
        var got = 0
        val input = stream.input
        while (got < want) {
            val n = input.read(bytes, got, want - got)
            if (n < 0) break
            got += n
        }
        val out = ShortArray(got / 2)
        for (i in out.indices) out[i] = ((bytes[2 * i + 1].toInt() shl 8) or (bytes[2 * i].toInt() and 0xff)).toShort()
        return out
    }
}
