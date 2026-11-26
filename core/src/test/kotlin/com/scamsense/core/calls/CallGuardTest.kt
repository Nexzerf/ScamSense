package com.scamsense.core.calls

import com.scamsense.core.calls.CallGuard.Action
import com.scamsense.core.calls.CallGuard.Policy
import org.junit.Assert.assertEquals
import org.junit.Test

class CallGuardTest {

    private val assistantOn = Policy(assistantEnabled = true, assistantNumber = "02-123-4567")

    @Test fun spoofedPrefixAlwaysBlocked() {
        assertEquals(Action.BLOCK, CallGuard.decide("+6971234567", Policy()).action)
        assertEquals(Action.BLOCK, CallGuard.decide("+698 12 345 678", assistantOn).action)
    }

    @Test fun reportedNumberBlocked() {
        val p = Policy(blockedNumbers = listOf("081-234-5678"))
        assertEquals(Action.BLOCK, CallGuard.decide("+66812345678", p).action)
    }

    @Test fun internationalBlockedOnlyWhenOptedIn() {
        assertEquals(Action.ALLOW_WITH_WARNING, CallGuard.decide("+85512345678", Policy()).action)
        assertEquals(Action.BLOCK, CallGuard.decide("+85512345678", Policy(blockInternational = true)).action)
    }

    @Test fun unknownNumberGoesToAssistantWhenEnabled() {
        assertEquals(Action.SEND_TO_ASSISTANT, CallGuard.decide("0899999999", assistantOn).action)
        assertEquals(Action.ALLOW_WITH_WARNING, CallGuard.decide("0899999999", Policy()).action)
    }

    @Test fun hiddenNumberGoesToAssistantWhenEnabled() {
        assertEquals(Action.SEND_TO_ASSISTANT, CallGuard.decide(null, assistantOn).action)
        assertEquals(Action.ALLOW_WITH_WARNING, CallGuard.decide("", Policy()).action)
    }

    @Test fun bridgedCallFromAssistantIsAllowedWithoutLoop() {
        // Otherwise a safe call bridged back by the assistant would be rejected again -> loop.
        assertEquals(Action.ALLOW, CallGuard.decide("021234567", assistantOn).action)
        assertEquals(Action.ALLOW, CallGuard.decide("+66 2 123 4567", assistantOn).action)
    }

    @Test fun spoofableOfficialNumberStillWarns() {
        val d = CallGuard.decide("02-888-8888", Policy())
        assertEquals(Action.ALLOW_WITH_WARNING, d.action)
        assert(d.detail.contains("กสิกร"))
    }

    @Test fun forwardingCodes() {
        assertEquals("**67*021234567#", CallGuard.ForwardingCodes.busy("02-123-4567"))
        assertEquals("**61*+6621234567#", CallGuard.ForwardingCodes.noAnswer("+66 2123 4567"))
    }
}
