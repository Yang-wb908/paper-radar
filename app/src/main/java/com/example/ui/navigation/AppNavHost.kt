package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import com.example.data.PaperRepositoryProvider
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
import com.example.ui.search.SearchViewModelFactory
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Feed : Screen("feed", "피드", Icons.Filled.List)
    object Search : Screen("search", "검색", Icons.Filled.Search)
    object Library : Screen("library", "보관함", Icons.Filled.Bookmark)
    object Settings : Screen("settings", "설정", Icons.Filled.Settings)
}

val bottomNavItems = listOf(
    Screen.Feed,
    Screen.Search,
    Screen.Library,
    Screen.Settings
)

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    repository: PaperRepository = PaperRepositoryProvider.instance
) {
    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val isBottomBarVisible = bottomNavItems.any { it.route == currentDestination?.route }

            if (isBottomBarVisible) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
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
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                SettingsScreen(
                    uiState = uiState,
                    onToggleField = viewModel::toggleField,
                    onToggleJournal = viewModel::toggleJournal,
                    onToggleAllJournals = viewModel::toggleAllJournals,
                    onToggleNotifications = viewModel::toggleNotifications,
                    onSetSyncPeriod = viewModel::setSyncPeriod,
                    onAddKeyword = viewModel::addKeyword,
                    onRemoveKeyword = viewModel::removeKeyword
                )
            }
            composable(
                route = "detail/{paperId}",
                arguments = listOf(navArgument("paperId") { type = NavType.StringType })
            ) { backStackEntry ->
                val paperId = backStackEntry.arguments?.getString("paperId") ?: return@composable
                val viewModel: DetailViewModel = viewModel(factory = DetailViewModelFactory(paperId, repository))
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
