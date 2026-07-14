package com.untr.medeo.data.repo

import com.untr.medeo.data.local.Favorite
import com.untr.medeo.data.local.FavoriteDao
import com.untr.medeo.data.local.preferredKey
import com.untr.medeo.data.model.VodItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FavoriteRepository @Inject constructor(
    private val favoriteDao: FavoriteDao
) {
    fun observeFavorites(): Flow<List<Favorite>> = favoriteDao.observeAll()

    fun observeFavoriteKeys(): Flow<Set<String>> =
        favoriteDao.observeAll().map { favorites ->
            buildSet {
                favorites.forEach { favorite ->
                    add(favorite.contentKey)
                    add(favorite.preferredKey)
                }
            }
        }

    suspend fun setFavorite(item: VodItem, favorite: Boolean) {
        if (favorite) {
            favoriteDao.upsertForContent(
                Favorite(
                    contentKey = item.contentKey,
                    name = item.name,
                    pic = item.pic,
                    year = item.year,
                    preferredSourceId = item.sourceId,
                    preferredVodId = item.vodId,
                    preferredSourceName = item.sourceName,
                    savedAt = System.currentTimeMillis()
                )
            )
        } else {
            favoriteDao.deleteByIdentity(
                contentKey = item.contentKey,
                sourceId = item.sourceId,
                vodId = item.vodId
            )
        }
    }

    suspend fun updatePreferred(original: VodItem, recovered: VodItem) {
        val stored = favoriteDao.findByIdentity(
            contentKey = original.contentKey,
            sourceId = original.sourceId,
            vodId = original.vodId
        ) ?: return
        val existing = favoriteDao.findByContentKey(recovered.contentKey)
        val savedAt = listOfNotNull(stored.savedAt, existing?.savedAt).min()
        favoriteDao.replaceIdentity(
            oldContentKey = stored.contentKey,
            favorite = Favorite(
                contentKey = recovered.contentKey,
                name = recovered.name,
                pic = recovered.pic ?: stored.pic,
                year = recovered.year,
                preferredSourceId = recovered.sourceId,
                preferredVodId = recovered.vodId,
                preferredSourceName = recovered.sourceName,
                savedAt = savedAt
            )
        )
    }
}

fun Favorite.toVodItem(): VodItem {
    return VodItem(
        sourceId = preferredSourceId,
        sourceName = preferredSourceName,
        vodId = preferredVodId,
        name = name,
        pic = pic,
        year = year,
        area = null,
        typeName = null,
        remarks = null
    )
}
