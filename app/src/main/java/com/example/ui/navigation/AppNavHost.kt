package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import com.example.data.AppGraph
import com.example.data.Field
import com.example.data.NetworkPaperRepository
import com.example.data.PaperRepository
import com.example.ui.detail.DetailScreen
import com.example.ui.detail.DetailViewModel
import com.example.ui.detail.DetailViewModelFactory
import com.example.ui.feed.FeedScreen
import com.example.ui.feed.FeedViewModel
import com.example.ui.feed.FeedViewModelFactory
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.library.LibraryViewModelFactory
import com.example.ui.search.SearchScreen
import com.example.ui.search.SearchViewModel
import com.example.ui.notifications.NotificationsScreen
import com.example.ui.notifications.NotificationsViewModel
import com.example.ui.notifications.NotificationsViewModelFactory
import com.example.ui.search.SearchViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import com.example.ui.settings.ARXIV_SOURCE_LABEL
import com.example.ui.settings.syncPeriodMinutes
import com.example.data.PaperRadarWork
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Feed : Screen("feed", "피드", Icons.Filled.List)
    object Search : Screen("search", "검색", Icons.Filled.Search)
    object Notifications : Screen("notifications", "알림", Icons.Filled.Notifications)
    object Library : Screen("library", "보관함", Icons.Filled.Bookmark)
    object Settings : Screen("settings", "설정", Icons.Filled.Settings)
}

val bottomNavItems = listOf(
    Screen.Feed,
    Screen.Search,
    Screen.Notifications,
    Screen.Library,
    Screen.Settings
)

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    repository: PaperRepository = AppGraph.repository(LocalContext.current)
) {
    val paperRepository = repository as? NetworkPaperRepository
    val unreadCount by (paperRepository?.unreadNotificationCount() ?: flowOf(0))
        .collectAsStateWithLifecycle(initialValue = 0)

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val isBottomBarVisible = bottomNavItems.any { it.route == currentDestination?.route }

            if (isBottomBarVisible) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                if (screen == Screen.Notifications && unreadCount > 0) {
                                    BadgedBox(badge = { Badge { Text(unreadCount.toString()) } }) {
                                        Icon(screen.icon, contentDescription = screen.title)
                                    }
                                } else {
                                    Icon(screen.icon, contentDescription = screen.title)
                                }
                            },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Feed.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Feed.route) {
                val viewModel: FeedViewModel = viewModel(factory = FeedViewModelFactory(repository))
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                FeedScreen(
                    uiState = uiState,
                    onFilterSelected = viewModel::setFieldFilter,
                    onTogglePreprints = viewModel::togglePreprints,
                    onToggleBookmark = viewModel::toggleBookmark,
                    onRefresh = viewModel::refresh,
                    onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                    onNavigateToDetail = { paperId -> navController.navigate("detail/$paperId") }
                )
            }
            composable(Screen.Search.route) {
                val viewModel: SearchViewModel = viewModel(factory = SearchViewModelFactory(repository))
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                SearchScreen(
                    uiState = uiState,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = viewModel::onSearch,
                    onToggleField = viewModel::toggleField,
                    onToggleBookmark = viewModel::toggleBookmark,
                    onNavigateToDetail = { paperId -> navController.navigate("detail/$paperId") }
                )
            }
            composable(Screen.Notifications.route) {
                val context = LocalContext.current
                val concrete = AppGraph.repository(context)
                val viewModel: NotificationsViewModel =
                    viewModel(factory = NotificationsViewModelFactory(concrete))
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val prefs by viewModel.prefs.collectAsStateWithLifecycle()
                NotificationsScreen(
                    uiState = uiState,
                    prefs = prefs,
                    onToggleNotifications = viewModel::toggleNotifications,
                    onToggleField = viewModel::toggleField,
                    onToggleBookmark = viewModel::toggleBookmark,
                    onNavigateToDetail = { paperId -> navController.navigate("detail/$paperId") }
                )
            }
            composable(Screen.Library.route) {
                val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModelFactory(repository))
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                LibraryScreen(
                    uiState = uiState,
                    onRemoveBookmark = viewModel::removeBookmark,
                    onNavigateToDetail = { paperId -> navController.navigate("detail/$paperId") }
                )
            }
            composable(Screen.Settings.route) {
                val viewModel: SettingsViewModel = viewModel()
                val context = LocalContext.current
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val concrete = repository as? NetworkPaperRepository
                // 저장돼 있던 소스 설정을 먼저 불러오고, 그 뒤의 토글만 저장소에 반영한다.
                LaunchedEffect(concrete) {
                    val saved = concrete?.sourcePrefs()?.first() ?: return@LaunchedEffect
                    viewModel.applySourcePrefs(saved.disabledJournals, saved.arxivEnabled, saved.syncPeriodMinutes)
                }
                val syncScope = rememberCoroutineScope()
                LaunchedEffect(concrete) {
                    concrete?.syncInfo()?.collect { info ->
                        viewModel.applySyncInfo(info.lastSyncMillis, info.paperCount)
                    }
                }
                LaunchedEffect(uiState.journals, uiState.syncPeriod, uiState.sourcePrefsLoaded) {
                    if (!uiState.sourcePrefsLoaded) return@LaunchedEffect
                    val disabled = uiState.journals.filterValues { !it }.keys
                        .filter { it != ARXIV_SOURCE_LABEL }
                        .toSet()
                    val arxiv = uiState.journals[ARXIV_SOURCE_LABEL] ?: true
                    val minutes = syncPeriodMinutes(uiState.syncPeriod)
                    val before = concrete?.sourcePrefs()?.first()
                    // 주기가 바뀌었을 때만 WorkManager 스케줄을 갱신한다(UPDATE 정책 → 다음 실행 시각 유지).
                    if (before != null && before.syncPeriodMinutes != minutes) {
                        PaperRadarWork.schedule(context, minutes, replace = true)
                    }
                    concrete?.updateSourcePrefs(disabled, arxiv, minutes)
                }
                SettingsScreen(
                    uiState = uiState,
                    onToggleField = viewModel::toggleField,
                    onToggleJournal = viewModel::toggleJournal,
                    onToggleAllJournals = viewModel::toggleAllJournals,
                    onToggleNotifications = viewModel::toggleNotifications,
                    onSetSyncPeriod = viewModel::setSyncPeriod,
                    onAddKeyword = viewModel::addKeyword,
                    onRemoveKeyword = viewModel::removeKeyword,
                    onSyncNow = { syncScope.launch { concrete?.refresh() } }
                )
            }
            composable(
                route = "detail/{paperId}",
                arguments = listOf(navArgument("paperId") { type = NavType.StringType })
            ) { backStackEntry ->
                val paperId = backStackEntry.arguments?.getString("paperId") ?: return@composable
                val viewModel: DetailViewModel = viewModel(factory = DetailViewModelFactory(paperId, repository))
                // 상세를 열면 읽음 처리 → 카드의 파란 점이 사라진다.
                LaunchedEffect(paperId) { (repository as? NetworkPaperRepository)?.markRead(paperId) }
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                DetailScreen(
                    uiState = uiState,
                    onBackClick = { navController.popBackStack() },
                    onToggleBookmark = viewModel::toggleBookmark
                )
            }
        }
    }
}
