package com.scamsense.core.api

import kotlinx.serialization.Serializable

/** JSON contract between the Android app and the AI call-assistant server. */

@Serializable
data class PairRequest(val code: String)

@Serializable
data class PairResponse(
    val token: String,
    val subscriberName: String,
    /** Number the user's calls are forwarded to (the AI assistant's line). */
    val assistantNumber: String,
)

@Serializable
enum class CallOutcome {
    /** Scam detected: the assistant hung up and alerted the user/caregiver. */
    BLOCKED,
    /** Judged safe: the call was connected to the user. */
    CONNECTED,
    /** Unclear: the assistant took a message instead of connecting. */
    MESSAGE_TAKEN,
    /** Caller never spoke (robocall / gave up). */
    NO_SPEECH,
}

@Serializable
data class CallRecordDto(
    val id: Long,
    val caller: String,
    val startedAt: Long,
    val outcome: CallOutcome,
    val score: Int,
    val level: String,
    val scamType: String? = null,
    val reasons: List<String> = emptyList(),
    /** What the caller said (digit runs masked). */
    val transcript: List<String> = emptyList(),
)

@Serializable
data class CallListResponse(val calls: List<CallRecordDto>)

@Serializable
data class ErrorResponse(val error: String)
