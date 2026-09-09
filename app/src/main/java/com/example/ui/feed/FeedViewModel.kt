package com.example.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Field
import com.example.data.Paper
import com.example.data.PaperRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
    val papers: List<Paper> = emptyList(),
    val selectedField: Field? = null, // null means "All"
    val isRefreshing: Boolean = false
)

class FeedViewModel(private val repository: PaperRepository) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _selectedField = MutableStateFlow<Field?>(null)

    val uiState: StateFlow<FeedUiState> = combine(
        repository.getPapers(),
        _selectedField,
        _isRefreshing
    ) { papers, selectedField, isRefreshing ->
        val filteredPapers = if (selectedField == null) {
            papers
        } else {
            papers.filter { it.fields.contains(selectedField) }
        }
        FeedUiState(
            papers = filteredPapers.sortedByDescending { it.publishedDate },
            selectedField = selectedField,
            isRefreshing = isRefreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FeedUiState()
    )

    fun setFieldFilter(field: Field?) {
        _selectedField.value = field
    }

    fun toggleBookmark(paperId: String) {
        viewModelScope.launch {
            repository.toggleBookmark(paperId)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            // Fake refresh delay
            kotlinx.coroutines.delay(1000)
            _isRefreshing.value = false
        }
    }
}
