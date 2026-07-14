package com.untr.medeo.data.local

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SourceHealthTest {
    @Test
    fun `stored health contains only source status duration and time`() {
        val payload = SourceHealthPayload().withRecord(
            SourceHealthRecord(
                sourceId = "dbzy",
                status = SourceHealthStatus.AVAILABLE,
                durationMs = 123L,
                checkedAt = 456L
            )
        )
        val adapter = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(SourceHealthPayload::class.java)

        val json = adapter.toJson(payload)
        val decoded = adapter.fromJson(json)

        assertEquals(payload, decoded)
        assertFalse(json.contains("url", ignoreCase = true))
        assertFalse(json.contains("exception", ignoreCase = true))
        assertFalse(json.contains("response", ignoreCase = true))
    }
}
