package com.example.data

/**
 * 앱이 수집하는 저널 목록과 분야 태깅 규칙.
 *
 * OpenAlex source id는 런타임에 저널명으로 해석해 캐시한다(ISSN 오타로 소스가
 * 통째로 비는 사고를 막기 위함). issn은 Crossref 폴백 경로에서만 쓴다.
 */
data class JournalSource(
    val name: String,
    val issn: String?,
    val fields: Set<Field>
)

object JournalCatalog {

    private val SEMI = setOf(Field.SEMI)
    private val AI = setOf(Field.AI)
    private val COMM = setOf(Field.COMM)
    private val ENERGY = setOf(Field.ENERGY)

    /** 종합지: 저널만으로 분야를 판정할 수 없어 토픽·키워드 태깅에 맡긴다. */
    private val GENERAL = emptySet<Field>()

    val ALL: List<JournalSource> = listOf(
        JournalSource("Nature", "0028-0836", GENERAL),
        JournalSource("Science", "0036-8075", GENERAL),
        JournalSource("Science Advances", "2375-2548", GENERAL),
        JournalSource("Nature Communications", "2041-1723", GENERAL),

        JournalSource("Nature Materials", "1476-1122", SEMI),
        JournalSource("Nature Electronics", "2520-1131", SEMI),
        JournalSource("Nature Nanotechnology", "1748-3387", SEMI),
        JournalSource("Advanced Materials", "1521-4095", SEMI),
        JournalSource("Advanced Functional Materials", "1616-301X", SEMI),
        JournalSource("IEEE Electron Device Letters", "0741-3106", SEMI),
        JournalSource("IEEE Transactions on Electron Devices", "0018-9383", SEMI),

        JournalSource("Nature Machine Intelligence", "2522-5839", AI),
        JournalSource("Nature Computational Science", "2662-8457", AI),
        JournalSource("IEEE Transactions on Pattern Analysis and Machine Intelligence", "0162-8828", AI),

        JournalSource("IEEE Journal of Solid-State Circuits", "0018-9200", COMM),
        JournalSource("IEEE Transactions on Communications", "0090-6778", COMM),
        JournalSource("IEEE Transactions on Signal Processing", "1053-587X", COMM),

        JournalSource("Nature Energy", "2058-7546", ENERGY),
        JournalSource("Nature Photonics", "1749-4885", ENERGY),
        JournalSource("Joule", "2542-4351", ENERGY),
        JournalSource("Advanced Energy Materials", "1614-6832", ENERGY),
        JournalSource("Light: Science & Applications", "2047-7538", ENERGY),

        JournalSource("ACS Nano", "1936-0851", SEMI),
        JournalSource("Nano Letters", "1530-6984", SEMI),
        JournalSource("Small", "1613-6810", SEMI),
        JournalSource("Nature Reviews Materials", "2058-8437", SEMI),
        JournalSource("npj 2D Materials and Applications", "2397-7132", SEMI),

        JournalSource("IEEE Transactions on Neural Networks and Learning Systems", "2162-237X", AI),
        JournalSource("Nature Reviews Electrical Engineering", null, AI),

        JournalSource("IEEE Transactions on Circuits and Systems II: Express Briefs", "1549-7747", COMM),
        JournalSource("IEEE Transactions on Microwave Theory and Techniques", "0018-9480", COMM),
        JournalSource("IEEE Wireless Communications Letters", "2162-2337", COMM),

        JournalSource("ACS Energy Letters", "2380-8195", ENERGY),
        JournalSource("Optica", "2334-2536", ENERGY),
        JournalSource("Advanced Optical Materials", "2195-1071", ENERGY),
        JournalSource("Energy & Environmental Science", "1754-5692", ENERGY),

        JournalSource("Physical Review Letters", "0031-9007", GENERAL),
        JournalSource("Proceedings of the National Academy of Sciences", "0027-8424", GENERAL),
        JournalSource("Nature Reviews Physics", "2522-5820", GENERAL),
        JournalSource("Cell Reports Physical Science", "2666-3864", GENERAL)
    )

    fun byName(name: String?): JournalSource? {
        if (name.isNullOrBlank()) return null
        val n = name.trim().lowercase()
        return ALL.firstOrNull { it.name.lowercase() == n }
            ?: ALL.firstOrNull { n.startsWith(it.name.lowercase()) }
    }
}

/**
 * 분야 태깅. 1순위는 저널 매핑(전문지는 저널이 곧 분야),
 * 2순위는 OpenAlex 토픽 + 제목/초록 키워드 규칙.
 */
object FieldTagger {

    private val SEMI_KEYS = listOf(
        "transistor", "mosfet", "finfet", "semiconductor", "wafer", "cmos",
        "lithograph", "thin film", "2d material", "graphene", "mos2",
        "perovskite", "dielectric", "nanowire", "quantum dot", "memristor",
        "ferroelectric", "epitax", "doping", "photodetector", "heterostructure",
        "nanocrystal", "polymer", "alloy", "metal-organic framework"
    )

    private val AI_KEYS = listOf(
        "neural network", "deep learning", "machine learning", "transformer",
        "language model", "reinforcement learning", "generative model",
        "diffusion model", "computer vision", "segmentation", "classifier",
        "attention mechanism", "embedding", "benchmark", "inference",
        "foundation model", "self-supervised", "graph neural"
    )

    private val COMM_KEYS = listOf(
        "wireless", "antenna", "mimo", "modulation", "channel estimation",
        "ofdm", "5g", "6g", "signal processing", "analog-to-digital",
        "amplifier", "transceiver", "phase-locked", "oscillator",
        "beamforming", "error-correcting", "sram", "integrated circuit",
        "data converter", "rf front-end"
    )

    private val ENERGY_KEYS = listOf(
        "battery", "lithium", "sodium-ion", "solar cell", "photovoltaic",
        "electrolyte", "anode", "cathode", "fuel cell", "hydrogen",
        "energy storage", "supercapacitor", "laser", "photonic", "optical",
        "waveguide", "luminescen", "light-emitting", "optoelectronic",
        "thermoelectric", "electrocatal"
    )

    fun tag(
        journal: String?,
        title: String,
        abstract: String?,
        topics: List<String>
    ): Set<Field> {
        JournalCatalog.byName(journal)?.fields
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val hay = buildString {
            append(title.lowercase()).append(' ')
            abstract?.let { append(it.lowercase()).append(' ') }
            topics.forEach { append(it.lowercase()).append(' ') }
        }

        val hits = LinkedHashSet<Field>()
        if (SEMI_KEYS.any { hay.contains(it) }) hits.add(Field.SEMI)
        if (AI_KEYS.any { hay.contains(it) }) hits.add(Field.AI)
        if (COMM_KEYS.any { hay.contains(it) }) hits.add(Field.COMM)
        if (ENERGY_KEYS.any { hay.contains(it) }) hits.add(Field.ENERGY)

        return if (hits.isEmpty()) setOf(Field.OTHER) else hits
    }
}
