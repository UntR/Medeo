package com.untr.medeo.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.untr.medeo.BuildConfig
import com.untr.medeo.data.api.VodSource
import com.untr.medeo.data.local.AppSettings
import com.untr.medeo.data.local.AppThemeMode
import com.untr.medeo.data.repo.CacheUsage
import com.untr.medeo.ui.theme.MedeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clearCache_requiresExplicitConfirmation() {
        var clearCount = 0
        composeRule.setContent {
            MedeoTheme {
                CacheCard(
                    settings = settings(),
                    usage = CacheUsage(mediaBytes = 1L, imageBytes = 2L, httpBytes = 3L),
                    clearing = false,
                    message = null,
                    onMediaCacheMbChange = { _ -> },
                    onImageCacheMbChange = { _ -> },
                    onClear = { clearCount += 1 }
                )
            }
        }

        composeRule.onNodeWithText("清空缓存").performClick()

        composeRule.onNodeWithText("清空全部缓存？").assertIsDisplayed()
        composeRule.onNodeWithText(
            "将删除媒体、图片和接口缓存；不会删除收藏、观看进度或设置。"
        ).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, clearCount) }

        composeRule.onNodeWithText("确认清空").performClick()

        composeRule.runOnIdle { assertEquals(1, clearCount) }
        assertEquals(
            0,
            composeRule.onAllNodesWithText("清空全部缓存？").fetchSemanticsNodes().size
        )
    }

    @Test
    fun aboutCard_showsBuildVersionAndOpenSourceDeclaration() {
        composeRule.setContent {
            MedeoTheme { AboutCard() }
        }

        composeRule.onNodeWithText("关于").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Medeo ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "本项目采用 MIT License 开源；第三方组件遵循各自开源许可证。"
        ).assertIsDisplayed()
    }

    @Test
    fun sourceRow_explainsApiCheckBoundary() {
        composeRule.setContent {
            MedeoTheme {
                SourceRow(
                    source = VodSource(
                        id = "test",
                        name = "测试源",
                        baseUrl = "https://example.com/api.php/provide/vod",
                        defaultEnabled = true
                    ),
                    checked = true,
                    health = null,
                    testing = false,
                    onCheckedChange = {},
                    onTest = {}
                )
            }
        }

        composeRule.onNodeWithText(
            "仅测试 API 基础列表；可用不代表关键词搜索或媒体播放一定可用"
        ).assertIsDisplayed()
    }

    private fun settings(): AppSettings =
        AppSettings(
            enabledSourceIds = emptySet(),
            mediaCacheMb = 500,
            imageCacheMb = 200,
            httpCacheMb = 50,
            wifiOnlyPlay = false,
            sourceManifestUrl = "",
            remoteSourceManifestJson = null,
            remoteSourceManifestUpdatedAt = null,
            disclaimerAccepted = true,
            themeMode = AppThemeMode.DAY
        )
}
