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

/**
 * OpenAlex를 1차, Crossref를 2차로 쓰는 수집 저장소.
 * 결과는 PaperStore(JSON)로 영속화해서 앱을 껐다 켜도, 백그라운드 워커가 돌아도 이어진다.
 */
class NetworkPaperRepository(
    private val store: PaperStore? = null,
    private val journals: List<JournalSource> = JournalCatalog.ALL
) : PaperRepository {

    private val papers = MutableStateFlow<List<Paper>>(emptyList())
    private val bookmarks = MutableStateFlow<Set<String>>(emptySet())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()

    @Volatile
    private var notified: Set<String> = emptySet()

    @Volatile
    private var enabledFields: Set<Field> = setOf(Field.SEMI, Field.AI, Field.COMM, Field.ENERGY)

    @Volatile
    private var notificationsEnabled: Boolean = true

    @Volatile
    private var lastSyncMillis: Long = 0L

    init {
        scope.launch {
            restore()
            refresh()
        }
    }

    private suspend fun restore() {
        val saved = store?.load() ?: return
        papers.value = saved.papers
        bookmarks.value = saved.bookmarks
        notified = saved.notified
        enabledFields = saved.enabledFields.ifEmpty { enabledFields }
        notificationsEnabled = saved.notificationsEnabled
        lastSyncMillis = saved.lastSyncMillis
        Log.i(TAG, "restored " + saved.papers.size + " papers from disk")
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
        persist()
    }

    /** 설정 화면의 관심 분야·알림 스위치를 저장소에 반영한다. */
    suspend fun updateNotificationPrefs(fields: Set<Field>, enabled: Boolean) {
        if (fields == enabledFields && enabled == notificationsEnabled) return
        enabledFields = fields
        notificationsEnabled = enabled
        persist()
    }

    /**
     * 아직 알리지 않은, 관심 분야에 걸리는 논문을 돌려주고 알림 완료로 표시한다.
     * 워커가 한 번 소비하면 같은 논문으로 다시 알리지 않는다.
     */
    suspend fun consumeUnnotified(): List<Paper> {
        if (!notificationsEnabled) return emptyList()
        val fresh = papers.value.filter { paper ->
            !notified.contains(paper.id) && paper.fields.any { enabledFields.contains(it) }
        }
        if (fresh.isEmpty()) return emptyList()
        notified = notified + fresh.map { it.id }
        persist()
        return fresh.sortedByDescending { it.publishedDate }
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

            // 번역은 최신 논문부터. 논문당 한 번만 돌고 결과는 디스크에 남는다.
            val ordered = merged.values.sortedByDescending { it.publishedDate }
            papers.value = GeminiTranslator.translate(ordered)
            lastSyncMillis = now
            persist()
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

    private suspend fun persist() {
        val target = store ?: return
        target.save(
            StoredState(
                papers = papers.value,
                bookmarks = bookmarks.value,
                notified = notified,
                enabledFields = enabledFields,
                notificationsEnabled = notificationsEnabled,
                lastSyncMillis = lastSyncMillis
            )
        )
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        const val ABSTRACT_FETCH_BUDGET = 30
    }
}
