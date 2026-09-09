package com.example.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.example.data.DEFAULT_SYNC_PERIOD_MINUTES
import com.example.data.JournalCatalog

/** 설정 화면의 저널 목록에서 arXiv 전체를 대표하는 항목 이름. */
const val ARXIV_SOURCE_LABEL = "arXiv 프리프린트 (전체 카테고리)"

/** 백그라운드 동기화 주기 선택지. WorkManager 최소 주기가 15분이라 그 아래는 두지 않는다. */
val SYNC_PERIOD_OPTIONS: List<Pair<String, Int>> = listOf(
    "30분" to 30,
    "1시간" to 60,
    "3시간" to 180,
    "6시간" to 360,
    "12시간" to 720
)

fun syncPeriodLabel(minutes: Int): String =
    SYNC_PERIOD_OPTIONS.firstOrNull { it.second == minutes }?.first ?: "${minutes}분"

fun syncPeriodMinutes(label: String): Int =
    SYNC_PERIOD_OPTIONS.firstOrNull { it.first == label }?.second ?: DEFAULT_SYNC_PERIOD_MINUTES

data class SettingsUiState(
    val fields: Map<String, Boolean> = mapOf(
        "반도체·소자·재료" to true,
        "AI·머신러닝·컴퓨팅" to true,
        "통신·신호처리·회로" to true,
        "에너지·배터리·광학" to true
    ),
    val journals: Map<String, Boolean> =
        (listOf(ARXIV_SOURCE_LABEL) + JournalCatalog.ALL.map { it.name }).associateWith { true },
    val sourcePrefsLoaded: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val syncPeriod: String = syncPeriodLabel(DEFAULT_SYNC_PERIOD_MINUTES),
    val quietTimeStart: String = "23:00",
    val quietTimeEnd: String = "08:00",
    val keywords: List<String> = emptyList(),
    val lastSyncTime: String = "아직 동기화 전",
    val savedPaperCount: Int = 0
)

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun toggleField(fieldName: String) {
        _uiState.update { state ->
            val newFields = state.fields.toMutableMap()
            newFields[fieldName] = !(newFields[fieldName] ?: true)
            state.copy(fields = newFields)
        }
    }

    /** 저장소에 남아 있던 소스 설정을 화면 상태에 반영한다. 한 번만 호출된다. */
    fun applySourcePrefs(disabledJournals: Set<String>, arxivEnabled: Boolean, syncPeriodMinutes: Int) {
        _uiState.update { state ->
            val next = state.journals.keys.associateWith { name ->
                if (name == ARXIV_SOURCE_LABEL) arxivEnabled else !disabledJournals.contains(name)
            }
            state.copy(
                journals = next,
                syncPeriod = syncPeriodLabel(syncPeriodMinutes),
                sourcePrefsLoaded = true
            )
        }
    }

    /** 저장소의 실제 동기화 시각·건수를 화면에 반영한다. */
    fun applySyncInfo(lastSyncMillis: Long, paperCount: Int) {
        val label = if (lastSyncMillis == 0L) {
            "아직 동기화 전"
        } else {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.KOREA)
                .format(java.util.Date(lastSyncMillis))
        }
        _uiState.update { it.copy(lastSyncTime = label, savedPaperCount = paperCount) }
    }

    fun toggleJournal(journalName: String) {
        _uiState.update { state ->
            val newJournals = state.journals.toMutableMap()
            newJournals[journalName] = !(newJournals[journalName] ?: true)
            state.copy(journals = newJournals)
        }
    }

    fun toggleAllJournals(enabled: Boolean) {
        _uiState.update { state ->
            val newJournals = state.journals.mapValues { enabled }
            state.copy(journals = newJournals)
        }
    }

    fun toggleNotifications() {
        _uiState.update { it.copy(notificationsEnabled = !it.notificationsEnabled) }
    }

    fun setSyncPeriod(period: String) {
        _uiState.update { it.copy(syncPeriod = period) }
    }

    fun addKeyword(keyword: String) {
        if (keyword.isNotBlank() && !_uiState.value.keywords.contains(keyword)) {
            _uiState.update { it.copy(keywords = it.keywords + keyword) }
        }
    }

    fun removeKeyword(keyword: String) {
        _uiState.update { it.copy(keywords = it.keywords - keyword) }
    }
}
