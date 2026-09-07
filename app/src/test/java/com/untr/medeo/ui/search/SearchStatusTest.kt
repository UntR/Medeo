package com.untr.medeo.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchStatusTest {
    @Test fun partialFailuresRemainVisibleAfterCompletion() {
        assertEquals("搜索完成，4 个源成功、2 个失败，结果可能不完整",
            SearchUiState(completedSources = 6, totalSources = 6, failedSources = 2).sourceStatusLabel())
    }
    @Test fun completeDoesNotMeanSuccessful() {
        assertEquals("正在搜索：3/6 个源已处理，2 个源成功、1 个失败",
            SearchUiState(loading = true, completedSources = 3, totalSources = 6, failedSources = 1).sourceStatusLabel())
    }
}
