package com.scamsense.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantClientTest {
    private val c = AssistantClient()

    @Test fun acceptsHttpsAndAddsScheme() {
        assertEquals("https://assist.example.com", c.normaliseServer(" assist.example.com/ "))
        assertEquals("https://assist.example.com:8443", c.normaliseServer("https://assist.example.com:8443"))
    }

    @Test fun rejectsInsecureOrTrickyAddresses() {
        assertNull(c.normaliseServer("http://assist.example.com"))
        assertNull(c.normaliseServer("https://user:pass@assist.example.com"))
        assertNull(c.normaliseServer("ftp://x"))
        assertNull(c.normaliseServer("https://"))
        assertNull(c.normaliseServer("https://evil.com/path?x=1"))
        assertNull(c.normaliseServer("localhost"))
    }
}
