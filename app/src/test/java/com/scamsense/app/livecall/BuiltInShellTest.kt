package com.scamsense.app.livecall

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scamsense.app.livecall.adb.BuiltInShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BuiltInShellTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun portScanFindsListeningPortOnLoopback() {
        ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { server ->
            val port = server.localPort
            val found = BuiltInShell.scanOpenPorts("127.0.0.1", (port - 40).coerceAtLeast(1), port + 40)
            assertTrue("found $found, expected $port", port in found)
        }
    }

    @Test fun keysAreCreatedOnceThenReloaded() {
        val dir = File(context.noBackupFilesDir, "adb")
        dir.deleteRecursively()
        BuiltInShell.resetConnection()
        val ok = BuiltInShell.prepareKeys(context)
        assertTrue(com.scamsense.app.livecall.adb.AdbLog.text(), ok)
        val key = File(dir, "key.pk8").readBytes()
        val cert = File(dir, "cert.der").readBytes()
        assertTrue(key.size > 1000 && cert.size > 300)
        // A fresh manager must load the same identity (adbd remembers it after pairing).
        BuiltInShell.resetConnection()
        assertTrue(BuiltInShell.prepareKeys(context))
        assertTrue(key.contentEquals(File(dir, "key.pk8").readBytes()))
        assertTrue(cert.contentEquals(File(dir, "cert.der").readBytes()))
    }

    @Test fun pairingRejectsBadCodeBeforeTouchingTheNetwork() {
        val r = BuiltInShell.pairAndSetup(context, "12a")
        assertTrue(r is BuiltInShell.Result.Failed)
        assertEquals("code-format", (r as BuiltInShell.Result.Failed).reason)
        assertFalse(BuiltInShell.isPaired(context))
    }

    @Test fun forgetClearsPairing() {
        context.getSharedPreferences("builtin_shell", 0).edit().putBoolean("paired", true).commit()
        assertTrue(BuiltInShell.isPaired(context))
        BuiltInShell.forget(context)
        assertFalse(BuiltInShell.isPaired(context))
    }

    @Test fun pairingInputAcceptsCodeWithOrWithoutPort() {
        fun p(s: String) = BuiltInShell.parsePairingInput(s)
        assertEquals(BuiltInShell.PairingInput("123456", null), p("123456"))
        assertEquals(BuiltInShell.PairingInput("123456", 41235), p("123456 41235"))
        assertEquals(BuiltInShell.PairingInput("123456", 41235), p("41235 123456"))
        assertEquals(BuiltInShell.PairingInput("123456", 41235), p("192.168.1.5:41235 123456"))
        assertEquals(BuiltInShell.PairingInput("654321", 38001), p(" 654321 , 38001 "))
        assertEquals(null, p("12345"))
        assertEquals(BuiltInShell.PairingInput("123456", null), p("123456 80")) // not a valid adb port
    }
}
