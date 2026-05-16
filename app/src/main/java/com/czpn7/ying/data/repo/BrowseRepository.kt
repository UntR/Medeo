package com.czpn7.ying.data.repo

import android.util.Log
import com.czpn7.ying.data.api.DEFAULT_HOME_SOURCE
import com.czpn7.ying.data.api.VodClientFactory
import com.czpn7.ying.data.api.VodSource
import com.czpn7.ying.data.model.VodCategory
import com.czpn7.ying.data.model.VodItem
import com.czpn7.ying.data.model.toDomain
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BrowseResult(
    val items: List<VodItem>,
    val categories: List<VodCategory>
)

@Singleton
class BrowseRepository @Inject constructor(
    private val factory: VodClientFactory
) {
    suspend fun latest(
        source: VodSource = DEFAULT_HOME_SOURCE,
        page: Int = 1,
        typeId: Int? = null
    ): List<VodItem> = latestResult(source, page, typeId).items

    suspend fun latestResult(
        source: VodSource = DEFAULT_HOME_SOURCE,
        page: Int = 1,
        typeId: Int? = null
    ): BrowseResult = withContext(Dispatchers.IO) {
        runCatching {
            val response = factory.get(source)
                .list(pg = page, typeId = typeId)
            BrowseResult(
                items = response.list.map { dto -> dto.toDomain(source) },
                categories = response.classes.map { dto ->
                    VodCategory(typeId = dto.typeId, name = dto.typeName)
                }
            )
        }.getOrElse { error ->
            Log.w("BrowseRepository", "${source.name} latest failed", error)
            BrowseResult(items = emptyList(), categories = emptyList())
        }
    }
}
