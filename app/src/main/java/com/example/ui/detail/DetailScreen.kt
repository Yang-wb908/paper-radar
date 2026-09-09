package com.example.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.EmptyState
import com.example.ui.components.FieldChip
import com.example.ui.components.JournalBadge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    uiState: DetailUiState,
    onBackClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.paper == null) {
            EmptyState(message = "논문을 찾을 수 없습니다.", modifier = Modifier.padding(innerPadding))
        } else {
            val paper = uiState.paper
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp)
            ) {
                // Title
                Text(
                    text = paper.displayTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Authors
                Text(
                    text = paper.authorsLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Journal and Date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    JournalBadge(journalName = paper.journal)
                    val daysAgo = ((System.currentTimeMillis() - paper.publishedDate) / 86400000L).toInt()
                    Text(
                        text = if (daysAgo == 0) "오늘" else "${daysAgo}일 전",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tags
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    paper.fields.forEach { field ->
                        FieldChip(field = field)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Abstract Section
                Text(
                    text = "초록",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                val abstractBody = paper.displayAbstract
                if (abstractBody.isNullOrEmpty()) {
                    Text(
                        text = "초록 미제공 - 원문에서 확인",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                } else {
                    val displayAbstract = if (abstractBody.length > 400) {
                        abstractBody.take(400) + "…"
                    } else {
                        abstractBody
                    }
                    Text(
                        text = displayAbstract,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "출처: ${paper.journal}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Primary Action Button
                Button(
                    onClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("원문 링크 열기: ${paper.url}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("원문 보기")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Secondary Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("DOI 복사됨: ${paper.id}")
                        }
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "DOI 복사", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DOI 복사")
                    }

                    TextButton(onClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("공유하기")
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "공유", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("공유")
                    }

                    TextButton(onClick = onToggleBookmark) {
                        Icon(
                            if (paper.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "북마크",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (paper.isBookmarked) "북마크 해제" else "북마크")
                    }
                }
            }
        }
    }
}
