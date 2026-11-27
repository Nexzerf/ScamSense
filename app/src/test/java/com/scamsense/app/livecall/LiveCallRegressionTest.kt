package com.scamsense.app.livecall

import android.telephony.TelephonyManager.CALL_STATE_IDLE
import android.telephony.TelephonyManager.CALL_STATE_OFFHOOK
import android.telephony.TelephonyManager.CALL_STATE_RINGING
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scamsense.app.data.HistoryRepository
import com.scamsense.app.data.InMemoryBlobStore
import com.scamsense.core.engine.AnalysisOutcome
import com.scamsense.core.engine.CheckType
import com.scamsense.core.engine.ScamAnalyzer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveCallRegressionTest {

    @Test fun idleBeforeRingingIsNotTreatedAsCallEnd() {
        val t = CallPhaseTracker()
        // Registration happens before the phone rings: first callback is IDLE.
        assertEquals(CallPhaseTracker.Event.NONE, t.on(CALL_STATE_IDLE))
        assertEquals(CallPhaseTracker.Event.NONE, t.on(CALL_STATE_RINGING))
        assertEquals(CallPhaseTracker.Event.ANSWERED, t.on(CALL_STATE_OFFHOOK))
        assertEquals(CallPhaseTracker.Event.NONE, t.on(CALL_STATE_OFFHOOK))
        assertEquals(CallPhaseTracker.Event.ENDED, t.on(CALL_STATE_IDLE))
        assertEquals(CallPhaseTracker.Event.NONE, t.on(CALL_STATE_IDLE))
    }

    @Test fun missedCallEndsAfterRinging() {
        val t = CallPhaseTracker()
        t.on(CALL_STATE_IDLE)
        t.on(CALL_STATE_RINGING)
        assertEquals(CallPhaseTracker.Event.ENDED, t.on(CALL_STATE_IDLE))
    }

    @Test fun backgroundWriteDoesNotWipeUnloadedHistory() = runBlocking {
        val store = InMemoryBlobStore()
        val r = (ScamAnalyzer().analyze(CheckType.MESSAGE, "ขอรหัส OTP ด่วน") as AnalysisOutcome.Success).result
        // A previous app session saved two checks.
        HistoryRepository(store).apply { load(); add(r.copy(analyzedAt = 1)); add(r.copy(analyzedAt = 2)) }
        // Fresh process: the live-call service adds a result before the UI ever loaded history.
        val background = HistoryRepository(store)
        background.add(r.copy(analyzedAt = 3))
        val reread = HistoryRepository(store).apply { load() }
        assertEquals(listOf(3L, 2L, 1L), reread.items.value.map { it.analyzedAt })
    }
}
