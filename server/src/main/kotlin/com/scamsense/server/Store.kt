package com.scamsense.server

import com.scamsense.core.api.CallOutcome
import com.scamsense.core.api.CallRecordDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

data class Subscriber(
    val id: Long,
    val name: String,
    /** The protected person's real number (calls judged safe are bridged here). */
    val phone: String,
    val caregiverPhone: String?,
    /** Our number their SIM forwards to; incoming webhooks are routed by it. */
    val assistantNumber: String,
)

data class CallSession(
    val id: Long,
    val subscriberId: Long,
    val callSid: String,
    val caller: String,
    val startedAt: Long,
    val transcript: List<String>,
    val silentTurns: Int,
    val outcome: CallOutcome?,
)

/**
 * SQLite persistence (single file, zero ops). All statements are parameterised.
 * One connection guarded by a lock is plenty for a call assistant's load.
 */
class Store(path: String, private val clock: () -> Long = System::currentTimeMillis) : AutoCloseable {
    private val conn: Connection
    private val lock = Any()
    private val listSer = ListSerializer(String.serializer())

    init {
        Class.forName("org.sqlite.JDBC")
        conn = DriverManager.getConnection("jdbc:sqlite:$path")
        conn.createStatement().use { st ->
            st.execute("PRAGMA journal_mode=WAL")
            st.execute("PRAGMA foreign_keys=ON")
            st.execute(
                """CREATE TABLE IF NOT EXISTS subscribers(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL, phone TEXT NOT NULL, caregiver_phone TEXT,
                    assistant_number TEXT NOT NULL UNIQUE,
                    token_hash TEXT, pair_code_hash TEXT, pair_expires INTEGER,
                    created_at INTEGER NOT NULL)""",
            )
            st.execute(
                """CREATE TABLE IF NOT EXISTS calls(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    subscriber_id INTEGER NOT NULL REFERENCES subscribers(id) ON DELETE CASCADE,
                    call_sid TEXT NOT NULL UNIQUE, caller TEXT NOT NULL, started_at INTEGER NOT NULL,
                    transcript TEXT NOT NULL DEFAULT '[]', silent_turns INTEGER NOT NULL DEFAULT 0,
                    outcome TEXT, score INTEGER NOT NULL DEFAULT 0, level TEXT NOT NULL DEFAULT 'SAFE',
                    scam_type TEXT, reasons TEXT NOT NULL DEFAULT '[]')""",
            )
            st.execute("CREATE INDEX IF NOT EXISTS calls_sub ON calls(subscriber_id, started_at)")
        }
    }

    // ------------------------------------------------------------------ subscribers

    fun addSubscriber(name: String, phone: String, caregiverPhone: String?, assistantNumber: String): Long = synchronized(lock) {
        conn.prepareStatement(
            "INSERT INTO subscribers(name, phone, caregiver_phone, assistant_number, created_at) VALUES(?,?,?,?,?)",
            java.sql.Statement.RETURN_GENERATED_KEYS,
        ).use { ps ->
            ps.setString(1, name); ps.setString(2, phone); ps.setString(3, caregiverPhone)
            ps.setString(4, assistantNumber); ps.setLong(5, clock())
            ps.executeUpdate()
            ps.generatedKeys.use { it.next(); it.getLong(1) }
        }
    }

    fun setPairingCode(subscriberId: Long, codeHash: String, expiresAt: Long) = synchronized(lock) {
        conn.prepareStatement("UPDATE subscribers SET pair_code_hash=?, pair_expires=? WHERE id=?").use { ps ->
            ps.setString(1, codeHash); ps.setLong(2, expiresAt); ps.setLong(3, subscriberId); ps.executeUpdate()
        }
    }

    /** Consumes a pairing code (single use) and stores the new device-token hash. */
    fun redeemPairingCode(codeHash: String, tokenHash: String): Subscriber? = synchronized(lock) {
        val sub = conn.prepareStatement("SELECT * FROM subscribers WHERE pair_code_hash=? AND pair_expires>?").use { ps ->
            ps.setString(1, codeHash); ps.setLong(2, clock())
            ps.executeQuery().use { rs -> if (rs.next()) rs.toSubscriber() else null }
        } ?: return null
        conn.prepareStatement("UPDATE subscribers SET token_hash=?, pair_code_hash=NULL, pair_expires=NULL WHERE id=?").use { ps ->
            ps.setString(1, tokenHash); ps.setLong(2, sub.id); ps.executeUpdate()
        }
        sub
    }

    fun subscriberByToken(tokenHash: String): Subscriber? = query("SELECT * FROM subscribers WHERE token_hash=?", tokenHash)

    fun subscriberByAssistantNumber(number: String): Subscriber? = query("SELECT * FROM subscribers WHERE assistant_number=?", number)

    fun subscriber(id: Long): Subscriber? = synchronized(lock) {
        conn.prepareStatement("SELECT * FROM subscribers WHERE id=?").use { ps ->
            ps.setLong(1, id); ps.executeQuery().use { rs -> if (rs.next()) rs.toSubscriber() else null }
        }
    }

    fun isAssistantNumber(number: String): Boolean = subscriberByAssistantNumber(number) != null

    private fun query(sql: String, arg: String): Subscriber? = synchronized(lock) {
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, arg); ps.executeQuery().use { rs -> if (rs.next()) rs.toSubscriber() else null }
        }
    }

    private fun ResultSet.toSubscriber() = Subscriber(
        getLong("id"), getString("name"), getString("phone"), getString("caregiver_phone"), getString("assistant_number"),
    )

    // ------------------------------------------------------------------ calls

    fun startCall(subscriberId: Long, callSid: String, caller: String): CallSession = synchronized(lock) {
        purgeOld()
        conn.prepareStatement("INSERT OR IGNORE INTO calls(subscriber_id, call_sid, caller, started_at) VALUES(?,?,?,?)").use { ps ->
            ps.setLong(1, subscriberId); ps.setString(2, callSid); ps.setString(3, caller); ps.setLong(4, clock()); ps.executeUpdate()
        }
        session(callSid)!!
    }

    fun session(callSid: String): CallSession? = synchronized(lock) {
        conn.prepareStatement("SELECT * FROM calls WHERE call_sid=?").use { ps ->
            ps.setString(1, callSid)
            ps.executeQuery().use { rs ->
                if (!rs.next()) null else CallSession(
                    rs.getLong("id"), rs.getLong("subscriber_id"), rs.getString("call_sid"), rs.getString("caller"),
                    rs.getLong("started_at"), Json.decodeFromString(listSer, rs.getString("transcript")),
                    rs.getInt("silent_turns"), rs.getString("outcome")?.let { CallOutcome.valueOf(it) },
                )
            }
        }
    }

    fun recordTurn(callSid: String, transcript: List<String>, silentTurns: Int) = synchronized(lock) {
        conn.prepareStatement("UPDATE calls SET transcript=?, silent_turns=? WHERE call_sid=?").use { ps ->
            ps.setString(1, Json.encodeToString(listSer, transcript)); ps.setInt(2, silentTurns); ps.setString(3, callSid); ps.executeUpdate()
        }
    }

    fun finishCall(callSid: String, outcome: CallOutcome, score: Int, level: String, scamType: String?, reasons: List<String>) = synchronized(lock) {
        conn.prepareStatement("UPDATE calls SET outcome=?, score=?, level=?, scam_type=?, reasons=? WHERE call_sid=?").use { ps ->
            ps.setString(1, outcome.name); ps.setInt(2, score); ps.setString(3, level); ps.setString(4, scamType)
            ps.setString(5, Json.encodeToString(listSer, reasons)); ps.setString(6, callSid); ps.executeUpdate()
        }
    }

    fun recentCalls(subscriberId: Long, limit: Int = 50): List<CallRecordDto> = synchronized(lock) {
        conn.prepareStatement(
            "SELECT * FROM calls WHERE subscriber_id=? AND outcome IS NOT NULL ORDER BY started_at DESC, id DESC LIMIT ?",
        ).use { ps ->
            ps.setLong(1, subscriberId); ps.setInt(2, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        CallRecordDto(
                            id = rs.getLong("id"), caller = rs.getString("caller"), startedAt = rs.getLong("started_at"),
                            outcome = CallOutcome.valueOf(rs.getString("outcome")), score = rs.getInt("score"),
                            level = rs.getString("level"), scamType = rs.getString("scam_type"),
                            reasons = Json.decodeFromString(listSer, rs.getString("reasons")),
                            transcript = Json.decodeFromString(listSer, rs.getString("transcript")),
                        ),
                    )
                }
            }
        }
    }

    fun recentCallTranscript(callId: Long): List<String>? = synchronized(lock) {
        conn.prepareStatement("SELECT transcript FROM calls WHERE id=?").use { ps ->
            ps.setLong(1, callId)
            ps.executeQuery().use { rs -> if (rs.next()) Json.decodeFromString(listSer, rs.getString(1)) else null }
        }
    }

    fun deleteCalls(subscriberId: Long) = synchronized(lock) {
        conn.prepareStatement("DELETE FROM calls WHERE subscriber_id=?").use { ps -> ps.setLong(1, subscriberId); ps.executeUpdate() }
    }

    var retentionDays: Int = 30

    /** Data minimisation: transcripts are deleted automatically after [retentionDays]. */
    private fun purgeOld() {
        conn.prepareStatement("DELETE FROM calls WHERE started_at < ?").use { ps ->
            ps.setLong(1, clock() - retentionDays * 86_400_000L); ps.executeUpdate()
        }
    }

    override fun close() = conn.close()
}
