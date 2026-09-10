package com.example.data

import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** arXiv 카테고리 하나. 코드가 곧 질의 키이고, 분야 태깅은 이 매핑을 그대로 쓴다. */
data class ArxivCategory(
    val code: String,
    val labelKo: String,
    val fields: Set<Field>
)

object ArxivCatalog {

    val ALL: List<ArxivCategory> = listOf(
        ArxivCategory("cs.LG", "머신러닝", setOf(Field.AI)),
        ArxivCategory("cs.CV", "컴퓨터비전", setOf(Field.AI)),
        ArxivCategory("cs.CL", "자연어처리", setOf(Field.AI)),
        ArxivCategory("cs.AI", "인공지능", setOf(Field.AI)),
        ArxivCategory("cs.AR", "컴퓨터구조", setOf(Field.AI, Field.COMM)),

        ArxivCategory("cond-mat.mtrl-sci", "재료과학", setOf(Field.SEMI)),
        ArxivCategory("cond-mat.mes-hall", "메조스케일·나노", setOf(Field.SEMI)),
        ArxivCategory("physics.app-ph", "응용물리", setOf(Field.SEMI)),

        ArxivCategory("eess.SP", "신호처리", setOf(Field.COMM)),
        ArxivCategory("cs.IT", "정보이론", setOf(Field.COMM)),

        ArxivCategory("physics.optics", "광학", setOf(Field.ENERGY)),
        ArxivCategory("cond-mat.supr-con", "초전도", setOf(Field.ENERGY)),

        ArxivCategory("q-bio.NC", "신경과학", setOf(Field.BIO)),
        ArxivCategory("q-bio.QM", "정량생물학", setOf(Field.BIO)),
        ArxivCategory("q-bio.BM", "생체분자", setOf(Field.BIO))
    )

    fun fieldsFor(codes: List<String>): Set<Field> {
        val out = LinkedHashSet<Field>()
        for (code in codes) {
            ALL.firstOrNull { it.code == code }?.let { out.addAll(it.fields) }
        }
        return out
    }
}

/**
 * arXiv 프리프린트 수집기. 저널 논문보다 훨씬 빨리 올라오고 초록이 항상 있어서,
 * IEEE처럼 초록이 비는 소스를 보완하는 역할도 한다.
 *
 * Atom XML이라 JSON 파서를 못 쓰고 XmlPullParser로 읽는다.
 */
object ArxivSource {

    private const val BASE = "https://export.arxiv.org/api/query"

    suspend fun fetchRecent(
        categories: List<ArxivCategory> = ArxivCatalog.ALL,
        perGroup: Int = 100
    ): List<Paper> = withContext(Dispatchers.IO) {
        if (categories.isEmpty()) return@withContext emptyList()

        // 전체 카테고리를 OR로 묶어 한 번에 받으면 cs.LG·cs.CV가 결과를 다 먹는다.
        // 분야별로 나눠 받아야 광학·생명 쪽도 피드에 올라온다.
        val groups = categories.groupBy { it.fields.firstOrNull() ?: Field.OTHER }
        val out = LinkedHashMap<String, Paper>()
        for ((field, group) in groups) {
            val query = group.joinToString("+OR+") { "cat:" + it.code }
            val url = BASE + "?search_query=" + query +
                "&sortBy=submittedDate&sortOrder=descending&max_results=" + perGroup
            val xml = Http.getText(url)
            if (xml == null) {
                Log.w(TAG, "arXiv group " + field.name + " failed")
                continue
            }
            for (paper in parseFeed(xml)) out[paper.id] = paper
            delay(ARXIV_GAP_MILLIS)
        }
        Log.i(TAG, "arXiv returned " + out.size + " preprints from " + groups.size + " groups")
        out.values.toList()
    }

    /** arXiv API 권장 간격. 연속 요청으로 막히지 않게 그룹 사이에 쉰다. */
    private const val ARXIV_GAP_MILLIS = 1000L

    private fun parseFeed(xml: String): List<Paper> {
        val out = ArrayList<Paper>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var inEntry = false
            var inAuthor = false
            var rawId = ""
            var title = ""
            var summary = ""
            var published = ""
            var link: String? = null
            val authors = ArrayList<String>()
            val categories = ArrayList<String>()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "entry" -> {
                            inEntry = true
                            rawId = ""; title = ""; summary = ""; published = ""; link = null
                            authors.clear(); categories.clear()
                        }
                        "id" -> if (inEntry) rawId = safeText(parser)
                        "title" -> if (inEntry) title = collapse(safeText(parser))
                        "summary" -> if (inEntry) summary = collapse(safeText(parser))
                        "published" -> if (inEntry) published = safeText(parser)
                        "author" -> inAuthor = true
                        "name" -> if (inEntry && inAuthor) authors.add(safeText(parser))
                        "category" -> if (inEntry) {
                            parser.getAttributeValue(null, "term")?.let { categories.add(it) }
                        }
                        "link" -> if (inEntry) {
                            val rel = parser.getAttributeValue(null, "rel")
                            if (rel == "alternate") link = parser.getAttributeValue(null, "href")
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    when (parser.name) {
                        "author" -> inAuthor = false
                        "entry" -> {
                            inEntry = false
                            build(rawId, title, summary, published, link, authors, categories)
                                ?.let { out.add(it) }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "arXiv parse failed: " + e.message)
        }
        return out
    }

    private fun safeText(parser: XmlPullParser): String = try {
        parser.nextText().trim()
    } catch (e: Exception) {
        ""
    }

    private fun collapse(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    private fun build(
        rawId: String,
        title: String,
        summary: String,
        published: String,
        link: String?,
        authors: List<String>,
        categories: List<String>
    ): Paper? {
        if (title.isBlank() || rawId.isBlank()) return null

        // http://arxiv.org/abs/2509.01234v1 -> arxiv:2509.01234
        val bare = rawId.substringAfterLast("/abs/").substringBefore("v")
        if (bare.isBlank()) return null

        val primary = categories.firstOrNull { code -> ArxivCatalog.ALL.any { it.code == code } }
            ?: categories.firstOrNull()
            ?: "arXiv"

        val fields = ArxivCatalog.fieldsFor(categories).ifEmpty {
            FieldTagger.tag(null, title, summary, categories)
        }

        return Paper(
            id = "arxiv:" + bare,
            title = title,
            authorsLine = authorsLine(authors),
            journal = "arXiv · " + primary,
            publishedDate = parseAtomDate(published),
            abstractText = summary.takeIf { it.isNotBlank() },
            url = link ?: ("https://arxiv.org/abs/" + bare),
            fields = fields,
            isBookmarked = false,
            isRead = false
        )
    }

    private fun parseAtomDate(text: String): Long {
        if (text.isBlank()) return 0L
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(text)?.time ?: 0L
        } catch (e: Exception) {
            parseIsoDay(text.take(10))
        }
    }
}

/**
 * bioRxiv 프리프린트. 생명·바이오 분야는 저널보다 여기로 먼저 올라온다.
 * 날짜 구간 API라 최근 이틀치만 받아 건수를 통제한다.
 */
object BioRxivSource {

    private const val BASE = "https://api.biorxiv.org/details/biorxiv/"

    suspend fun fetchRecent(fromDay: String, toDay: String): List<Paper> = withContext(Dispatchers.IO) {
        val url = BASE + fromDay + "/" + toDay + "/0"
        val root = Http.getJson(url) ?: return@withContext emptyList()
        val items = root.optJSONArray("collection") ?: return@withContext emptyList()

        val out = ArrayList<Paper>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            parseItem(item)?.let { out.add(it) }
        }
        Log.i(TAG, "bioRxiv returned " + out.size + " preprints")
        out
    }

    private fun parseItem(item: org.json.JSONObject): Paper? {
        val title = cleanTitle(item.optString("title"))
        val doi = normalizeDoi(item.optString("doi")) ?: return null
        if (title.isBlank()) return null

        val abstract = stripMarkup(if (item.isNull("abstract")) null else item.optString("abstract"))
        val names = item.optString("authors")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val category = item.optString("category").takeIf { it.isNotBlank() }

        val fields = LinkedHashSet<Field>()
        fields.add(Field.BIO)
        fields.addAll(FieldTagger.tag(null, title, abstract, listOfNotNull(category)).filter { it != Field.OTHER })

        return Paper(
            id = doi,
            title = title,
            authorsLine = authorsLine(names),
            journal = "bioRxiv",
            publishedDate = parseIsoDay(item.optString("date").take(10)),
            abstractText = abstract,
            url = "https://doi.org/" + doi,
            fields = fields,
            isBookmarked = false,
            isRead = false
        )
    }
}
