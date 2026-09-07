package com.untr.medeo.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.squareup.moshi.Moshi
import com.untr.medeo.data.api.SourceCatalog
import com.untr.medeo.data.api.VodClientFactory
import com.untr.medeo.data.diagnostics.DiagnosticLogger
import com.untr.medeo.data.local.AppDatabase
import com.untr.medeo.data.local.SettingsStore
import com.untr.medeo.data.model.VodItem
import com.untr.medeo.data.net.NetworkMonitor
import com.untr.medeo.data.repo.DetailRepository
import com.untr.medeo.data.repo.DetailSelectionStore
import com.untr.medeo.data.repo.PlaybackUrlValidator
import com.untr.medeo.data.repo.ProgressRepository
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerSwitchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchingUsesEpisodeIdentityAndUnmatchedSelectionNeedsConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val store = ViewModelStore()
        val moshi = Moshi.Builder().build()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val id = chain.request().url.queryParameter("ids")!!.toInt()
            val names = when (id) {
                1 -> listOf("EP01", "EP03")
                2 -> listOf("预告", "EP01", "EP02", "第03集")
                3 -> listOf("EP01", "EP02")
                else -> listOf("EP03", "EP02", "EP01")
            }
            val urls = names.mapIndexed { index, name -> "$name\$https://example.com/$id/$index.m3u8" }.joinToString("#")
            val payload = """{"code":1,"list":[{"vod_id":$id,"vod_name":"测试剧","vod_year":"2026","vod_play_from":"A","vod_play_url":"$urls"}]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(payload.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val settings = SettingsStore(context, moshi)
        val catalog = SourceCatalog(settings, moshi)
        val selections = DetailSelectionStore().apply {
            remember(listOf("dbzy", "dytt", "jszy", "hnzy").mapIndexed { index, source ->
                VodItem(source, source, index + 1L, "测试剧", null, "2026", null, "国产剧", null)
            })
        }
        lateinit var vm: PlayerViewModel
        try {
            compose.runOnIdle {
                vm = PlayerViewModel(
                    SavedStateHandle(mapOf("sourceId" to "dbzy", "vodId" to "1", "playSourceIndex" to "0", "episodeIndex" to "1")),
                    DetailRepository(VodClientFactory(client, moshi), catalog, PlaybackUrlValidator(client)),
                    selections, ProgressRepository(database.progressDao()), catalog, settings,
                    NetworkMonitor(context), DiagnosticLogger(context), DefaultMediaSourceFactory(context)
                )
                store.put("player", vm)
            }
            compose.waitUntil(10_000) { vm.uiState.details.size == 4 }
            fun detailIndex(source: String) = vm.uiState.details.indexOfFirst { it.item.sourceId == source }
            compose.runOnIdle {
                assertEquals("EP03", vm.uiState.episode(vm.detailIndex, vm.playSourceIndex, vm.episodeIndex)?.name)
                vm.saveProgress(60_000, 120_000)
                vm.selectDetail(detailIndex("dytt"))
                assertEquals(3, vm.episodeIndex)
                assertEquals(60_000L, vm.resumePositionForCurrentEpisode())
                vm.selectDetail(detailIndex("hnzy"))
                assertEquals(0, vm.episodeIndex)
                assertEquals(60_000L, vm.resumePositionForCurrentEpisode())
                vm.selectDetail(detailIndex("jszy"))
                assertNotNull(vm.pendingSelection)
                assertEquals(detailIndex("hnzy"), vm.detailIndex)
                assertEquals(0, vm.episodeIndex)
                vm.cancelPendingSelection()
                assertNull(vm.pendingSelection)
                assertEquals(detailIndex("hnzy"), vm.detailIndex)
                vm.selectDetail(detailIndex("jszy"))
                vm.confirmPendingEpisode(1)
                assertEquals("EP02", vm.uiState.episode(vm.detailIndex, vm.playSourceIndex, vm.episodeIndex)?.name)
                assertEquals(0L, vm.resumePositionForCurrentEpisode())
                vm.selectEpisode(99)
                assertEquals(1, vm.episodeIndex)
            }
        } finally {
            compose.runOnIdle { store.clear() }
            database.close()
        }
    }
}
