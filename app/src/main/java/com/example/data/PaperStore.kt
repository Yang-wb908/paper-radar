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
    val enabledFields: Set<Field> = setOf(Field.SEMI, Field.AI, Field.COMM, Field.ENERGY),
    val notificationsEnabled: Boolean = true,
    val lastSyncMillis: Long = 0L
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
                lastSyncMillis = root.optLong("lastSyncMillis", 0L)
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
        put("titleKo", paper.titleKo ?: JSONObject.NULL)
        put("abstractKo", paper.abstractKo ?: JSONObject.NULL)
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
                    isRead = o.optBoolean("isRead", false),
                    titleKo = if (o.isNull("titleKo")) null else o.optString("titleKo"),
                    abstractKo = if (o.isNull("abstractKo")) null else o.optString("abstractKo")
                )
            )
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
    }
}

/**
 * 제목·초록 한국어 번역기. Gemini API를 쓰고, 논문 하나당 딱 한 번만 번역해 저장한다.
 *
 * API 키는 AI Studio Secrets(.env의 GEMINI_API_KEY)에서 온다.
 * 키가 없으면 조용히 원문을 그대로 돌려준다 — 번역이 없다고 앱이 죽으면 안 된다.
 */
object GeminiTranslator {

    private const val MODEL = "gemini-2.5-flash"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent"

    private const val BATCH_SIZE = 8
    private const val MAX_BATCHES_PER_SYNC = 3
    private const val ABSTRACT_LIMIT = 1200

    val isAvailable: Boolean
        get() = apiKey().isNotBlank()

    /** BuildConfig는 .env가 있을 때만 필드가 생기므로 리플렉션으로 안전하게 읽는다. */
    private fun apiKey(): String = try {
        val clazz = Class.forName("com.example.BuildConfig")
        (clazz.getField("GEMINI_API_KEY").get(null) as? String).orEmpty().trim()
    } catch (e: Throwable) {
        ""
    }

    suspend fun translate(papers: List<Paper>): List<Paper> = withContext(Dispatchers.IO) {
        val key = apiKey()
        if (key.isBlank()) {
            Log.i(TAG, "GEMINI_API_KEY not set - skipping translation")
            return@withContext papers
        }

        val targets = papers.filter { it.needsTranslation }
        if (targets.isEmpty()) return@withContext papers

        val translations = HashMap<String, Pair<String?, String?>>()
        var batches = 0
        for (chunk in targets.chunked(BATCH_SIZE)) {
            if (batches >= MAX_BATCHES_PER_SYNC) break
            batches++
            translateChunk(key, chunk)?.let { translations.putAll(it) }
        }

        if (translations.isEmpty()) return@withContext papers

        papers.map { paper ->
            val hit = translations[paper.id] ?: return@map paper
            paper.copy(
                titleKo = hit.first ?: paper.titleKo,
                abstractKo = hit.second ?: paper.abstractKo
            )
        }
    }

    private fun translateChunk(key: String, chunk: List<Paper>): Map<String, Pair<String?, String?>>? {
        val items = JSONArray()
        chunk.forEachIndexed { index, paper ->
            val item = JSONObject()
            item.put("i", index)
            item.put("t", paper.title)
            paper.abstractText?.let { item.put("a", it.take(ABSTRACT_LIMIT)) }
            items.put(item)
        }

        val instruction = buildString {
            append("다음은 학술 논문의 제목(t)과 초록(a)이다. 각 항목을 자연스러운 한국어로 번역하라.\n")
            append("규칙:\n")
            append("- 전문 용어는 널리 쓰이는 한국어 표기를 쓰되, 정착된 번역어가 없으면 영문을 그대로 둔다.\n")
            append("- 물질명·소자명·약어(MoS2, CMOS, LLM 등)는 번역하지 않는다.\n")
            append("- 초록은 요약하지 말고 원문 순서대로 번역한다.\n")
            append("- 설명이나 사족을 붙이지 말고 JSON만 출력한다.\n")
            append("출력 형식: {\"r\":[{\"i\":0,\"t\":\"번역된 제목\",\"a\":\"번역된 초록\"}]}\n")
            append("입력:\n")
            append(items.toString())
        }

        val part = JSONObject().put("text", instruction)
        val content = JSONObject()
            .put("role", "user")
            .put("parts", JSONArray().put(part))
        val body = JSONObject()
            .put("contents", JSONArray().put(content))
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("responseMimeType", "application/json")
            )

        val response = Http.postJson(ENDPOINT + "?key=" + key, body) ?: return null
        val text = response
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            ?: return null

        return parseTranslation(text, chunk)
    }

    private fun parseTranslation(raw: String, chunk: List<Paper>): Map<String, Pair<String?, String?>>? {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return try {
            val results = JSONObject(cleaned).optJSONArray("r") ?: return null
            val out = HashMap<String, Pair<String?, String?>>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val index = item.optInt("i", -1)
                val paper = chunk.getOrNull(index) ?: continue
                val titleKo = item.optString("t").takeIf { it.isNotBlank() && it != "null" }
                val abstractKo = item.optString("a").takeIf { it.isNotBlank() && it != "null" }
                out[paper.id] = Pair(titleKo, abstractKo)
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "translation parse failed: " + e.message)
            null
        }
    }
}
