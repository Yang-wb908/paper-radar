package com.example.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.width
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.DEFAULT_SYNC_PERIOD_MINUTES
import com.example.data.Field
import com.example.data.NetworkPaperRepository
import com.example.data.NotifPrefs
import com.example.data.NotifGroup
import com.example.ui.components.EmptyState
import com.example.ui.components.PaperCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
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

    val prefs: StateFlow<NotifPrefs> =
        repository.notificationPrefs()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = NotifPrefs()
            )

        val syncPeriodMinutes: StateFlow<Int> =
        repository.sourcePrefs()
            .map { it.syncPeriodMinutes }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = DEFAULT_SYNC_PERIOD_MINUTES
            )

    init {
        // 탭을 열면 읽음 처리해서 하단 배지를 지운다.
        viewModelScope.launch { repository.markNotificationsRead() }
    }

    fun toggleNotifications() {
        viewModelScope.launch {
            val current = repository.notificationPrefs().first()
            repository.updateNotificationPrefs(current.enabledFields, !current.notificationsEnabled)
        }
    }

    fun toggleField(field: Field) {
        viewModelScope.launch {
            val current = repository.notificationPrefs().first()
            val next = if (current.enabledFields.contains(field)) {
                current.enabledFields - field
            } else {
                current.enabledFields + field
            }
            repository.updateNotificationPrefs(next, current.notificationsEnabled)
        }
    }

    fun addKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val current = repository.notificationPrefs().first()
            repository.updateKeywords(current.keywords + trimmed)
        }
    }

    fun removeKeyword(keyword: String) {
        viewModelScope.launch {
            val current = repository.notificationPrefs().first()
            repository.updateKeywords(current.keywords - keyword)
        }
    }

    fun setQuietHours(startHour: Int, endHour: Int) {
        viewModelScope.launch { repository.updateQuietHours(startHour, endHour) }
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
    prefs: NotifPrefs,
    syncPeriodMinutes: Int,
    onToggleNotifications: () -> Unit,
    onToggleField: (Field) -> Unit,
    onAddKeyword: (String) -> Unit = {},
    onRemoveKeyword: (String) -> Unit = {},
    onSetQuietHours: (Int, Int) -> Unit = { _, _ -> },
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
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "prefs") {
                NotificationPrefsCard(
                    prefs = prefs,
                    syncPeriodMinutes = syncPeriodMinutes,
                    onToggleNotifications = onToggleNotifications,
                    onToggleField = onToggleField,
                    onAddKeyword = onAddKeyword,
                    onRemoveKeyword = onRemoveKeyword,
                    onSetQuietHours = onSetQuietHours
                )
            }

            if (uiState.groups.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        message = "아직 받은 알림이 없습니다.\n관심 분야에 새 논문이 올라오면 여기에 쌓입니다.",
                        modifier = Modifier.padding(top = 48.dp)
                    )
                }
            }

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationPrefsCard(
    prefs: NotifPrefs,
    syncPeriodMinutes: Int,
    onToggleNotifications: () -> Unit,
    onToggleField: (Field) -> Unit,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onSetQuietHours: (Int, Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "새 논문 알림",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = syncPeriodText(syncPeriodMinutes) + " · 조용 시간 23:00–08:00",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.notificationsEnabled,
                    onCheckedChange = { onToggleNotifications() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "알림 받을 분야",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (field in Field.values()) {
                    if (field == Field.OTHER) continue
                    FilterChip(
                        selected = prefs.enabledFields.contains(field),
                        onClick = { onToggleField(field) },
                        label = { Text(field.labelKo) },
                        enabled = prefs.notificationsEnabled
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "키워드 워치리스트",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "관심 분야가 아니어도 제목·초록에 이 단어가 있으면 알립니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            var keywordInput by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = keywordInput,
                    onValueChange = { keywordInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("예: perovskite") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = {
                    onAddKeyword(keywordInput)
                    keywordInput = ""
                }) { Text("추가") }
            }
            if (prefs.keywords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (keyword in prefs.keywords) {
                        InputChip(
                            selected = false,
                            onClick = { onRemoveKeyword(keyword) },
                            label = { Text(keyword) },
                            trailingIcon = { Text("×") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "조용 시간",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "이 시간대에는 알림을 보류했다가 끝나면 모아서 보냅니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HourStepper(
                    label = "시작",
                    hour = prefs.quietStartHour,
                    onChange = { onSetQuietHours(it, prefs.quietEndHour) }
                )
                Spacer(modifier = Modifier.width(16.dp))
                HourStepper(
                    label = "종료",
                    hour = prefs.quietEndHour,
                    onChange = { onSetQuietHours(prefs.quietStartHour, it) }
                )
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

/** "3시간마다 확인" 같은 안내 문구. 설정의 백그라운드 주기를 그대로 비춘다. */
private fun syncPeriodText(minutes: Int): String =
    if (minutes % 60 == 0) (minutes / 60).toString() + "시간마다 확인"
    else minutes.toString() + "분마다 확인"

/** 조용 시간 시작·종료를 한 시간 단위로 조절하는 작은 스테퍼. */
@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { onChange((hour + 23) % 24) }) { Text("−") }
        Text(
            text = String.format(Locale.KOREA, "%02d:00", hour),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(onClick = { onChange((hour + 1) % 24) }) { Text("+") }
    }
}
