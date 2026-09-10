package com.example.ui.search

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
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val searchResults: List<Paper> = emptyList(),
    val selectedFields: Set<Field> = emptySet(),
    val recentSearches: List<String> = listOf("Tandem solar cell", "LLM reasoning")
)

class SearchViewModel(private val repository: PaperRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedFields = MutableStateFlow<Set<Field>>(emptySet())
    private val _recentSearches = MutableStateFlow(listOf("Tandem solar cell", "LLM reasoning"))

    val uiState: StateFlow<SearchUiState> = combine(
        repository.getPapers(),
        _query,
        _selectedFields,
        _recentSearches
    ) { papers, query, fields, recent ->
        val results = if (query.isBlank() && fields.isEmpty()) {
            emptyList()
        } else {
            papers.filter { paper ->
                val haystack = listOfNotNull(
                        paper.title, paper.abstractText,
                        paper.journal, paper.authorsLine
                    )
                    val matchesQuery = query.isBlank() || haystack.any { it.contains(query, ignoreCase = true) }
                val matchesFields = fields.isEmpty() || paper.fields.any { it in fields }
                matchesQuery && matchesFields
            }
        }

        SearchUiState(
            query = query,
            searchResults = results,
            selectedFields = fields,
            recentSearches = recent
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchUiState()
    )

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun onSearch(query: String) {
        if (query.isNotBlank() && !_recentSearches.value.contains(query)) {
            _recentSearches.value = listOf(query) + _recentSearches.value.take(4)
        }
    }

    fun toggleField(field: Field) {
        val current = _selectedFields.value
        if (current.contains(field)) {
            _selectedFields.value = current - field
        } else {
            _selectedFields.value = current + field
        }
    }

    fun toggleBookmark(paperId: String) {
        viewModelScope.launch {
            repository.toggleBookmark(paperId)
        }
    }
}
