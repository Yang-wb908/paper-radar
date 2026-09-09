package com.example.data

enum class Field(val labelKo: String) {
    SEMI("반도체·소자·재료"),
    AI("AI·머신러닝·컴퓨팅"),
    COMM("통신·신호처리·회로"),
    ENERGY("에너지·배터리·광학"),
    BIO("생명·바이오·의공학"),
    OTHER("기타");

    companion object {
        fun fromLabel(label: String): Field? = values().firstOrNull { it.labelKo == label }
    }
}

data class Paper(
    val id: String,              // DOI
    val title: String,           // 원제(영문)
    val authorsLine: String,     // "J. Kim 외 7인"
    val journal: String,         // "Nature Materials"
    val publishedDate: Long,     // epoch millis
    val abstractText: String?,   // 원문 초록, null 가능
    val url: String,             // https://doi.org/...
    val fields: Set<Field>,
    val isBookmarked: Boolean,
    val isRead: Boolean,
    val titleKo: String? = null,     // Gemini 번역 제목
    val abstractKo: String? = null   // Gemini 번역 초록
) {
    /** 카드에서 크게 읽히는 제목. 번역이 있으면 한국어를 앞세운다. */
    val displayTitle: String
        get() = titleKo ?: title

    /** 번역이 있을 때만 원제를 보조 줄로 노출한다. */
    val originalTitleOrNull: String?
        get() = if (titleKo != null) title else null

    val displayAbstract: String?
        get() = abstractKo ?: abstractText

    val isTranslated: Boolean
        get() = titleKo != null

    /** arXiv·ChemRxiv·bioRxiv처럼 심사 전 원고인지. 홈에서 끄고 켤 수 있다. */
    val isPreprint: Boolean
        get() = journal.startsWith("arXiv") || journal == "ChemRxiv" || journal == "bioRxiv"

    /** 번역 대상인지. 초록이 없어도 제목은 번역한다. */
    val needsTranslation: Boolean
        get() = titleKo == null || (abstractText != null && abstractKo == null)
}
