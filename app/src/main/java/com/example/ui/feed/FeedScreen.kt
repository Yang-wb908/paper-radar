package com.example.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.Field
import com.example.ui.components.EmptyState
import com.example.ui.components.FieldChip
import com.example.ui.components.PaperCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    uiState: FeedUiState,
    onFilterSelected: (Field?) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Show FAB if scrolled past 3 items
    val showFab by remember {
        derivedStateOf { listState.firstVisibleItemIndex >= 3 }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Paper Radar", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "검색")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "맨 위로")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Horizontal Field Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FieldChip(
                        field = Field.OTHER,
                        labelOverride = "전체",
                        selected = uiState.selectedField == null,
                        onClick = {
                            onFilterSelected(null)
                            coroutineScope.launch { listState.scrollToItem(0) }
                        }
                    )
                }
                items(Field.values()) { field ->
                    if (field != Field.OTHER) {
                        FieldChip(
                            field = field,
                            selected = uiState.selectedField == field,
                            onClick = {
                                onFilterSelected(field)
                                coroutineScope.launch { listState.scrollToItem(0) }
                            }
                        )
                    }
                }
                item {
                     FieldChip(
                            field = Field.OTHER,
                            selected = uiState.selectedField == Field.OTHER,
                            onClick = {
                                onFilterSelected(Field.OTHER)
                                coroutineScope.launch { listState.scrollToItem(0) }
                            }
                        )
                }
            }

            // Summary
            val fieldName = uiState.selectedField?.labelKo ?: "전체"
            Text(
                text = "논문 ${uiState.papers.size}건 · $fieldName",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Pull to Refresh Box & List
            val pullRefreshState = rememberPullToRefreshState()
            
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                if (uiState.papers.isEmpty()) {
                    EmptyState(message = "조건에 맞는 논문이 없습니다.")
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.papers, key = { it.id }) { paper ->
                            PaperCard(
                                paper = paper,
                                onClick = { onNavigateToDetail(paper.id) },
                                onBookmarkClick = { onToggleBookmark(paper.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
