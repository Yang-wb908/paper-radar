package com.example.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val fields: Map<String, Boolean> = mapOf(
        "반도체·소자·재료" to true,
        "AI·머신러닝·컴퓨팅" to true,
        "통신·신호처리·회로" to true,
        "에너지·배터리·광학" to true
    ),
    val journals: Map<String, Boolean> = listOf(
        "Nature", "Science", "Science Advances", "Nature Communications", "Nature Materials",
        "Nature Electronics", "Nature Nanotechnology", "Nature Machine Intelligence",
        "Nature Computational Science", "Nature Energy", "Nature Photonics", "Advanced Materials",
        "Advanced Functional Materials", "Advanced Energy Materials", "IEEE Electron Device Letters",
        "IEEE Transactions on Electron Devices", "IEEE Journal of Solid-State Circuits",
        "IEEE Transactions on Communications", "IEEE Transactions on Signal Processing",
        "IEEE Transactions on Pattern Analysis and Machine Intelligence", "Joule", "Light: Science & Applications"
    ).associateWith { true },
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
