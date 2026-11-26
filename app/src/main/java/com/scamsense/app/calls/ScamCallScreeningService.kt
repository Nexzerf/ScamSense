package com.scamsense.app.calls

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import com.scamsense.app.ScamSenseApplication
import com.scamsense.core.calls.CallGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Screens incoming calls before they ring (requires the user to give ScamSense the
 * "call screening" role, Android 10+). Only numbers NOT in the user's contacts reach
 * this service, and only the number is visible — never the call audio.
 */
@RequiresApi(Build.VERSION_CODES.N)
class ScamCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onScreenCall(details: Call.Details) {
        // Only screen incoming calls; outgoing calls are always allowed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            details.callDirection != Call.Details.DIRECTION_INCOMING
        ) {
            respondToCall(details, allow())
            return
        }
        val number = details.handle?.schemeSpecificPart
        scope.launch {
            // Must answer within 5 s; settings normally load in a few ms.
            val settings = withTimeoutOrNull(2_500) {
                (application as ScamSenseApplication).container.settingsRepository.ensureLoaded()
            }
            val decision = if (settings == null) {
                CallGuard.decide(number, CallGuard.Policy())
            } else {
                CallGuard.decide(
                    number,
                    CallGuard.Policy(
                        blockedNumbers = settings.blockedNumbers,
                        blockInternational = settings.blockInternational,
                        assistantEnabled = settings.assistantActive,
                        assistantNumber = settings.assistantNumber,
                    ),
                )
            }
            val response = when (decision.action) {
                CallGuard.Action.ALLOW, CallGuard.Action.ALLOW_WITH_WARNING -> allow()
                // Rejected calls are logged as "blocked" in the system call log.
                CallGuard.Action.BLOCK -> reject(skipNotification = true)
                // A rejected call triggers the carrier's busy-forwarding to the AI assistant.
                CallGuard.Action.SEND_TO_ASSISTANT -> reject(skipNotification = false)
            }
            respondToCall(details, response)
            val notify = when (decision.action) {
                CallGuard.Action.ALLOW -> false
                CallGuard.Action.ALLOW_WITH_WARNING -> settings?.callWarnings != false
                CallGuard.Action.BLOCK, CallGuard.Action.SEND_TO_ASSISTANT -> true
            }
            if (notify) CallNotifications.show(applicationContext, decision)
        }
    }

    private fun allow() = CallResponse.Builder().build()

    private fun reject(skipNotification: Boolean) = CallResponse.Builder()
        .setDisallowCall(true)
        .setRejectCall(true)
        .setSkipCallLog(false)
        .setSkipNotification(skipNotification)
        .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
