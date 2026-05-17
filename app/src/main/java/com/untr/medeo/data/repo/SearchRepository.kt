package com.untr.medeo.data.repo

import android.util.Log
import com.untr.medeo.data.api.SourceCatalog
import com.untr.medeo.data.api.VodClientFactory
import com.untr.medeo.data.model.AggregatedResult
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.data.model.toDomain
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SearchProgress(
    val completedSources: Int = 0,
    val totalSources: Int = 0,
    val failedSources: Int = 0,
    val results: List<AggregatedResult> = emptyList(),
    val loading: Boolean = false
)

@Singleton
class SearchRepository @Inject constructor(
    private val factory: VodClientFactory,
    private val sourceCatalog: SourceCatalog
) {
    suspend fun search(keyword: String, page: Int = 1): List<AggregatedResult> =
        searchProgress(keyword, page).last().results

    fun searchProgress(keyword: String, page: Int = 1): Flow<SearchProgress> = channelFlow {
        val query = keyword.trim()
        if (query.isBlank()) {
            send(SearchProgress())
            return@channelFlow
        }

        val sources = sourceCatalog.enabledSources()
        if (sources.isEmpty()) {
            send(SearchProgress())
            return@channelFlow
        }

        val mutex = Mutex()
        val items = mutableListOf<VodItem>()
        var completed = 0
        var failed = 0

        send(
            SearchProgress(
                completedSources = 0,
                totalSources = sources.size,
                loading = true
            )
        )

        sources.forEach { source ->
            launch(Dispatchers.IO) {
                val result = runCatching {
                    factory.get(source)
                        .list(wd = query, pg = page)
                        .list
                        .map { dto -> dto.toDomain(source) }
                }
                val sourceItems = result.getOrElse { error ->
                    Log.w("SearchRepository", "${source.name} search failed", error)
                    emptyList()
                }

                val update = mutex.withLock {
                    items += sourceItems
                    completed += 1
                    if (result.isFailure) {
                        failed += 1
                    }
                    SearchUpdate(
                        completedSources = completed,
                        failedSources = failed,
                        items = items.toList()
                    )
                }
                send(
                    SearchProgress(
                        completedSources = update.completedSources,
                        totalSources = sources.size,
                        failedSources = update.failedSources,
                        results = aggregate(update.items),
                        loading = update.completedSources < sources.size
                    )
                )
            }
        }
    }

    private fun aggregate(items: List<VodItem>): List<AggregatedResult> =
        items.groupBy { it.dedupKey }
            .map { (key, group) ->
                val sortedGroup = group.sortedWith(
                    compareBy(
                        { sourceCatalog.playbackPriority(it.sourceId) },
                        { it.sourceName }
                    )
                )
                AggregatedResult(
                    dedupKey = key,
                    primary = sortedGroup.first(),
                    perSource = sortedGroup
                )
            }
}

private data class SearchUpdate(
    val completedSources: Int,
    val failedSources: Int,
    val items: List<VodItem>
)
