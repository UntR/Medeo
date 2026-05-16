package com.czpn7.ying.data.repo

import android.util.Log
import com.czpn7.ying.data.api.PlayUrlParser
import com.czpn7.ying.data.api.SourceCatalog
import com.czpn7.ying.data.api.VodClientFactory
import com.czpn7.ying.data.api.VodSource
import com.czpn7.ying.data.model.VodDetail
import com.czpn7.ying.data.model.VodItem
import com.czpn7.ying.data.model.PlaySource
import com.czpn7.ying.data.model.stripHtml
import com.czpn7.ying.data.model.toDomain
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
    private val playbackUrlValidator: PlaybackUrlValidator,
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
                    VodDetail(
                        item = it.toDomain(source),
                        content = it.vodContent?.stripHtml()?.takeIf { content -> content.isNotBlank() },
                        actor = it.vodActor?.takeIf { actor -> actor.isNotBlank() },
                        director = it.vodDirector?.takeIf { director -> director.isNotBlank() },
                        playSources = PlayUrlParser.parse(it.vodPlayFrom, it.vodPlayUrl)
                            .directMediaOnly()
                            .also { parsedSources ->
                                if (parsedSources.isEmpty()) {
                                    Log.w("DetailRepository", "${source.name} has no direct media urls")
                                }
                            },
                        playbackIssue = PlayUrlParser.parse(it.vodPlayFrom, it.vodPlayUrl)
                            .directMediaOnly()
                            .firstPlaybackIssue(playbackUrlValidator)
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

private suspend fun List<PlaySource>.firstPlaybackIssue(
    validator: PlaybackUrlValidator
): String? {
    val firstUrl = firstOrNull()?.episodes?.firstOrNull()?.url
        ?: return "没有直连播放地址"
    val validation = validator.validate(firstUrl)
    return if (validation.playable) null else validation.reason
}
