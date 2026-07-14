package com.untr.medeo.data.repo

import com.untr.medeo.data.api.VodClientFactory
import com.untr.medeo.data.api.VodSource
import com.untr.medeo.data.api.dto.VodListResponse
import com.untr.medeo.data.local.SettingsStore
import com.untr.medeo.data.local.SourceHealthRecord
import com.untr.medeo.data.local.SourceHealthStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class SourceHealthRepository @Inject constructor(
    private val clientFactory: VodClientFactory,
    private val settingsStore: SettingsStore
) {
    suspend fun test(source: VodSource): SourceHealthRecord {
        val result = probeSourceApi {
            clientFactory.get(source).list(pg = 1)
        }
        return SourceHealthRecord(
            sourceId = source.id,
            status = result.status,
            durationMs = result.durationMs,
            checkedAt = System.currentTimeMillis()
        ).also { settingsStore.setSourceHealth(it) }
    }
}

internal data class SourceProbeResult(
    val status: SourceHealthStatus,
    val durationMs: Long
)

internal suspend fun probeSourceApi(
    timeoutMs: Long = SOURCE_HEALTH_TIMEOUT_MS,
    elapsedRealtimeMs: () -> Long = { System.nanoTime() / 1_000_000L },
    request: suspend () -> VodListResponse
): SourceProbeResult {
    val startedAt = elapsedRealtimeMs()
    val response = withTimeoutOrNull(timeoutMs) {
        try {
            request()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
    return SourceProbeResult(
        status = if (response?.code == 1) {
            SourceHealthStatus.AVAILABLE
        } else {
            SourceHealthStatus.UNAVAILABLE
        },
        durationMs = (elapsedRealtimeMs() - startedAt).coerceAtLeast(0L)
    )
}

internal const val SOURCE_HEALTH_TIMEOUT_MS = 8_000L
