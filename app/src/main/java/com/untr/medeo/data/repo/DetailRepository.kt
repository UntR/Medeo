package com.untr.medeo.data.repo

import android.util.Log
import com.untr.medeo.data.api.PlayUrlParser
import com.untr.medeo.data.api.SourceCatalog
import com.untr.medeo.data.api.VodClientFactory
import com.untr.medeo.data.api.VodSource
import com.untr.medeo.data.model.VodDetail
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.data.model.PlaySource
import com.untr.medeo.data.model.stripHtml
import com.untr.medeo.data.model.toDomain
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DetailRepository @Inject constructor(
    private val factory: VodClientFactory,
    private val sourceCatalog: SourceCatalog
) {
    suspend fun detail(item: VodItem): VodDetail? {
        val source = sourceCatalog.sourceById(item.sourceId) ?: return null
        return detail(source, item.vodId)
    }

    suspend fun details(items: List<VodItem>): List<VodDetail> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                items.distinctBy { it.key }
                    .map { item -> async { detail(item) } }
                    .awaitAll()
                    .filterNotNull()
                    .filter { it.playSources.isNotEmpty() }
            }
        }

    suspend fun detail(source: VodSource, vodId: Long): VodDetail? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dto = factory.get(source)
                    .detail(ids = vodId.toString())
                    .list
                    .firstOrNull()

                dto?.let {
                    val parsedPlaySources = PlayUrlParser.parse(it.vodPlayFrom, it.vodPlayUrl)
                        .directMediaOnly()
                    VodDetail(
                        item = it.toDomain(source),
                        content = it.vodContent?.stripHtml()?.takeIf { content -> content.isNotBlank() },
                        actor = it.vodActor?.takeIf { actor -> actor.isNotBlank() },
                        director = it.vodDirector?.takeIf { director -> director.isNotBlank() },
                        playSources = parsedPlaySources.also { parsedSources ->
                            if (parsedSources.isEmpty()) {
                                Log.w("DetailRepository", "${source.name} has no playable media candidates")
                            }
                        }
                    )
                }
            }.getOrElse { error ->
                Log.w("DetailRepository", "${source.name} detail failed: $vodId", error)
                null
            }
        }
}

private fun List<PlaySource>.directMediaOnly(): List<PlaySource> =
    mapNotNull { source ->
        val episodes = source.episodes.filter { it.isDirectMedia() }
        if (episodes.isEmpty()) null else source.copy(episodes = episodes)
    }
