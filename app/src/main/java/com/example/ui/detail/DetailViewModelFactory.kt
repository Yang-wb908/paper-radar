package com.example.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.PaperRepository

class DetailViewModelFactory(private val paperId: String, private val repository: PaperRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetailViewModel(paperId, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
