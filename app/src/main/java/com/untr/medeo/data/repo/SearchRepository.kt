package com.untr.medeo.data.repo

import android.util.Log
import com.untr.medeo.data.api.SourceCatalog
import com.untr.medeo.data.api.VodClientFactory
import com.untr.medeo.data.api.VodSource
import com.untr.medeo.data.api.dto.VodListResponse
import com.untr.medeo.data.model.AggregatedResult
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.data.model.normalize
import com.untr.medeo.data.model.toDomain
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class SearchProgress(
    val completedSources: Int = 0,
    val totalSources: Int = 0,
    val failedSources: Int = 0,
    val results: List<AggregatedResult> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false
)

@Singleton
class SearchRepository @Inject constructor(
    private val factory: VodClientFactory,
    private val sourceCatalog: SourceCatalog
) {
    private val sessions = ConcurrentHashMap<SearchCacheKey, SearchSession>()

    suspend fun search(keyword: String, page: Int = 1): List<AggregatedResult> =
        searchProgress(keyword, page = page, loadMore = page > 1).last().results

    fun searchProgress(
        keyword: String,
        page: Int = 1,
        loadMore: Boolean = false
    ): Flow<SearchProgress> = channelFlow {
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

        val cacheKey = SearchCacheKey(
            query = query.lowercase(),
            sourceIds = sources.map { it.id }
        )
        val cachedSession = sessions[cacheKey]?.takeIf { it.isFresh() }
            ?: run {
                sessions.remove(cacheKey)
                null
            }
        if (!loadMore && page == FIRST_PAGE && cachedSession != null) {
            send(cachedSession.toProgress(query = query, sources = sources))
            return@channelFlow
        }

        val appendToSession = loadMore && cachedSession != null
        val baseSession = cachedSession.takeIf { appendToSession }
        val mutex = Mutex()
        var items = baseSession?.items?.toMutableList() ?: mutableListOf()
        val pageStates = baseSession?.pageStates?.toMutableMap() ?: mutableMapOf()
        val failedSourceIds = baseSession?.failedSourceIds?.toMutableSet() ?: mutableSetOf()
        var completed = 0
        val requestSources = if (appendToSession) {
            sources.filter { source -> pageStates[source.id]?.hasMore == true }
        } else {
            sources
        }

        if (requestSources.isEmpty()) {
            send(
                SearchProgress(
                    completedSources = sources.size,
                    totalSources = sources.size,
                    failedSources = failedSourceIds.size,
                    results = aggregate(items, query),
                    loading = false,
                    loadingMore = false,
                    hasMore = hasMore(pageStates, sources)
                )
            )
            return@channelFlow
        }

        val initialResults = aggregate(items, query)
        val initialHasMore = hasMore(pageStates, sources)
        send(
            SearchProgress(
                completedSources = 0,
                totalSources = requestSources.size,
                failedSources = failedSourceIds.size,
                results = initialResults,
                loading = !loadMore,
                loadingMore = loadMore,
                hasMore = initialHasMore
            )
        )

        requestSources.forEach { source ->
            launch(Dispatchers.IO) {
                val requestedPage = if (appendToSession) {
                    (pageStates[source.id]?.currentPage ?: 0) + 1
                } else {
                    page.coerceAtLeast(FIRST_PAGE)
                }
                val result = searchSource(
                    source = source,
                    query = query,
                    page = requestedPage,
                    previousState = pageStates[source.id]
                )

                val update = mutex.withLock {
                    items = mergeSearchItems(items, result.items).toMutableList()
                    pageStates[source.id] = result.pageState
                    completed += 1
                    if (result.failed) {
                        failedSourceIds += source.id
                    } else {
                        failedSourceIds -= source.id
                    }

                    val session = SearchSession(
                        items = items.toList(),
                        pageStates = pageStates.toMap(),
                        failedSourceIds = failedSourceIds.toSet(),
                        updatedAt = System.currentTimeMillis()
                    )
                    sessions[cacheKey] = session

                    SearchUpdate(
                        completedSources = completed,
                        failedSources = failedSourceIds.size,
                        items = session.items,
                        pageStates = session.pageStates
                    )
                }
                send(
                    SearchProgress(
                        completedSources = update.completedSources,
                        totalSources = requestSources.size,
                        failedSources = update.failedSources,
                        results = aggregate(update.items, query),
                        loading = !loadMore && update.completedSources < requestSources.size,
                        loadingMore = loadMore && update.completedSources < requestSources.size,
                        hasMore = hasMore(update.pageStates, sources)
                    )
                )
            }
        }
    }

    private suspend fun searchSource(
        source: VodSource,
        query: String,
        page: Int,
        previousState: SourcePageState?
    ): SourceSearchResult {
        val response = withTimeoutOrNull(SOURCE_SEARCH_TIMEOUT_MS) {
            runCatching {
                factory.get(source).list(wd = query, pg = page)
            }
        }
        if (response == null) {
            Log.w("SearchRepository", "${source.name} search timed out at page $page")
            return SourceSearchResult(
                items = emptyList(),
                pageState = previousState ?: SourcePageState(currentPage = page, pageCount = page),
                failed = true
            )
        }

        val vodResponse = response.getOrElse { error ->
            Log.w("SearchRepository", "${source.name} search failed", error)
            return SourceSearchResult(
                items = emptyList(),
                pageState = previousState ?: SourcePageState(currentPage = page, pageCount = page),
                failed = true
            )
        }

        val result = vodResponse.toSourceSearchResult(
            source = source,
            requestedPage = page,
            previousState = previousState
        )
        if (result.failed) {
            Log.w("SearchRepository", "${source.name} search returned code ${vodResponse.code}")
        }
        return result
    }

    private fun aggregate(items: List<VodItem>, query: String): List<AggregatedResult> =
        aggregateSearchResults(
            items = items,
            query = query,
            playbackPriority = sourceCatalog::playbackPriority
        )

    private fun SearchSession.toProgress(
        query: String,
        sources: List<VodSource>
    ): SearchProgress =
        SearchProgress(
            completedSources = sources.size,
            totalSources = sources.size,
            failedSources = failedSourceIds.size,
            results = aggregate(items, query),
            loading = false,
            loadingMore = false,
            hasMore = hasMore(pageStates, sources)
        )

    private fun hasMore(
        pageStates: Map<String, SourcePageState>,
        sources: List<VodSource>
    ): Boolean =
        sources.any { source -> pageStates[source.id]?.hasMore == true }
}

internal fun VodListResponse.toSourceSearchResult(
    source: VodSource,
    requestedPage: Int,
    previousState: SourcePageState?
): SourceSearchResult {
    if (code != 1) {
        return SourceSearchResult(
            items = emptyList(),
            pageState = previousState ?: SourcePageState(
                currentPage = requestedPage,
                pageCount = requestedPage
            ),
            failed = true
        )
    }

    val responsePage = page.coerceAtLeast(requestedPage)
    return SourceSearchResult(
        items = list.map { dto -> dto.toDomain(source) },
        pageState = SourcePageState(
            currentPage = responsePage,
            pageCount = pagecount.coerceAtLeast(responsePage)
        ),
        failed = false
    )
}

internal fun aggregateSearchResults(
    items: List<VodItem>,
    query: String,
    playbackPriority: (String) -> Int
): List<AggregatedResult> =
    mergeSearchItems(items)
        .groupBy { it.dedupKey }
        .map { (key, group) ->
            val sortedGroup = group.sortedWith(
                compareBy(
                    { playbackPriority(it.sourceId) },
                    { it.sourceName }
                )
            )
            AggregatedResult(
                dedupKey = key,
                primary = sortedGroup.first(),
                perSource = sortedGroup
            )
        }
        .sortedWith(
            compareBy<AggregatedResult> { result ->
                result.perSource.minOf { item -> searchRelevanceRank(item.name, query) }
            }
                .thenByDescending { result -> result.perSource.size }
                .thenByDescending { result -> result.primary.year?.toIntOrNull() ?: 0 }
                .thenBy { result -> result.primary.name }
        )

private fun mergeSearchItems(
    existing: List<VodItem>,
    incoming: List<VodItem> = emptyList()
): List<VodItem> =
    (existing + incoming).distinctBy { it.key }

private fun searchRelevanceRank(title: String, query: String): Int {
    val normalizedTitle = normalize(title)
    val normalizedQuery = normalize(query)
    return when {
        normalizedQuery.isBlank() -> 3
        normalizedTitle == normalizedQuery -> 0
        normalizedTitle.startsWith(normalizedQuery) -> 1
        normalizedTitle.contains(normalizedQuery) -> 2
        else -> 3
    }
}

internal data class SourceSearchResult(
    val items: List<VodItem>,
    val pageState: SourcePageState,
    val failed: Boolean
)

internal data class SourcePageState(
    val currentPage: Int,
    val pageCount: Int
) {
    val hasMore: Boolean get() = currentPage < pageCount
}

private data class SearchSession(
    val items: List<VodItem>,
    val pageStates: Map<String, SourcePageState>,
    val failedSourceIds: Set<String>,
    val updatedAt: Long
) {
    fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs - updatedAt <= SEARCH_CACHE_TTL_MS
}

private data class SearchCacheKey(
    val query: String,
    val sourceIds: List<String>
)

private data class SearchUpdate(
    val completedSources: Int,
    val failedSources: Int,
    val items: List<VodItem>,
    val pageStates: Map<String, SourcePageState>
)

private const val FIRST_PAGE = 1
private const val SOURCE_SEARCH_TIMEOUT_MS = 8_000L
private const val SEARCH_CACHE_TTL_MS = 5L * 60L * 1000L
