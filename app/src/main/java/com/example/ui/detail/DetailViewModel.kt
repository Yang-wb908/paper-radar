package com.example.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Paper
import com.example.data.PaperRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val paper: Paper? = null,
    val isLoading: Boolean = true
)

class DetailViewModel(
    private val paperId: String,
    private val repository: PaperRepository
) : ViewModel() {

    val uiState: StateFlow<DetailUiState> = repository.getPaperById(paperId)
        .map { paper ->
            DetailUiState(paper = paper, isLoading = false)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DetailUiState(isLoading = true)
        )

    fun toggleBookmark() {
        viewModelScope.launch {
            repository.toggleBookmark(paperId)
        }
    }
}
