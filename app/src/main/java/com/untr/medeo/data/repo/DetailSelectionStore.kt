package com.untr.medeo.data.repo

import com.untr.medeo.data.model.AggregatedResult
import com.untr.medeo.data.model.VodItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DetailSelectionStore @Inject constructor() {
    private val selections = LinkedHashMap<String, List<VodItem>>()

    fun remember(item: VodItem) {
        remember(item.key, listOf(item))
    }

    fun remember(result: AggregatedResult) {
        remember(result.primary.key, result.perSource)
    }

    fun candidates(sourceId: String, vodId: Long): List<VodItem> =
        selections["$sourceId|$vodId"].orEmpty()

    private fun remember(primaryKey: String, items: List<VodItem>) {
        val distinct = items.distinctBy { it.key }
        selections[primaryKey] = distinct
        distinct.forEach { item -> selections[item.key] = distinct }
        trimToLimit()
    }

    private fun trimToLimit() {
        while (selections.size > MAX_ENTRIES) {
            val firstKey = selections.keys.firstOrNull() ?: return
            selections.remove(firstKey)
        }
    }

    private companion object {
        const val MAX_ENTRIES = 200
    }
}
