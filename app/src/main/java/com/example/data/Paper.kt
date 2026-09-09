package com.example.data

enum class Field(val labelKo: String) {
    SEMI("반도체·소자·재료"),
    AI("AI·머신러닝·컴퓨팅"),
    COMM("통신·신호처리·회로"),
    ENERGY("에너지·배터리·광학"),
    OTHER("기타")
}

data class Paper(
    val id: String,              // DOI
    val title: String,
    val authorsLine: String,     // "J. Kim 외 7인"
    val journal: String,         // "Nature Materials"
    val publishedDate: Long,     // epoch millis
    val abstractText: String?,   // null 가능
    val url: String,             // https://doi.org/...
    val fields: Set<Field>,
    val isBookmarked: Boolean,
    val isRead: Boolean
)
