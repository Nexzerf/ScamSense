package com.scamsense.app

/**
 * Product switches. The AI call assistant (calls forwarded to a cloud answering service,
 * server/ module) is hidden for now: it needs a paid phone number and a server, and the
 * on-device listener covers the main need. The code stays so it can be switched back on.
 */
object Features {
    const val AI_CALL_ASSISTANT = false
}
