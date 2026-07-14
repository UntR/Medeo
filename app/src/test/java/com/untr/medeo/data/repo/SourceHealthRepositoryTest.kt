package com.untr.medeo.data.repo

import com.squareup.moshi.JsonDataException
import com.untr.medeo.data.api.dto.VodListResponse
import com.untr.medeo.data.local.SourceHealthStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SourceHealthRepositoryTest {
    @Test
    fun `code one is available`() = runTest {
        var elapsed = 100L

        val result = probeSourceApi(elapsedRealtimeMs = { elapsed }) {
            elapsed = 225L
            VodListResponse(code = 1)
        }

        assertEquals(SourceHealthStatus.AVAILABLE, result.status)
        assertEquals(125L, result.durationMs)
    }

    @Test
    fun `business error is unavailable`() = runTest {
        val result = probeSourceApi {
            VodListResponse(code = 0, msg = "failed")
        }

        assertEquals(SourceHealthStatus.UNAVAILABLE, result.status)
    }

    @Test
    fun `parsing failure is unavailable`() = runTest {
        val result = probeSourceApi {
            throw JsonDataException("invalid response")
        }

        assertEquals(SourceHealthStatus.UNAVAILABLE, result.status)
    }

    @Test
    fun `request has an eight second soft timeout`() = runTest {
        val result = probeSourceApi(
            elapsedRealtimeMs = { testScheduler.currentTime }
        ) {
            delay(SOURCE_HEALTH_TIMEOUT_MS + 1L)
            VodListResponse(code = 1)
        }

        assertEquals(SourceHealthStatus.UNAVAILABLE, result.status)
        assertEquals(SOURCE_HEALTH_TIMEOUT_MS, result.durationMs)
    }
}
