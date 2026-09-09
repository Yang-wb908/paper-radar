package com.example.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 앱 전역에서 하나의 저장소 인스턴스를 공유한다(화면 전환마다 재수집 방지). */
object PaperRepositoryProvider {
    val instance: PaperRepository by lazy { NetworkPaperRepository() }
}

/**
 * OpenAlex를 1차, Crossref를 2차로 쓰는 실제 수집 저장소.
 *
 * 아직 영속화(Room)는 붙이지 않았다. 프로세스가 살아 있는 동안만 누적하고,
 * 다음 단계에서 Room + WorkManager로 옮긴다.
 */
class NetworkPaperRepository(
    private val journals: List<JournalSource> = JournalCatalog.ALL
) : PaperRepository {

    private val papers = MutableStateFlow<List<Paper>>(emptyList())
    private val bookmarks = MutableStateFlow<Set<String>>(emptySet())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()

    @Volatile
    private var lastSyncMillis: Long = 0L

    init {
        scope.launch { refresh() }
    }

    override fun getPapers(): Flow<List<Paper>> =
        combine(papers, bookmarks) { list, marked ->
            list.map { it.copy(isBookmarked = marked.contains(it.id)) }
                .sortedByDescending { it.publishedDate }
        }

    override fun getBookmarkedPapers(): Flow<List<Paper>> =
        getPapers().map { list -> list.filter { it.isBookmarked } }

    override fun getPaperById(id: String): Flow<Paper?> =
        getPapers().map { list -> list.firstOrNull { it.id == id } }

    override suspend fun toggleBookmark(id: String) {
        val current = bookmarks.value
        bookmarks.value = if (current.contains(id)) current - id else current + id
    }

    override suspend fun refresh(): Result<Int> = refreshLock.withLock {
        try {
            val now = System.currentTimeMillis()
            // 첫 동기화는 최근 3주치를 긁고, 이후에는 3일 마진만 다시 본다.
            val lookbackDays = if (lastSyncMillis == 0L) 21 else 3
            val sinceDay = isoDay(now - lookbackDays * DAY_MILLIS)

            var fetched = OpenAlexSource.fetchRecent(journals, sinceDay)

            if (fetched.isEmpty()) {
                Log.w(TAG, "OpenAlex empty - falling back to Crossref per journal")
                val collected = ArrayList<Paper>()
                for (journal in journals) {
                    collected.addAll(CrossrefSource.fetchJournalWorks(journal, sinceDay))
                }
                fetched = collected
            }

            if (fetched.isEmpty()) {
                return@withLock Result.failure(IllegalStateException("수집된 논문이 없습니다"))
            }

            val enriched = fillMissingAbstracts(fetched)

            val merged = LinkedHashMap<String, Paper>()
            papers.value.forEach { merged[it.id] = it }
            var added = 0
            for (paper in enriched) {
                val existing = merged[paper.id]
                if (existing == null) {
                    merged[paper.id] = paper
                    added++
                } else if (existing.abstractText == null && paper.abstractText != null) {
                    merged[paper.id] = existing.copy(abstractText = paper.abstractText)
                }
            }

            papers.value = merged.values.sortedByDescending { it.publishedDate }
            lastSyncMillis = now
            Log.i(TAG, "refresh done: +" + added + " new, " + merged.size + " total")
            Result.success(added)
        } catch (e: Exception) {
            Log.w(TAG, "refresh failed: " + e.message)
            Result.failure(e)
        }
    }

    /**
     * 초록이 빈 논문은 Crossref로 한 번 더 물어본다.
     * IEEE 계열은 어느 소스에도 초록이 없는 경우가 많아 한 사이클당 상한을 둔다.
     */
    private suspend fun fillMissingAbstracts(input: List<Paper>): List<Paper> {
        var budget = ABSTRACT_FETCH_BUDGET
        return input.map { paper ->
            if (paper.abstractText != null || budget <= 0) {
                paper
            } else {
                budget--
                val abstract = CrossrefSource.fetchAbstract(paper.id)
                if (abstract == null) paper else paper.copy(abstractText = abstract)
            }
        }
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        const val ABSTRACT_FETCH_BUDGET = 20
    }
}
