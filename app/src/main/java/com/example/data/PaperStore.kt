package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 앱이 재시작돼도 수집한 논문이 남아 있어야 백그라운드 동기화와 알림이 의미가 있다.
 * Room을 붙이지 않고 filesDir의 JSON 한 덩어리로 영속화한다(스키마 마이그레이션 부담 없음).
 */
data class StoredState(
    val papers: List<Paper> = emptyList(),
    val bookmarks: Set<String> = emptySet(),
    val notified: Set<String> = emptySet(),
    val enabledFields: Set<Field> = setOf(Field.SEMI, Field.AI, Field.COMM, Field.ENERGY, Field.BIO),
    val notificationsEnabled: Boolean = true,
    val lastSyncMillis: Long = 0L,
    val notifLog: List<NotifEvent> = emptyList(),
    val disabledJournals: Set<String> = emptySet(),
    val arxivEnabled: Boolean = true,
    val syncPeriodMinutes: Int = DEFAULT_SYNC_PERIOD_MINUTES,
    val sourceIds: Map<String, String> = emptyMap()
)

/** 알림 탭에 쌓이는 발송 기록 한 건. */
data class NotifEvent(
    val id: String,
    val timestamp: Long,
    val paperIds: List<String>,
    val fields: Set<Field>,
    val isRead: Boolean = false
)

/** 알림 한 건과 그때 걸린 논문들 (화면 표시용). */
data class NotifGroup(
    val event: NotifEvent,
    val papers: List<Paper>
)

class PaperStore(context: Context) {

    private val file = File(context.filesDir, "paper-radar-store.json")

    suspend fun load(): StoredState = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext StoredState()
        try {
            val root = JSONObject(file.readText())
            StoredState(
                papers = parsePapers(root.optJSONArray("papers")),
                bookmarks = parseStrings(root.optJSONArray("bookmarks")),
                notified = parseStrings(root.optJSONArray("notified")),
                enabledFields = parseFields(root.optJSONArray("enabledFields")),
                notificationsEnabled = root.optBoolean("notificationsEnabled", true),
                lastSyncMillis = root.optLong("lastSyncMillis", 0L),
                notifLog = parseNotifLog(root.optJSONArray("notifLog")),
                disabledJournals = parseStrings(root.optJSONArray("disabledJournals")),
                arxivEnabled = root.optBoolean("arxivEnabled", true),
                syncPeriodMinutes = root.optInt("syncPeriodMinutes", DEFAULT_SYNC_PERIOD_MINUTES),
                sourceIds = parseStringMap(root.optJSONObject("sourceIds"))
            )
        } catch (e: Exception) {
            Log.w(TAG, "store load failed: " + e.message)
            StoredState()
        }
    }

    suspend fun save(state: StoredState) = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            val papers = JSONArray()
            // 상한을 두지 않으면 파일이 무한히 커진다. 북마크는 항상 남기고 나머지는 최신순 상한.
            val keep = state.papers
                .sortedByDescending { it.publishedDate }
                .filterIndexed { index, paper -> index < MAX_PAPERS || state.bookmarks.contains(paper.id) }
            for (paper in keep) papers.put(toJson(paper))
            root.put("papers", papers)
            root.put("bookmarks", JSONArray(state.bookmarks.toList()))
            root.put("notified", JSONArray(state.notified.toList().takeLast(MAX_NOTIFIED)))
            root.put("enabledFields", JSONArray(state.enabledFields.map { it.name }))
            root.put("notificationsEnabled", state.notificationsEnabled)
            root.put("lastSyncMillis", state.lastSyncMillis)
            val notifLog = JSONArray()
            for (event in state.notifLog.takeLast(MAX_NOTIF_EVENTS)) {
                notifLog.put(
                    JSONObject()
                        .put("id", event.id)
                        .put("timestamp", event.timestamp)
                        .put("paperIds", JSONArray(event.paperIds))
                        .put("fields", JSONArray(event.fields.map { it.name }))
                        .put("isRead", event.isRead)
                )
            }
            root.put("notifLog", notifLog)
            root.put("disabledJournals", JSONArray(state.disabledJournals.toList()))
            root.put("arxivEnabled", state.arxivEnabled)
            root.put("syncPeriodMinutes", state.syncPeriodMinutes)
            root.put("sourceIds", JSONObject(state.sourceIds))
            file.writeText(root.toString())
        } catch (e: Exception) {
            Log.w(TAG, "store save failed: " + e.message)
        }
    }

    private fun toJson(paper: Paper): JSONObject = JSONObject().apply {
        put("id", paper.id)
        put("title", paper.title)
        put("authorsLine", paper.authorsLine)
        put("journal", paper.journal)
        put("publishedDate", paper.publishedDate)
        put("abstractText", paper.abstractText ?: JSONObject.NULL)
        put("url", paper.url)
        put("fields", JSONArray(paper.fields.map { it.name }))
        put("isRead", paper.isRead)
    }

    private fun parsePapers(array: JSONArray?): List<Paper> {
        if (array == null) return emptyList()
        val out = ArrayList<Paper>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
            out.add(
                Paper(
                    id = id,
                    title = o.optString("title"),
                    authorsLine = o.optString("authorsLine"),
                    journal = o.optString("journal"),
                    publishedDate = o.optLong("publishedDate", 0L),
                    abstractText = if (o.isNull("abstractText")) null else o.optString("abstractText"),
                    url = o.optString("url"),
                    fields = parseFields(o.optJSONArray("fields")),
                    isBookmarked = false,
                    isRead = o.optBoolean("isRead", false)
                )
            )
        }
        return out
    }

    private fun parseNotifLog(array: JSONArray?): List<NotifEvent> {
        if (array == null) return emptyList()
        val out = ArrayList<NotifEvent>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
            out.add(
                NotifEvent(
                    id = id,
                    timestamp = o.optLong("timestamp", 0L),
                    paperIds = parseStrings(o.optJSONArray("paperIds")).toList(),
                    fields = parseFields(o.optJSONArray("fields")),
                    isRead = o.optBoolean("isRead", false)
                )
            )
        }
        return out
    }

    private fun parseStringMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val out = HashMap<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.optString(key)
            if (value.isNotBlank()) out[key] = value
        }
        return out
    }

    private fun parseStrings(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        val out = LinkedHashSet<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (value.isNotBlank()) out.add(value)
        }
        return out
    }

    private fun parseFields(array: JSONArray?): Set<Field> {
        if (array == null) return emptySet()
        val out = LinkedHashSet<Field>()
        for (i in 0 until array.length()) {
            val name = array.optString(i)
            val field = Field.values().firstOrNull { it.name == name }
            if (field != null) out.add(field)
        }
        return out
    }

    private companion object {
        const val MAX_PAPERS = 3000
        const val MAX_NOTIFIED = 4000
        const val MAX_NOTIF_EVENTS = 100
    }
}
