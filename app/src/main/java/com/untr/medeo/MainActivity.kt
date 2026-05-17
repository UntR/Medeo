package com.untr.medeo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.untr.medeo.data.local.AppThemeMode
import com.untr.medeo.player.PlayerScreen
import com.untr.medeo.ui.components.MessageState
import com.untr.medeo.ui.detail.DetailScreen
import com.untr.medeo.ui.favorites.FavoritesScreen
import com.untr.medeo.ui.home.HomeScreen
import com.untr.medeo.ui.nav.Routes
import com.untr.medeo.ui.search.SearchScreen
import com.untr.medeo.ui.settings.SettingsScreen
import com.untr.medeo.ui.startup.StartupViewModel
import com.untr.medeo.ui.theme.MedeoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedeoThemedRoot()
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MedeoThemedRoot(
    startupViewModel: StartupViewModel = hiltViewModel()
) {
    val startupState = startupViewModel.uiState
    MedeoTheme(darkTheme = startupState.themeMode == AppThemeMode.NIGHT) {
        MedeoAppRoot(startupViewModel = startupViewModel)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MedeoAppRoot(
    modifier: Modifier = Modifier,
    startupViewModel: StartupViewModel
) {
    val navController = rememberNavController()
    val startupState = startupViewModel.uiState
    val topLevelRoutes = listOf(Routes.HOME, Routes.FAVORITES, Routes.SETTINGS)
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination?.hierarchy
        ?.any { it.route in topLevelRoutes } == true

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                MedeoBottomBar(
                    currentRoute = currentDestination?.route,
                    onDestinationClick = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenDetail = { item ->
                        navController.navigate(Routes.detail(item.sourceId, item.vodId))
                    }
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { item ->
                        navController.navigate(Routes.detail(item.sourceId, item.vodId))
                    }
                )
            }
            composable(Routes.FAVORITES) {
                FavoritesScreen(
                    onOpenDetail = { item ->
                        navController.navigate(Routes.detail(item.sourceId, item.vodId))
                    }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(Routes.DETAIL_PATTERN) { backStackEntry ->
                DetailScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { sourceId, vodId, playSourceIndex, episodeIndex ->
                        navController.navigate(
                            Routes.player(sourceId, vodId, playSourceIndex, episodeIndex)
                        )
                    }
                )
            }
            composable(Routes.PLAYER_PATTERN) {
                PlayerScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    if (!startupState.loading && !startupState.disclaimerAccepted) {
        DisclaimerDialog(onAccept = startupViewModel::acceptDisclaimer)
    }
}

@Composable
private fun DisclaimerDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = "免责声明",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("本应用仅用于技术学习、个人研究和网络访问能力验证。")
                Text("应用不内置、不存储、不上传影视内容；搜索与播放结果来自用户自行启用的数据源。")
                Text("请确认你拥有合法授权后再访问相关内容，并遵守所在地法律法规和版权要求。")
                Text("继续使用即表示你已了解上述风险；不同意请停止使用并卸载本应用。")
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("我已了解并继续")
            }
        }
    )
}

@Composable
private fun MedeoBottomBar(
    currentRoute: String?,
    onDestinationClick: (TopLevelDestination) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentRoute == destination.route
                Column(
                    modifier = Modifier
                        .width(86.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .clickable { onDestinationClick(destination) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(if (selected) 76.dp else 58.dp)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(destination.iconRes),
                            contentDescription = destination.label,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private enum class TopLevelDestination(
    val route: String,
    val label: String,
    @DrawableRes val iconRes: Int
) {
    Home(Routes.HOME, "首页", R.drawable.ic_nav_home),
    Favorites(Routes.FAVORITES, "收藏", R.drawable.ic_nav_favorite),
    Settings(Routes.SETTINGS, "设置", R.drawable.ic_nav_settings)
}

@Preview(showBackground = true)
@Composable
private fun MedeoAppRootPreview() {
    MedeoTheme {
        MessageState("Medeo")
    }
}
