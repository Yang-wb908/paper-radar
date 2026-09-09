package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

internal const val TAG = "PaperRadar"

/** OpenAlex/Crossref polite pool 진입용 연락처. UA와 mailto 파라미터 양쪽에 넣는다. */
private const val CONTACT = "jumpboy85@gmail.com"
private const val USER_AGENT = "PaperRadar/1.0 (Android; mailto:jumpboy85@gmail.com)"

internal object Http {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getJson(url: String): JSONObject? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP " + response.code + " <- " + url)
                    return null
                }
                val body = response.body?.string() ?: return null
                JSONObject(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "request failed: " + e.message)
            null
        }
    }

    fun postJson(url: String, payload: JSONObject): JSONObject? {
        return try {
            val media = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(media))
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP " + response.code + " <- " + url.substringBefore("?key="))
                    return null
                }
                if (body == null) return null
                JSONObject(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "post failed: " + e.message)
            null
        }
    }

    /** arXiv는 Atom XML이라 원문 텍스트가 필요하다. */
    fun getText(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP " + response.code + " <- " + url)
                    return null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            Log.w(TAG, "text request failed: " + e.message)
            null
        }
    }
}

internal fun isoDay(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date(millis))
}

internal fun parseIsoDay(text: String?): Long {
    if (text.isNullOrBlank()) return 0L
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        fmt.parse(text)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}

/** OpenAlex는 저작권 때문에 초록을 역색인으로 준다. 위치 순으로 되돌린다. */
internal fun invertedIndexToText(index: JSONObject?): String? {
    if (index == null || index.length() == 0) return null
    val slots = sortedMapOf<Int, String>()
    val keys = index.keys()
    while (keys.hasNext()) {
        val word = keys.next()
        val positions = index.optJSONArray(word) ?: continue
        for (i in 0 until positions.length()) {
            slots[positions.optInt(i)] = word
        }
    }
    val text = slots.values.joinToString(" ").trim()
    return text.ifBlank { null }
}

/** Crossref 초록은 JATS XML로 들어온다. 태그를 걷어낸다. */
internal fun stripMarkup(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val text = raw
        .replace(Regex("<[^>]+>"), " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace(Regex("(?i)^\\s*abstract\\s*"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    return text.ifBlank { null }
}

internal fun normalizeDoi(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return raw.trim()
        .removePrefix("https://doi.org/")
        .removePrefix("http://doi.org/")
        .removePrefix("doi:")
        .lowercase()
        .ifBlank { null }
}

internal fun authorsLine(names: List<String>): String {
    if (names.isEmpty()) return "저자 정보 없음"
    val first = names.first()
    return if (names.size == 1) first else first + " 외 " + (names.size - 1) + "인"
}

private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

/**
 * 1차 수집기. 저널 source id를 OR로 묶어 한 번에 질의하고,
 * 메타데이터와 초록(역색인)을 함께 받는다.
 */
object OpenAlexSource {

    private const val BASE = "https://api.openalex.org"
    private val sourceIdCache = mutableMapOf<String, String>()

    suspend fun resolveSourceId(journal: JournalSource): String? = withContext(Dispatchers.IO) {
        sourceIdCache[journal.name]?.let { return@withContext it }

        val url = BASE + "/sources?search=" + enc(journal.name) + "&per-page=5&mailto=" + CONTACT
        val root = Http.getJson(url) ?: return@withContext null
        val results = root.optJSONArray("results") ?: return@withContext null

        var best: String? = null
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val id = item.optString("id").substringAfterLast('/')
            if (id.isBlank()) continue
            val issnL = item.optString("issn_l")
            val displayName = item.optString("display_name")
            if (journal.issn != null && issnL == journal.issn) {
                best = id
                break
            }
            if (best == null && displayName.equals(journal.name, ignoreCase = true)) {
                best = id
            }
        }
        if (best == null && results.length() > 0) {
            val fallback = results.optJSONObject(0)?.optString("id")?.substringAfterLast('/')
            if (!fallback.isNullOrBlank()) best = fallback
        }
        best?.also { sourceIdCache[journal.name] = it }
    }

    suspend fun fetchRecent(
        journals: List<JournalSource>,
        sinceDay: String,
        perPage: Int = 200
    ): List<Paper> = withContext(Dispatchers.IO) {
        val idToJournal = LinkedHashMap<String, JournalSource>()
        for (journal in journals) {
            val id = resolveSourceId(journal)
            if (id != null) idToJournal[id] = journal
        }
        if (idToJournal.isEmpty()) {
            Log.w(TAG, "no OpenAlex source ids resolved")
            return@withContext emptyList()
        }

        val filter = "primary_location.source.id:" + idToJournal.keys.joinToString("|") +
            ",from_publication_date:" + sinceDay
        val select = "id,doi,title,publication_date,primary_location,authorships,topics,abstract_inverted_index"
        val url = BASE + "/works?filter=" + enc(filter) +
            "&sort=publication_date:desc&per-page=" + perPage +
            "&select=" + select + "&mailto=" + CONTACT

        val root = Http.getJson(url) ?: return@withContext emptyList()
        val results = root.optJSONArray("results") ?: return@withContext emptyList()

        val papers = ArrayList<Paper>(results.length())
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            parseWork(item)?.let { papers.add(it) }
        }
        Log.i(TAG, "OpenAlex returned " + papers.size + " papers since " + sinceDay)
        papers
    }

    private fun parseWork(item: JSONObject): Paper? {
        val doi = normalizeDoi(item.optString("doi")) ?: return null
        val title = item.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: return null

        val journal = item.optJSONObject("primary_location")
            ?.optJSONObject("source")
            ?.optString("display_name")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: "Unknown"

        val names = ArrayList<String>()
        item.optJSONArray("authorships")?.let { arr ->
            for (i in 0 until arr.length()) {
                val name = arr.optJSONObject(i)?.optJSONObject("author")?.optString("display_name")
                if (!name.isNullOrBlank() && name != "null") names.add(name)
            }
        }

        val topics = ArrayList<String>()
        item.optJSONArray("topics")?.let { arr ->
            for (i in 0 until arr.length()) {
                val name = arr.optJSONObject(i)?.optString("display_name")
                if (!name.isNullOrBlank() && name != "null") topics.add(name)
            }
        }

        val abstract = invertedIndexToText(item.optJSONObject("abstract_inverted_index"))

        return Paper(
            id = doi,
            title = title,
            authorsLine = authorsLine(names),
            journal = journal,
            publishedDate = parseIsoDay(item.optString("publication_date")),
            abstractText = abstract,
            url = "https://doi.org/" + doi,
            fields = FieldTagger.tag(journal, title, abstract, topics),
            isBookmarked = false,
            isRead = false
        )
    }
}

/**
 * 2차 소스. 초록 보강(Wiley·AAAS 계열은 여기서 채워진다)과
 * OpenAlex가 막혔을 때의 단독 수집 경로를 모두 담당한다.
 */
object CrossrefSource {

    private const val BASE = "https://api.crossref.org"

    suspend fun fetchAbstract(doi: String): String? = withContext(Dispatchers.IO) {
        val url = BASE + "/works/" + doi + "?mailto=" + CONTACT
        val message = Http.getJson(url)?.optJSONObject("message") ?: return@withContext null
        if (message.isNull("abstract")) return@withContext null
        stripMarkup(message.optString("abstract"))
    }

    suspend fun fetchJournalWorks(
        journal: JournalSource,
        sinceDay: String,
        rows: Int = 40
    ): List<Paper> = withContext(Dispatchers.IO) {
        val issn = journal.issn ?: return@withContext emptyList()
        val select = "DOI,title,abstract,published,container-title,URL,author"
        val url = BASE + "/journals/" + issn + "/works?filter=from-pub-date:" + sinceDay +
            "&sort=published&order=desc&rows=" + rows +
            "&select=" + enc(select) + "&mailto=" + CONTACT

        val items = Http.getJson(url)?.optJSONObject("message")?.optJSONArray("items")
            ?: return@withContext emptyList()

        val papers = ArrayList<Paper>(items.length())
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            parseItem(item, journal)?.let { papers.add(it) }
        }
        papers
    }

    private fun parseItem(item: JSONObject, journal: JournalSource): Paper? {
        val doi = normalizeDoi(item.optString("DOI")) ?: return null
        val title = firstOf(item.optJSONArray("title")) ?: return null
        val container = firstOf(item.optJSONArray("container-title")) ?: journal.name
        val abstract = if (item.isNull("abstract")) null else stripMarkup(item.optString("abstract"))

        val names = ArrayList<String>()
        item.optJSONArray("author")?.let { arr ->
            for (i in 0 until arr.length()) {
                val author = arr.optJSONObject(i) ?: continue
                val given = author.optString("given")
                val family = author.optString("family")
                val name = (given + " " + family).trim()
                if (name.isNotBlank()) names.add(name)
            }
        }

        val url = item.optString("URL").takeIf { it.isNotBlank() && it != "null" }
            ?: ("https://doi.org/" + doi)

        return Paper(
            id = doi,
            title = title,
            authorsLine = authorsLine(names),
            journal = container,
            publishedDate = parseDateParts(item.optJSONObject("published")),
            abstractText = abstract,
            url = url,
            fields = FieldTagger.tag(container, title, abstract, emptyList()),
            isBookmarked = false,
            isRead = false
        )
    }

    private fun firstOf(array: JSONArray?): String? {
        if (array == null || array.length() == 0) return null
        return array.optString(0).takeIf { it.isNotBlank() && it != "null" }
    }

    private fun parseDateParts(published: JSONObject?): Long {
        val parts = published?.optJSONArray("date-parts")?.optJSONArray(0) ?: return 0L
        if (parts.length() == 0) return 0L
        val year = parts.optInt(0, 0)
        if (year == 0) return 0L
        val month = if (parts.length() > 1) parts.optInt(1, 1) else 1
        val day = if (parts.length() > 2) parts.optInt(2, 1) else 1
        val text = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
        return parseIsoDay(text)
    }
}
