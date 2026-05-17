package com.untr.medeo.data.repo

import com.untr.medeo.data.local.Favorite
import com.untr.medeo.data.local.FavoriteDao
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
        favoriteDao.observeAll().map { favorites -> favorites.mapTo(linkedSetOf()) { it.key } }

    suspend fun setFavorite(item: VodItem, favorite: Boolean) {
        if (favorite) {
            favoriteDao.upsert(
                Favorite(
                    key = item.key,
                    name = item.name,
                    pic = item.pic,
                    sourceName = item.sourceName,
                    savedAt = System.currentTimeMillis()
                )
            )
        } else {
            favoriteDao.deleteByKey(item.key)
        }
    }
}

fun Favorite.toVodItem(): VodItem {
    val parts = key.split("|", limit = 2)
    val sourceId = parts.getOrNull(0).orEmpty()
    val vodId = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    return VodItem(
        sourceId = sourceId,
        sourceName = sourceName,
        vodId = vodId,
        name = name,
        pic = pic,
        year = null,
        area = null,
        typeName = null,
        remarks = null
    )
}
