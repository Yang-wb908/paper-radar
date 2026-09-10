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
    val id: String,            // DOI 또는 arxiv:<id>
    val title: String,
    val authorsLine: String,   // "J. Kim 외 7인"
    val journal: String,       // "Nature Materials"
    val publishedDate: Long,   // epoch millis
    val abstractText: String?, // 초록, null 가능
    val url: String,           // https://doi.org/...
    val fields: Set<Field>,
    val isBookmarked: Boolean,
    val isRead: Boolean
) {
    /** 카드·상세에서 크게 읽히는 제목. */
    val displayTitle: String
        get() = title

    /** 카드 발췌·상세 본문에 쓰는 초록. */
    val displayAbstract: String?
        get() = abstractText

    /** arXiv·bioRxiv처럼 심사 전 원고인지. 홈에서 끄고 켤 수 있다. */
    val isPreprint: Boolean
        get() = journal.startsWith("arXiv") || journal == "bioRxiv"
}

/** 카드·상세가 같이 쓰는 상대 날짜 표기. 발행일이 없거나 미래여도 안전하게 접는다. */
fun relativeDayLabel(publishedDate: Long, now: Long = System.currentTimeMillis()): String {
    if (publishedDate <= 0L) return "날짜 미상"
    val days = ((now - publishedDate) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "오늘"
        days == 1 -> "어제"
        days < 30 -> days.toString() + "일 전"
        days < 365 -> (days / 30).toString() + "개월 전"
        else -> (days / 365).toString() + "년 전"
    }
}
