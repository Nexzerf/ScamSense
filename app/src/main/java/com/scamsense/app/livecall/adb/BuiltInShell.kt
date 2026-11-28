package com.scamsense.app.livecall.adb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.android.AdbMdns
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.InputStream
import java.math.BigInteger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * "Built-in mode": ScamSense talks to the phone's own ADB daemon (Wireless debugging) itself,
 * so no separate Shizuku app is needed. The user pairs once with the 6-digit code; ScamSense
 * keeps its own key, so later connections need no user action.
 *
 * Every step writes to [AdbLog] (and logcat tag SS:Adb) so failures on real phones can be
 * diagnosed from a screenshot or `adb logcat`.
 */
object BuiltInShell {

    private const val PREFS = "builtin_shell"
    private const val KEY_PAIRED = "paired"
    private const val KEY_PORT = "last_port"
    private const val LOCALHOST = "127.0.0.1"

    @Volatile private var manager: Manager? = null
    private val lock = Any()

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun isPaired(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PAIRED, false)

    /** Granted once over ADB after pairing: lets ScamSense switch Wireless debugging on by itself. */
    fun canToggleWirelessDebugging(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Creates (or loads) ScamSense's ADB key and certificate. Exposed for tests. */
    internal fun prepareKeys(context: Context): Boolean =
        runCatching { managerFor(context.applicationContext) }.onFailure { AdbLog.add("keys: ${it.javaClass.simpleName} ${it.message}") }.isSuccess

    /** Drops the in-memory connection (tests, or after the key files changed). */
    internal fun resetConnection() = synchronized(lock) { runCatching { manager?.close() }; manager = null }

    fun forget(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        synchronized(lock) { runCatching { manager?.close() }; manager = null }
        AdbLog.add("forgot pairing")
    }

    // ------------------------------------------------------------------ pairing

    sealed interface Result {
        data object Ok : Result
        data class Failed(val reason: String, val userMessage: String) : Result
    }

    /** Pairs with the code shown in Settings → Wireless debugging → Pair with pairing code, then sets up. Blocking. */
    fun pairAndSetup(context: Context, code: String): Result {
        val app = context.applicationContext
        AdbLog.add("pair: start (Android ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL})")
        if (!isSupported()) return fail("api", "ต้องใช้ Android 11 ขึ้นไป")
        val digits = code.filter { it.isDigit() }
        if (digits.length != 6) return fail("code-format", "รหัสจับคู่ต้องเป็นตัวเลข 6 หลัก")
        if (!onWifi(app)) return fail("no-wifi", "ต้องต่อ Wi‑Fi ก่อน (การแก้ไขข้อบกพร่องแบบไร้สายใช้ได้เฉพาะบน Wi‑Fi)")

        val mgr = runCatching { managerFor(app) }.getOrElse { return fail("keys: ${it.javaClass.simpleName}", "สร้างกุญแจไม่สำเร็จ") }
        val hosts = candidateHosts(app)
        val mdnsPort = mdnsPort(app, AdbMdns.SERVICE_TYPE_TLS_PAIRING, 4_000)
        AdbLog.add("pair: mDNS pairing port = ${mdnsPort ?: "not found"}")
        val attempts = buildList {
            if (mdnsPort != null) hosts.forEach { add(it to mdnsPort) }
            hosts.forEach { h -> scanOpenPorts(h).filter { it != mdnsPort }.forEach { add(h to it) } }
        }
        AdbLog.add("pair: ${attempts.size} candidate(s): ${attempts.take(8).joinToString { "${it.first}:${it.second}" }}")
        if (attempts.isEmpty()) return fail("pair-port", "ไม่พบหน้าต่างจับคู่ ให้เปิด \"จับคู่อุปกรณ์ด้วยรหัสจับคู่\" ค้างไว้ แล้วใส่รหัสใหม่")

        var paired = false
        for ((host, port) in attempts.distinct()) {
            val ok = withTimeout(10_000) { mgr.pair(host, port, digits) }
            AdbLog.add("pair: $host:$port -> ${ok ?: "timeout"}")
            if (ok == true) { paired = true; break }
        }
        if (!paired) return fail("pair", "จับคู่ไม่สำเร็จ ตรวจรหัสอีกครั้ง (รหัสเปลี่ยนทุกครั้งที่เปิดหน้าต่างจับคู่)")
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PAIRED, true).apply()

        // Pairing dialog closes after success; give adbd a moment before connecting.
        Thread.sleep(800)
        if (!connect(app)) return fail("connect-after-pair", "จับคู่แล้ว แต่เชื่อมต่อไม่สำเร็จ ลองกด \"ทดสอบการฟัง\" อีกครั้ง")
        val grant = runShell(app, "pm grant ${app.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}")
        AdbLog.add("grant WRITE_SECURE_SETTINGS: '${grant?.trim().orEmpty()}' -> ${canToggleWirelessDebugging(app)}")
        AdbLog.add("whoami: ${runShell(app, "id")?.trim()}")
        return Result.Ok
    }

    // ------------------------------------------------------------------ connect / run

    /** Connects (reusing a live connection). Turns Wireless debugging on first if allowed. Blocking. */
    fun connect(context: Context): Boolean = synchronized(lock) {
        val app = context.applicationContext
        val mgr = runCatching { managerFor(app) }.getOrElse { AdbLog.add("connect: keys ${it.javaClass.simpleName}"); return false }
        if (mgr.isConnected) return true
        if (!ensureWirelessDebugging(app)) return false

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hosts = candidateHosts(app)
        val tried = HashSet<Pair<String, Int>>()
        fun attempt(host: String, port: Int): Boolean {
            if (!tried.add(host to port)) return false
            val ok = withTimeout(8_000) {
                mgr.setTimeout(6, TimeUnit.SECONDS)
                mgr.connect(host, port)
                mgr.isConnected
            }
            AdbLog.add("connect: $host:$port -> ${ok ?: "timeout"}")
            if (ok == true) prefs.edit().putInt(KEY_PORT, port).apply()
            else runCatching { mgr.disconnect() }
            return ok == true
        }

        val cached = prefs.getInt(KEY_PORT, -1)
        if (cached > 0 && hosts.any { attempt(it, cached) }) return true
        val mdns = mdnsPort(app, AdbMdns.SERVICE_TYPE_TLS_CONNECT, 3_000)
        AdbLog.add("connect: mDNS port = ${mdns ?: "not found"}")
        if (mdns != null && hosts.any { attempt(it, mdns) }) return true
        for (h in hosts) for (p in scanOpenPorts(h)) if (attempt(h, p)) return true
        AdbLog.add("connect: no adb port answered")
        false
    }

    /** Opens `exec:` (binary-clean stdout, no terminal). Caller closes the stream. */
    fun exec(context: Context, command: String): AdbStream? {
        if (!connect(context)) return null
        return synchronized(lock) {
            runCatching { manager?.openStream("exec:$command") }
                .onFailure { AdbLog.add("exec failed: ${it.javaClass.simpleName} ${it.message}") }
                .getOrNull()
        }
    }

    /** Runs a short shell command and returns its output (null if it could not run). */
    fun runShell(context: Context, command: String, timeoutMs: Long = 8_000): String? {
        val stream = exec(context, command) ?: return null
        return withTimeout(timeoutMs) {
            stream.use { s -> s.openInputStream().bufferedReader().readText() }
        }
    }

    // ------------------------------------------------------------------ wireless debugging

    private fun ensureWirelessDebugging(context: Context): Boolean {
        val cr = context.contentResolver
        val on = Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1
        if (on) return true
        if (!onWifi(context)) { AdbLog.add("wireless debugging off and no Wi-Fi"); return false }
        if (!canToggleWirelessDebugging(context)) {
            AdbLog.add("wireless debugging is off and cannot be switched on (no WRITE_SECURE_SETTINGS)")
            return false
        }
        val ok = runCatching {
            Settings.Global.putInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
            Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
        }.getOrDefault(false)
        AdbLog.add("switched wireless debugging on: $ok")
        if (!ok) return false
        // adbd needs a moment to start listening.
        repeat(12) {
            Thread.sleep(500)
            if (Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1 && it >= 2) return true
        }
        return Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1
    }

    private fun onWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.allNetworks.any { n -> cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }

    // ------------------------------------------------------------------ discovery

    private fun mdnsPort(context: Context, type: String, timeoutMs: Long): Int? {
        val latch = CountDownLatch(1)
        var found: Int? = null
        val mdns = runCatching {
            AdbMdns(context, type) { _, port -> if (port > 0) { found = port; latch.countDown() } }.also { it.start() }
        }.getOrNull() ?: return null
        try { latch.await(timeoutMs, TimeUnit.MILLISECONDS) } finally { runCatching { mdns.stop() } }
        return found
    }

    /** Loopback first; some builds bind adbd only to the Wi‑Fi address. */
    private fun candidateHosts(context: Context): List<String> {
        val wifi = runCatching { io.github.muntashirakon.adb.android.AndroidUtils.getHostIpAddress(context) }.getOrNull()
        return listOfNotNull(LOCALHOST, wifi?.takeIf { it.isNotBlank() && it != "0.0.0.0" && it != LOCALHOST }).distinct()
    }

    /**
     * Fallback when mDNS discovery fails (seen on ColorOS): try every port in the range
     * adbd picks from, on this phone only. ~35k local connects across 64 threads takes a
     * second or two; closed ports are refused immediately.
     */
    fun scanOpenPorts(host: String, from: Int = 30_000, to: Int = 65_535): List<Int> {
        val started = System.currentTimeMillis()
        val pool = Executors.newFixedThreadPool(64)
        val open = java.util.concurrent.ConcurrentLinkedQueue<Int>()
        val addr = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return emptyList()
        val step = 512
        var p = from
        while (p <= to) {
            val a = p
            val b = minOf(p + step - 1, to)
            pool.execute {
                for (port in a..b) {
                    runCatching { Socket().use { it.connect(InetSocketAddress(addr, port), 150); open += port } }
                }
            }
            p += step
        }
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS)
        val ports = open.sorted()
        AdbLog.add("scan $host: ${ports.size} open in ${System.currentTimeMillis() - started} ms ${ports.take(10)}")
        return ports
    }

    // ------------------------------------------------------------------ keys

    private fun managerFor(context: Context): Manager = synchronized(lock) {
        manager ?: Manager(context).also { manager = it }
    }

    private class Manager(context: Context) : AbsAdbConnectionManager() {
        private val key: PrivateKey
        private val cert: Certificate

        init {
            api = Build.VERSION.SDK_INT
            isThrowOnUnauthorised = true // key is known after pairing; never wait on a prompt
            val dir = File(context.noBackupFilesDir, "adb").apply { mkdirs() }
            val keyFile = File(dir, "key.pk8")
            val certFile = File(dir, "cert.der")
            if (keyFile.exists() && certFile.exists()) {
                key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
            } else {
                val kpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }
                val pair = kpg.generateKeyPair()
                val name = X500Name("CN=ScamSense")
                val now = System.currentTimeMillis()
                val builder = JcaX509v3CertificateBuilder(
                    name, BigInteger.valueOf(SecureRandom().nextLong() and Long.MAX_VALUE),
                    Date(now - 86_400_000L), Date(now + 30L * 365 * 86_400_000L), name, pair.public,
                )
                val signer = JcaContentSignerBuilder("SHA256withRSA").build(pair.private)
                key = pair.private
                cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
                keyFile.writeBytes(key.encoded)
                certFile.writeBytes(cert.encoded)
                AdbLog.add("generated new ADB key")
            }
        }

        override fun getPrivateKey(): PrivateKey = key
        override fun getCertificate(): Certificate = cert
        override fun getDeviceName(): String = "ScamSense"
    }

    // ------------------------------------------------------------------ helpers

    private fun fail(reason: String, message: String): Result.Failed {
        AdbLog.add("FAILED: $reason")
        return Result.Failed(reason, message)
    }

    /** Runs [block] on a worker with a hard deadline (libadb has no timeout for every step). */
    private fun <T> withTimeout(ms: Long, block: () -> T): T? {
        val ex = Executors.newSingleThreadExecutor()
        return try {
            ex.submit<T> { block() }.get(ms, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            val cause = (e.cause ?: e)
            if (e !is java.util.concurrent.TimeoutException) AdbLog.add("  error: ${cause.javaClass.simpleName}: ${cause.message?.take(120)}")
            null
        } finally {
            ex.shutdownNow()
        }
    }
}

/** In-memory diagnostic log (last 300 lines), also mirrored to logcat as SS:Adb. */
object AdbLog {
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun add(line: String) {
        Log.w("SS:Adb", line)
        lines.addLast("${fmt.format(Date())} $line")
        while (lines.size > 300) lines.removeFirst()
    }

    @Synchronized
    fun text(): String = lines.joinToString("\n")
}
