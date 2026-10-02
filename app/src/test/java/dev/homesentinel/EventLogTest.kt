package dev.homesentinel

import dev.homesentinel.data.preferences.EventLog
import dev.homesentinel.domain.model.*
import java.io.IOException
import java.net.SocketTimeoutException
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class EventLogTest {
    private class Storage(var json: String = "[]") {
        var time = 1_700_000_000_000L
        fun open(limit: Int = LogRetention.DEFAULT) = EventLog(
            read = { json }, write = { json = it }, now = { time++ }, initialRetention = limit,
        )
    }

    @Test fun defaultRetainsTheNewest500EntriesAcrossRestart() {
        val storage = Storage()
        val log = storage.open()
        repeat(520) { log.add("Event $it", type = LogType.SIGNAL_DETECTED) }
        assertEquals(500, log.entries.value.size)
        assertEquals("Event 519", log.entries.value.first().message)
        assertEquals("Event 20", log.entries.value.last().message)
        assertEquals(log.entries.value, storage.open().entries.value)
        assertTrue(storage.open().entries.value.all { it.type == LogType.SIGNAL_DETECTED })
    }

    @Test fun shrinkingRemovesOldestImmediatelyAndRaisingCannotRestoreThem() {
        val storage = Storage()
        val log = storage.open()
        repeat(100) { log.add("Event $it") }
        log.setRetention(50)
        assertEquals("Event 50", log.entries.value.last().message)
        assertEquals(50, storage.open(50).entries.value.size)
        log.setRetention(1_000)
        assertEquals(50, log.entries.value.size)
        log.add("Next")
        assertEquals(51, log.entries.value.size)
    }

    @Test fun startupWaitsForTheSavedLimitWithoutDiscardingHistoryAbove500() {
        val storage = Storage()
        val log = storage.open(1_000)
        repeat(750) { log.add("Event $it") }
        val restarted = storage.open(LogRetention.MAX)
        restarted.setRetention(1_000)
        assertEquals(750, restarted.entries.value.size)
        assertEquals("Event 0", restarted.entries.value.last().message)
    }

    @Test fun invalidRetentionDoesNotChangeHistoryOrCapacity() {
        val log = Storage().open()
        for (invalid in listOf(0, 49, 5_001, Int.MAX_VALUE)) {
            try {
                log.setRetention(invalid)
                fail("Invalid limit accepted")
            } catch (_: IllegalArgumentException) { }
        }
        repeat(510) { log.add("Event $it") }
        assertEquals(500, log.entries.value.size)
    }

    @Test fun oldEntriesAndUnknownTypesRemainReadableAndDetailsImplyAnError() {
        val storage = Storage("""[
            {"at":1,"message":"Old event"},
            {"at":2,"message":"Future type","type":"FUTURE"},
            {"at":3,"message":"Legacy error","isError":true},
            {"at":4,"message":"Details without old flag","errorDetails":{"operation":"Test"}}
        ]""")
        val entries = storage.open().entries.value
        assertEquals(listOf(LogType.INFO, LogType.INFO, LogType.ERROR, LogType.ERROR), entries.map { it.type })
        assertFalse(entries[0].isError)
        assertTrue(entries[3].isError)
        assertEquals("Test", entries[3].errorDetails!!.operation)
    }

    @Test fun everyTechnicalFieldIsSanitizedBeforeStorageAndAfterLoading() {
        val storage = Storage()
        val log = storage.open()
        val secret = "password=private token=secret"
        val details = ErrorDetails(secret, secret, "https://user:private@host/path?token=secret", secret, secret, secret, secret, secret)
        log.add(secret, errorDetails = details)
        assertFalse(storage.json.contains("private"))
        assertFalse(storage.json.contains("secret"))
        val restored = storage.open().entries.value.single()
        assertTrue(restored.isError)
        assertEquals(LogType.ERROR, restored.type)
        assertTrue(restored.errorDetails!!.stackTrace.contains("[REDACTED]"))
        storage.json = """[{"at":1,"message":"token=private","errorDetails":{"stackTrace":"password=secret"}}]"""
        assertEquals("token=[REDACTED]", storage.open().entries.value.single().message)
        assertEquals("password=[REDACTED]", storage.open().entries.value.single().errorDetails!!.stackTrace)
    }

    @Test fun errorsKeepRequestStatusCausesAndFullTraceAcrossRestart() {
        val storage = Storage()
        val cause = SocketTimeoutException("Timed out")
        val error = BlinkException(
            BlinkException.Kind.NETWORK, "Read failed", IOException("Connection lost", cause),
            BlinkErrorContext("Read status", "GET", "https://blink.invalid/status?token=private", 503),
        )
        storage.open().addError("Status unavailable", error)
        val details = storage.open().entries.value.single().errorDetails!!
        assertEquals("GET", details.httpMethod)
        assertEquals("503", details.httpStatus)
        assertEquals("Read status", details.operation)
        assertTrue(details.causes.contains("SocketTimeoutException: Timed out"))
        assertTrue(details.stackTrace.contains("Caused by: java.io.IOException"))
        assertFalse(details.url.contains("private"))
        storage.open().addError("No response", cause)
        assertEquals("No HTTP status received", storage.open().entries.value.first().errorDetails!!.httpStatus)
    }

    @Test fun clearingPersistsAnEmptyLogAndErrorTypeAlwaysMarksAnError() {
        val storage = Storage()
        val log = storage.open()
        log.add("Error without throwable", type = LogType.ERROR)
        assertTrue(log.entries.value.single().isError)
        log.clear()
        assertEquals(0, JSONArray(storage.json).length())
        assertTrue(storage.open().entries.value.isEmpty())
    }
}
