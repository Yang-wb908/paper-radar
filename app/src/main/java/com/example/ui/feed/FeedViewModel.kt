package com.example.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Field
import com.example.data.NetworkPaperRepository
import com.example.data.Paper
import com.example.data.PaperRepository
import com.example.data.SourcePrefs
import com.example.data.SyncInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FeedUiState(
    val papers: List<Paper> = emptyList(),
    val selectedField: Field? = null, // null means "All"
    val isRefreshing: Boolean = false,
    val showPreprints: Boolean = true,
    val lastSyncMillis: Long = 0L,
    val lastError: String? = null
)

class FeedViewModel(private val repository: PaperRepository) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _selectedField = MutableStateFlow<Field?>(null)
    private val concrete = repository as? NetworkPaperRepository

    // 백그라운드 워커나 설정 탭에서 시작된 동기화도 피드 상단에 그대로 비친다.
    private val syncInfo = concrete?.syncInfo() ?: flowOf(SyncInfo())

    // 프리프린트 포함 여부는 저장소 설정과 같은 값. 끄면 수집 자체를 안 하고, 저장된 것도 숨긴다.
    private val sourcePrefs = concrete?.sourcePrefs() ?: flowOf(SourcePrefs())

    val uiState: StateFlow<FeedUiState> = combine(
        repository.getPapers(),
        _selectedField,
        _isRefreshing,
        sourcePrefs,
        syncInfo
    ) { papers, selectedField, isRefreshing, prefs, info ->
        val filteredPapers = if (selectedField == null) {
            papers
        } else {
            papers.filter { it.fields.contains(selectedField) }
        }
        FeedUiState(
            papers = filteredPapers.sortedByDescending { it.publishedDate },
            selectedField = selectedField,
            isRefreshing = isRefreshing || info.isSyncing,
            showPreprints = prefs.arxivEnabled,
            lastSyncMillis = info.lastSyncMillis,
            lastError = info.lastError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FeedUiState()
    )

    fun togglePreprints() {
        val target = concrete ?: return
        val next = !uiState.value.showPreprints
        viewModelScope.launch { target.setPreprintsEnabled(next) }
    }

    fun setFieldFilter(field: Field?) {
        _selectedField.value = field
    }

    fun toggleBookmark(paperId: String) {
        viewModelScope.launch {
            repository.toggleBookmark(paperId)
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.refresh()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
