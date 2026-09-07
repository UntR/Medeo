package com.untr.medeo.ui.review

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.squareup.moshi.Moshi
import com.untr.medeo.data.api.SourceCatalog
import com.untr.medeo.data.api.VodClientFactory
import com.untr.medeo.data.local.AppDatabase
import com.untr.medeo.data.local.SettingsStore
import com.untr.medeo.data.local.WatchProgress
import com.untr.medeo.data.model.Episode
import com.untr.medeo.data.model.PlaySource
import com.untr.medeo.data.model.VodDetail
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.data.net.NetworkMonitor
import com.untr.medeo.data.repo.*
import com.untr.medeo.player.CompactPlayerProgressBar
import com.untr.medeo.player.PlaybackErrorPanel
import com.untr.medeo.ui.adaptive.MedeoWindowClass
import com.untr.medeo.ui.components.EpisodePicker
import com.untr.medeo.ui.detail.DetailContent
import com.untr.medeo.ui.detail.SummaryCard
import com.untr.medeo.ui.favorites.FavoritesScreen
import com.untr.medeo.ui.favorites.FavoritesViewModel
import com.untr.medeo.ui.home.RecentWatchingCard
import com.untr.medeo.ui.theme.MedeoTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val viewModels = ViewModelStore()
    private var database: AppDatabase? = null

    @After fun tearDown() {
        compose.runOnIdle { viewModels.clear() }
        database?.close()
    }

    @Test fun historyEleventhRecordAndUndoKeepOriginalProgress() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().also { database = it }
        val favorites = FavoriteRepository(db.favoriteDao())
        val progressRepo = ProgressRepository(db.progressDao())
        val original = progress("最早记录", 1)
        runBlocking {
            db.progressDao().upsertForContent(original)
            (2..11).forEach { db.progressDao().upsertForContent(progress("记录$it", it.toLong())) }
            (1..30).forEach { favorites.setFavorite(item().copy(name = "收藏$it", vodId = 100L + it), true) }
        }
        val moshi = Moshi.Builder().build()
        val client = OkHttpClient.Builder().addInterceptor { error("History browsing must not request network") }.build()
        val catalog = SourceCatalog(SettingsStore(context, moshi), moshi)
        val factory = VodClientFactory(client, moshi)
        val recovery = ContentRecoveryRepository(catalog, SearchRepository(factory, catalog),
            DetailRepository(factory, catalog, PlaybackUrlValidator(client)), favorites, progressRepo, NetworkMonitor(context))
        lateinit var vm: FavoritesViewModel
        compose.runOnIdle {
            vm = FavoritesViewModel(favorites, progressRepo, DetailSelectionStore(), recovery)
            viewModels.put("history", vm)
        }
        compose.setContent {
            MedeoTheme { FavoritesScreen({}, {}, {}, windowClass = MedeoWindowClass.Compact, viewModel = vm) }
        }
        compose.waitUntil(5_000) { !vm.uiState.loading }
        compose.onNodeWithText(vm.uiState.items.first().name, substring = false).assertIsDisplayed()
        compose.onNodeWithText("观看记录").performClick()
        compose.onNodeWithText("搜索观看记录中的片名").performTextInput("最早")
        compose.onNodeWithText("最早记录").assertIsDisplayed()
        compose.onNodeWithText("继续观看").assertIsDisplayed()
        screenshot("history-eleventh")
        compose.onNodeWithText("删除记录").performClick()
        compose.onNodeWithText("撤销").performClick()
        compose.waitUntil(5_000) { vm.uiState.recentItems.any { it.contentKey == original.contentKey } }
        assertEquals(original, runBlocking { db.progressDao().findByContentKey(original.contentKey) })
        compose.onNodeWithText("最早记录").assertIsDisplayed()
        assertEquals(30, vm.uiState.items.size)
    }

    @Test fun detailPrimaryActionFitsSmallScreenAndDoubleFont() {
        val detail = detail()
        compose.setContent {
            TestSize(320, 640, 2f) {
                DetailContent(listOf(detail), emptySet(), emptyMap(), {}, { _, _, _, _ -> }, {}, MedeoWindowClass.Compact)
            }
        }
        compose.onNodeWithText("播放 第1集").assertIsDisplayed()
        screenshot("detail-320dp-font200")
    }

    @Test fun episode83AndSpecialTitleCanBeSelectedAfterSorting() {
        val episodes = detail().playSources.first().episodes + Episode("特别篇·重聚（上）——演员谈拍摄", "special")
        var selected = -1
        compose.setContent {
            TestSize(320, 640, 2f) {
                EpisodePicker(episodes, 82, { selected = it }, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithText("查找集名 / 集号").performTextInput("83")
        compose.onNodeWithText("第83集").assertIsDisplayed().performClick()
        assertEquals(82, selected)
        compose.onNodeWithText("原顺序").performClick()
        compose.onNodeWithText("第83集").performClick()
        assertEquals(82, selected)
        screenshot("episodes-83-font200")
        compose.onNodeWithText("查找集名 / 集号").performTextReplacement("重聚")
        compose.onNodeWithText("特别篇·重聚（上）——演员谈拍摄").assertIsDisplayed().performClick()
        assertEquals(100, selected)
    }

    @Test fun locateCurrentEpisodeScrollsWithoutStartingPlayback() {
        var selected = -1
        compose.setContent {
            TestSize(320, 640) {
                EpisodePicker(detail().playSources.first().episodes, 82, { selected = it }, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithText("定位当前集").performClick()
        compose.onNodeWithText("第83集").assertIsDisplayed()
        assertEquals(-1, selected)
    }

    @Test fun progressProvidesAccessibleSeekAction() {
        var position by mutableStateOf(10_000L)
        compose.setContent {
            TestSize(320, 120) { CompactPlayerProgressBar(position, 100_000L, 50, { position = it }, Modifier.width(300.dp)) }
        }
        compose.onNodeWithContentDescription("播放进度")
            .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0.75f)) }
        assertEquals(75_000L, position)
    }

    @Test fun singleLineErrorOnlyOffersRetryAndBackAtDoubleFont() {
        compose.setContent {
            TestSize(320, 180, 2f) {
                PlaybackErrorPanel("播放地址已失效", {}, null, {}, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithText("下一集").assertDoesNotExist()
        compose.onNodeWithText("换源/线路").assertDoesNotExist()
        compose.onNodeWithText("返回").performScrollTo().assertIsDisplayed()
        screenshot("single-line-error-font200")
    }

    @Test fun longSummaryCanBeExpandedAndCollapsed() {
        compose.setContent { TestSize(320, 640) { SummaryCard("很长的剧情介绍。".repeat(60)) } }
        compose.onNodeWithText("展开简介").performClick()
        compose.onNodeWithText("收起简介").assertExists()
    }

    @Test fun noHistoryHidesHomeResumeCard() {
        compose.setContent { MedeoTheme { RecentWatchingCard(null, false, {}, {}) } }
        compose.onNodeWithText("继续观看").assertDoesNotExist()
        compose.onNodeWithText("全部记录").assertDoesNotExist()
    }

    @Composable private fun TestSize(width: Int, height: Int, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
            MedeoTheme { Box(Modifier.width(width.dp).height(height.dp)) { content() } }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val dir = File(context.cacheDir, "review-qa").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun item() = VodItem("dbzy", "测试源", 1, "测试长剧", null, "2026", "中国大陆", "国产剧", null)
    private fun detail() = VodDetail(item(), "剧情介绍。".repeat(100), null, null,
        listOf(PlaySource("线路A", (1..100).map { Episode("第${it}集", "https://example.com/$it.m3u8") })))
    private fun progress(name: String, id: Long) = WatchProgress("$name|2026", name, null, "2026", "dbzy", id,
        "测试源", "线路A", 82, "第83集", 60_000, 120_000, id)
}
