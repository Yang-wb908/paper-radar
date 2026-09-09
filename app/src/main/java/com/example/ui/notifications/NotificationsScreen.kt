package com.example.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.NetworkPaperRepository
import com.example.data.NotifGroup
import com.example.ui.components.EmptyState
import com.example.ui.components.PaperCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NotificationsUiState(
    val groups: List<NotifGroup> = emptyList()
)

class NotificationsViewModel(
    private val repository: NetworkPaperRepository
) : ViewModel() {

    val uiState: StateFlow<NotificationsUiState> =
        repository.getNotificationGroups()
            .map { NotificationsUiState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = NotificationsUiState()
            )

    init {
        // 탭을 열면 읽음 처리해서 하단 배지를 지운다.
        viewModelScope.launch { repository.markNotificationsRead() }
    }

    fun toggleBookmark(paperId: String) {
        viewModelScope.launch { repository.toggleBookmark(paperId) }
    }
}

class NotificationsViewModelFactory(
    private val repository: NetworkPaperRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotificationsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NotificationsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    uiState: NotificationsUiState,
    onToggleBookmark: (String) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("알림", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        if (uiState.groups.isEmpty()) {
            EmptyState(
                message = "아직 받은 알림이 없습니다.\n관심 분야에 새 논문이 올라오면 여기에 쌓입니다.",
                modifier = Modifier.padding(innerPadding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (group in uiState.groups) {
                item(key = "header-" + group.event.id) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(
                            text = "새 논문 " + group.event.paperIds.size + "건 · " + fieldSummary(group),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = formatTime(group.event.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(
                    count = group.papers.size,
                    key = { index -> group.event.id + "-" + group.papers[index].id }
                ) { index ->
                    val paper = group.papers[index]
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

private fun fieldSummary(group: NotifGroup): String {
    val labels = group.event.fields.map { it.labelKo }
    return when {
        labels.isEmpty() -> "관심 분야"
        labels.size == 1 -> labels.first()
        else -> labels.first() + " 외 " + (labels.size - 1) + "개 분야"
    }
}

private fun formatTime(millis: Long): String {
    val fmt = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)
    return fmt.format(Date(millis))
}
