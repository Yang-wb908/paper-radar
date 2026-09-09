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

/** 알림 탭이 구독하는 알림 설정. */
data class NotifPrefs(
    val enabledFields: Set<Field> = setOf(Field.SEMI, Field.AI, Field.COMM, Field.ENERGY, Field.BIO),
    val notificationsEnabled: Boolean = true
)

/** 설정 탭의 "데이터" 섹션과 피드 상단 요약이 같이 구독한다. */
data class SyncInfo(
    val lastSyncMillis: Long = 0L,
    val paperCount: Int = 0,
    val isSyncing: Boolean = false,
    val lastError: String? = null
)

/** 설정 탭이 구독하는 소스 설정. */
data class SourcePrefs(
    val disabledJournals: Set<String> = emptySet(),
    val arxivEnabled: Boolean = true
)

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
    private val notifLog = MutableStateFlow<List<NotifEvent>>(emptyList())
    private val notifPrefs = MutableStateFlow(NotifPrefs())
    private val sourcePrefs = MutableStateFlow(SourcePrefs())
    private val lastSync = MutableStateFlow(0L)
    private val syncing = MutableStateFlow(false)
    private val lastError = MutableStateFlow<String?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()

    @Volatile
    private var notified: Set<String> = emptySet()

    @Volatile
    private var enabledFields: Set<Field> = setOf(Field.SEMI, Field.AI, Field.COMM, Field.ENERGY, Field.BIO)

    @Volatile
    private var notificationsEnabled: Boolean = true

    @Volatile
    private var lastSyncMillis: Long = 0L

    init {
        scope.launch {
            restore()
            refreshIfStale()
        }
    }

    private suspend fun restore() {
        val saved = store?.load() ?: return
        papers.value = saved.papers
        bookmarks.value = saved.bookmarks
        notified = saved.notified
        notifLog.value = saved.notifLog
        enabledFields = saved.enabledFields.ifEmpty { enabledFields }
        notificationsEnabled = saved.notificationsEnabled
        lastSyncMillis = saved.lastSyncMillis
        lastSync.value = saved.lastSyncMillis
        notifPrefs.value = NotifPrefs(enabledFields, notificationsEnabled)
        sourcePrefs.value = SourcePrefs(saved.disabledJournals, saved.arxivEnabled)
        OpenAlexSource.preload(saved.sourceIds)
        Log.i(TAG, "restored " + saved.papers.size + " papers from disk")
    }

    /**
     * 앱을 켤 때마다 전 소스를 다시 긁으면 OpenAlex·arXiv 429가 잦다.
     * 최근에 동기화했으면 디스크 내용을 그대로 쓰고, 사용자가 원하면 새로고침 버튼으로 강제한다.
     */
    private suspend fun refreshIfStale() {
        val age = System.currentTimeMillis() - lastSyncMillis
        if (lastSyncMillis != 0L && age < STALE_MILLIS) {
            Log.i(TAG, "last sync " + (age / 60_000L) + "m ago - skipping startup refresh")
            return
        }
        refresh()
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

    /** 상세 화면을 열면 읽음 처리. 카드의 파란 점이 사라진다. */
    suspend fun markRead(id: String) {
        val current = papers.value
        val index = current.indexOfFirst { it.id == id }
        if (index < 0 || current[index].isRead) return
        val next = current.toMutableList()
        next[index] = current[index].copy(isRead = true)
        papers.value = next
        persist()
    }

    /** 설정 화면의 관심 분야·알림 스위치를 저장소에 반영한다. */
    suspend fun updateNotificationPrefs(fields: Set<Field>, enabled: Boolean) {
        if (fields == enabledFields && enabled == notificationsEnabled) return
        enabledFields = fields
        notificationsEnabled = enabled
        notifPrefs.value = NotifPrefs(fields, enabled)
        persist()
    }

    fun notificationPrefs(): Flow<NotifPrefs> = notifPrefs

    fun sourcePrefs(): Flow<SourcePrefs> = sourcePrefs

    fun syncInfo(): Flow<SyncInfo> =
        combine(lastSync, papers, syncing, lastError) { at, list, busy, error ->
            SyncInfo(at, list.size, busy, error)
        }

    suspend fun updateSourcePrefs(disabledJournals: Set<String>, arxivEnabled: Boolean) {
        val next = SourcePrefs(disabledJournals, arxivEnabled)
        if (next == sourcePrefs.value) return
        sourcePrefs.value = next
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
        val picked = fresh.sortedByDescending { it.publishedDate }
        val event = NotifEvent(
            id = "n" + System.currentTimeMillis(),
            timestamp = System.currentTimeMillis(),
            paperIds = picked.take(20).map { it.id },
            fields = picked.flatMap { it.fields }.filter { it != Field.OTHER }.toSet()
        )
        notifLog.value = (notifLog.value + event).takeLast(MAX_EVENTS)
        persist()
        return picked
    }

    /** 알림 탭용: 발송 기록과 그때 걸린 논문을 묶어 최신순으로 돌려준다. */
    fun getNotificationGroups(): Flow<List<NotifGroup>> =
        combine(notifLog, papers, bookmarks) { events, list, marked ->
            val byId = list.associateBy { it.id }
            events.sortedByDescending { it.timestamp }.map { event ->
                NotifGroup(
                    event = event,
                    papers = event.paperIds.mapNotNull { byId[it] }
                        .map { it.copy(isBookmarked = marked.contains(it.id)) }
                )
            }
        }

    fun unreadNotificationCount(): Flow<Int> =
        notifLog.map { list -> list.count { !it.isRead } }

    suspend fun markNotificationsRead() {
        if (notifLog.value.none { !it.isRead }) return
        notifLog.value = notifLog.value.map { it.copy(isRead = true) }
        persist()
    }

    override suspend fun refresh(): Result<Int> {
        // 이미 도는 동기화가 있으면 한 번 더 긁지 않고 그 결과를 같이 쓴다.
        if (!refreshLock.tryLock()) {
            Log.i(TAG, "refresh already running - skipped")
            return Result.success(0)
        }
        syncing.value = true
        try {
            val now = System.currentTimeMillis()
            // 첫 동기화는 최근 3주치를 긁고, 이후에는 3일 마진만 다시 본다.
            val lookbackDays = if (lastSyncMillis == 0L) 21 else 3
            val sinceDay = isoDay(now - lookbackDays * DAY_MILLIS)

            val activeJournals = journals.filter { !sourcePrefs.value.disabledJournals.contains(it.name) }
            var fetched = OpenAlexSource.fetchRecent(activeJournals, sinceDay)

            if (fetched.isEmpty()) {
                Log.w(TAG, "OpenAlex empty - falling back to Crossref per journal")
                val collected = ArrayList<Paper>()
                for (journal in activeJournals) {
                    collected.addAll(CrossrefSource.fetchJournalWorks(journal, sinceDay))
                }
                fetched = collected
            }

            // 프리프린트: arXiv(설정으로 on/off) + bioRxiv
            if (sourcePrefs.value.arxivEnabled) fetched = fetched + ArxivSource.fetchRecent()
            fetched = fetched + BioRxivSource.fetchRecent(isoDay(now - 2 * DAY_MILLIS), isoDay(now))

            if (fetched.isEmpty()) {
                lastError.value = "어느 소스에서도 논문을 받지 못했습니다. 네트워크를 확인해 주세요."
                return Result.failure(IllegalStateException("수집된 논문이 없습니다"))
            }

            // 초록 보강: Crossref → Semantic Scholar 순. IEEE 결손이 주 대상.
            val enriched = SemanticScholarSource.fillAbstracts(fillMissingAbstracts(fetched))

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
            lastSync.value = now
            lastError.value = null
            persist()
            Log.i(TAG, "refresh done: +" + added + " new, " + merged.size + " total")
            return Result.success(added)
        } catch (e: Exception) {
            Log.w(TAG, "refresh failed: " + e.message)
            lastError.value = describe(e)
            return Result.failure(e)
        } finally {
            syncing.value = false
            refreshLock.unlock()
        }
    }

    /** 사용자에게 보여줄 한 줄짜리 실패 사유. 스택은 로그에만 남긴다. */
    private fun describe(e: Exception): String {
        val message = e.message.orEmpty()
        return when {
            e is java.net.UnknownHostException -> "인터넷에 연결되어 있지 않습니다."
            e is java.net.SocketTimeoutException -> "서버 응답이 늦어 중단했습니다. 잠시 후 다시 시도해 주세요."
            message.contains("429") -> "요청이 잦아 잠시 제한됐습니다. 몇 분 뒤 다시 시도해 주세요."
            message.isBlank() -> e.javaClass.simpleName
            else -> message.take(80)
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
                lastSyncMillis = lastSyncMillis,
                notifLog = notifLog.value,
                disabledJournals = sourcePrefs.value.disabledJournals,
                arxivEnabled = sourcePrefs.value.arxivEnabled,
                sourceIds = OpenAlexSource.snapshot()
            )
        )
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        const val STALE_MILLIS = 15L * 60L * 1000L
        const val ABSTRACT_FETCH_BUDGET = 30
        const val MAX_EVENTS = 100
    }
}
