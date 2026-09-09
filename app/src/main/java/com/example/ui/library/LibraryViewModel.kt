package com.example.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Paper
import com.example.data.PaperRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val bookmarkedPapers: List<Paper> = emptyList()
)

class LibraryViewModel(private val repository: PaperRepository) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = repository.getBookmarkedPapers()
        .map { papers -> LibraryUiState(bookmarkedPapers = papers) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryUiState()
        )

    fun removeBookmark(paperId: String) {
        viewModelScope.launch {
            repository.toggleBookmark(paperId)
        }
    }
}
