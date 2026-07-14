package com.untr.medeo.data.local

import com.squareup.moshi.JsonClass

enum class SourceHealthStatus {
    AVAILABLE,
    UNAVAILABLE
}

@JsonClass(generateAdapter = true)
data class SourceHealthRecord(
    val sourceId: String,
    val status: SourceHealthStatus,
    val durationMs: Long,
    val checkedAt: Long
)

@JsonClass(generateAdapter = true)
data class SourceHealthPayload(
    val records: List<SourceHealthRecord> = emptyList()
) {
    fun bySourceId(): Map<String, SourceHealthRecord> =
        records.associateBy(SourceHealthRecord::sourceId)

    fun withRecord(record: SourceHealthRecord): SourceHealthPayload =
        SourceHealthPayload(
            records = (records.filterNot { it.sourceId == record.sourceId } + record)
                .sortedBy(SourceHealthRecord::sourceId)
        )
}
