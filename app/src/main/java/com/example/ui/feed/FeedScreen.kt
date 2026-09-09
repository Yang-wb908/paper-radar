package com.example.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Refresh
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
    onTogglePreprints: () -> Unit = {},
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
                    // 당겨서 새로고침 외에 상단 버튼으로도 바로 동기화. 진행 중엔 스피너로 바뀐다.
                    if (uiState.isRefreshing) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                        }
                    }
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
                // 프리프린트(arXiv·bioRxiv) 포함 여부 — 홈에서 바로 끄고 켠다
                item {
                    FilterChip(
                        selected = uiState.showPreprints,
                        onClick = {
                            onTogglePreprints()
                            coroutineScope.launch { listState.scrollToItem(0) }
                        },
                        label = { Text(if (uiState.showPreprints) "프리프린트 포함" else "프리프린트 제외") }
                    )
                }
            }

            // Summary: 건수 · 분야 · 마지막 동기화
            val fieldName = uiState.selectedField?.labelKo ?: "전체"
            val syncText = if (uiState.isRefreshing) "동기화 중…" else formatSyncAge(uiState.lastSyncMillis)
            Text(
                text = "논문 ${uiState.papers.size}건 · $fieldName · $syncText",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 마지막 동기화가 실패했으면 이유를 한 줄로. 기존 목록은 그대로 보여준다.
            uiState.lastError?.let { error ->
                if (!uiState.isRefreshing) {
                    Text(
                        text = "동기화 실패: $error",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
            }

            // Pull to Refresh Box & List
            val pullRefreshState = rememberPullToRefreshState()

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                if (uiState.papers.isEmpty()) {
                    val message = when {
                        uiState.isRefreshing -> "최신 논문을 불러오는 중입니다…"
                        uiState.lastSyncMillis == 0L -> "아직 동기화 전입니다. 위의 새로고침을 눌러 주세요."
                        else -> "조건에 맞는 논문이 없습니다."
                    }
                    EmptyState(message = message)
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

/** "3분 전 동기화" 식의 상대 시각. 요약 줄에서만 쓴다. */
private fun formatSyncAge(lastSyncMillis: Long): String {
    if (lastSyncMillis <= 0L) return "아직 동기화 전"
    val minutes = (System.currentTimeMillis() - lastSyncMillis) / 60_000L
    return when {
        minutes < 1 -> "방금 동기화"
        minutes < 60 -> "${minutes}분 전 동기화"
        minutes < 60 * 24 -> "${minutes / 60}시간 전 동기화"
        else -> "${minutes / (60 * 24)}일 전 동기화"
    }
}
