package com.untr.medeo.data.diagnostics

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLogStoreTest {
    @Test
    fun append_writesUtf8AndRedactsUrlIpAndToken() {
        val directory = Files.createTempDirectory("medeo-diagnostics").toFile()
        try {
            val store = DiagnosticLogStore(directory)
            store.start(diagnosticLine("2026-07-14T00:00:00Z", "session_start"))
            store.append(
                diagnosticLine(
                    timestamp = "2026-07-14T00:00:01Z",
                    event = "playback_error",
                    fields = mapOf(
                        "message" to "网络中断",
                        "url" to "https://192.168.1.2/video.m3u8?token=secret"
                    )
                )
            )

            val text = directory.resolve("medeo-diagnostic.log").readText(Charsets.UTF_8)

            assertTrue(text.contains("网络中断"))
            assertTrue(text.contains("<redacted-url>"))
            assertFalse(text.contains("192.168.1.2"))
            assertFalse(text.contains("secret"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun append_neverExceedsConfiguredSize() {
        val directory = Files.createTempDirectory("medeo-diagnostics").toFile()
        try {
            val maxBytes = 160L
            val store = DiagnosticLogStore(directory, maxBytes = maxBytes)
            store.start("time=0 event=session_start")
            repeat(20) { index ->
                store.append("time=$index event=playback_state value=${"x".repeat(30)}")
            }

            assertTrue(directory.resolve("medeo-diagnostic.log").length() <= maxBytes)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun deleteExpired_removesOldLog() {
        val directory = Files.createTempDirectory("medeo-diagnostics").toFile()
        try {
            val nowMs = 10_000L
            val store = DiagnosticLogStore(
                directory = directory,
                expiryMs = 1_000L,
                nowMs = { nowMs }
            )
            store.start("time=0 event=session_start")
            directory.resolve("medeo-diagnostic.log").setLastModified(nowMs - 1_001L)

            store.deleteExpired()

            assertFalse(directory.resolve("medeo-diagnostic.log").exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
