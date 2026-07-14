package com.untr.medeo.data.repo

import com.untr.medeo.data.api.SourceCatalog
import com.untr.medeo.data.api.VodSource
import com.untr.medeo.data.model.AggregatedResult
import com.untr.medeo.data.model.VodDetail
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.data.model.normalize
import com.untr.medeo.data.net.NetworkMonitor
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ContentRecoveryResult {
    data class Success(
        val candidates: List<VodItem>,
        val details: List<VodDetail>,
        val usedFallback: Boolean
    ) : ContentRecoveryResult {
        val primary: VodItem get() = details.first().item
    }

    data object NoEnabledSources : ContentRecoveryResult
    data object NetworkUnavailable : ContentRecoveryResult
    data object NotFound : ContentRecoveryResult
}

@Singleton
class ContentRecoveryRepository @Inject constructor(
    private val sourceCatalog: SourceCatalog,
    private val searchRepository: SearchRepository,
    private val detailRepository: DetailRepository,
    private val favoriteRepository: FavoriteRepository,
    private val progressRepository: ProgressRepository,
    private val networkMonitor: NetworkMonitor
) {
    suspend fun recover(original: VodItem): ContentRecoveryResult {
        val enabledSources = sourceCatalog.enabledSources()
        if (enabledSources.isEmpty()) return ContentRecoveryResult.NoEnabledSources
        if (!networkMonitor.snapshot().online) return ContentRecoveryResult.NetworkUnavailable
        val result = recoverContent(
            original = original,
            enabledSources = enabledSources,
            loadDetails = detailRepository::details,
            search = searchRepository::search
        )
        if (result is ContentRecoveryResult.Success) {
            favoriteRepository.updatePreferred(original, result.primary)
            progressRepository.updatePreferred(original, result.primary)
        }
        return result
    }
}

internal suspend fun recoverContent(
    original: VodItem,
    enabledSources: List<VodSource>,
    loadDetails: suspend (List<VodItem>) -> List<VodDetail>,
    search: suspend (String) -> List<AggregatedResult>
): ContentRecoveryResult {
    if (enabledSources.isEmpty()) return ContentRecoveryResult.NoEnabledSources

    if (enabledSources.any { source -> source.id == original.sourceId }) {
        val originalDetails = loadDetails(listOf(original))
        if (originalDetails.isNotEmpty()) {
            return ContentRecoveryResult.Success(
                candidates = originalDetails.map { detail -> detail.item },
                details = originalDetails,
                usedFallback = false
            )
        }
    }

    if (original.name.isBlank()) return ContentRecoveryResult.NotFound
    val matched = matchingRecoveryResult(
        results = search(original.name),
        name = original.name,
        year = original.year
    ) ?: return ContentRecoveryResult.NotFound
    val details = loadDetails(matched.perSource)
    if (details.isEmpty()) return ContentRecoveryResult.NotFound

    return ContentRecoveryResult.Success(
        candidates = details.map { detail -> detail.item },
        details = details,
        usedFallback = true
    )
}

internal fun matchingRecoveryResult(
    results: List<AggregatedResult>,
    name: String,
    year: String?
): AggregatedResult? {
    val normalizedName = normalize(name)
    val normalizedYear = year.orEmpty().trim()
    if (normalizedName.isBlank()) return null

    return results.firstNotNullOfOrNull { result ->
        val candidates = result.perSource.filter { item ->
            normalize(item.name) == normalizedName &&
                (normalizedYear.isBlank() || item.year.orEmpty().trim() == normalizedYear)
        }
        if (candidates.isEmpty()) {
            null
        } else {
            result.copy(
                dedupKey = candidates.first().dedupKey,
                primary = candidates.first(),
                perSource = candidates
            )
        }
    }
}
