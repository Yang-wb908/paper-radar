package com.example.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.example.data.JournalCatalog

/** 설정 화면의 저널 목록에서 arXiv 전체를 대표하는 항목 이름. */
const val ARXIV_SOURCE_LABEL = "arXiv 프리프린트 (전체 카테고리)"

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
    val syncPeriod: String = "1시간",
    val quietTimeStart: String = "23:00",
    val quietTimeEnd: String = "08:00",
    val keywords: List<String> = emptyList(),
    val lastSyncTime: String = "2024-05-20 14:30:00",
    val savedPaperCount: Int = 15 // Just a dummy value for UI
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
    fun applySourcePrefs(disabledJournals: Set<String>, arxivEnabled: Boolean) {
        _uiState.update { state ->
            val next = state.journals.keys.associateWith { name ->
                if (name == ARXIV_SOURCE_LABEL) arxivEnabled else !disabledJournals.contains(name)
            }
            state.copy(journals = next, sourcePrefsLoaded = true)
        }
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
