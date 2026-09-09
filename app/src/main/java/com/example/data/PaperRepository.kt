package com.example.data

import kotlinx.coroutines.flow.Flow

interface PaperRepository {
    fun getPapers(): Flow<List<Paper>>
    fun getBookmarkedPapers(): Flow<List<Paper>>
    fun getPaperById(id: String): Flow<Paper?>
    suspend fun toggleBookmark(id: String)

    /** 원격 소스에서 신규 논문을 가져온다. 반환값은 새로 추가된 건수. */
    suspend fun refresh(): Result<Int> = Result.success(0)
}
