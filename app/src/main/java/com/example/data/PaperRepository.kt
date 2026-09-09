package com.example.data

import kotlinx.coroutines.flow.Flow

interface PaperRepository {
    fun getPapers(): Flow<List<Paper>>
    fun getBookmarkedPapers(): Flow<List<Paper>>
    fun getPaperById(id: String): Flow<Paper?>
    suspend fun toggleBookmark(id: String)
}
