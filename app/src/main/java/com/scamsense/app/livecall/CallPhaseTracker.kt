package com.scamsense.app.livecall

import android.telephony.TelephonyManager

/**
 * Turns raw call-state callbacks into "answered" / "ended" events.
 *
 * Call screening runs BEFORE the phone rings, so the first state delivered when the
 * listener registers is IDLE. That IDLE must not be read as "the call ended".
 */
class CallPhaseTracker {
    enum class Event { NONE, ANSWERED, ENDED }

    private var ringing = false
    private var answered = false
    private var ended = false

    val isAnswered: Boolean get() = answered

    fun on(state: Int): Event {
        if (ended) return Event.NONE
        return when (state) {
            TelephonyManager.CALL_STATE_RINGING -> { ringing = true; Event.NONE }
            TelephonyManager.CALL_STATE_OFFHOOK -> if (answered) Event.NONE else { answered = true; Event.ANSWERED }
            TelephonyManager.CALL_STATE_IDLE -> if (ringing || answered) { ended = true; Event.ENDED } else Event.NONE
            else -> Event.NONE
        }
    }
}
