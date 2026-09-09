package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakePaperRepository : PaperRepository {

    private val dummyPapers = listOf(
        Paper(
            id = "10.1038/s41563-024-01",
            title = "High-efficiency perovskite-silicon tandem solar cells with robust stability",
            authorsLine = "J. Kim 외 7인",
            journal = "Nature Materials",
            publishedDate = System.currentTimeMillis() - 86400000L * 1, // 1일 전
            abstractText = "We report a monolithic perovskite-silicon tandem solar cell exceeding 33% efficiency. The design incorporates a novel passivation layer that significantly reduces non-radiative recombination at the interface...",
            url = "https://doi.org/10.1038/s41563-024-01",
            fields = setOf(Field.SEMI, Field.ENERGY),
            isBookmarked = false,
            isRead = false
        ),
        Paper(
            id = "10.1126/science.adi2024",
            title = "Scaling laws for large language models in scientific reasoning",
            authorsLine = "A. Smith 외 3인",
            journal = "Science",
            publishedDate = System.currentTimeMillis() - 86400000L * 2, // 2일 전
            abstractText = "We establish rigorous scaling laws for large language models applied to complex scientific reasoning tasks, demonstrating predictability in capability emergence across multiple disciplines...",
            url = "https://doi.org/10.1126/science.adi2024",
            fields = setOf(Field.AI),
            isBookmarked = true,
            isRead = true
        ),
        Paper(
            id = "10.1109/JSSC.2024.1",
            title = "A 50-Gb/s PAM4 Receiver with Adaptive Equalization in 3nm FinFET",
            authorsLine = "C. Lee 외 5인",
            journal = "IEEE Journal of Solid-State Circuits",
            publishedDate = System.currentTimeMillis() - 86400000L * 3, // 3일 전
            abstractText = null, // IEEE 논문 - 초록 미제공
            url = "https://doi.org/10.1109/JSSC.2024.1",
            fields = setOf(Field.SEMI, Field.COMM),
            isBookmarked = false,
            isRead = false
        ),
        Paper(
            id = "10.1038/s42256-024-00",
            title = "Neural algorithmic reasoning for accelerating materials discovery",
            authorsLine = "D. Park 외 4인",
            journal = "Nature Machine Intelligence",
            publishedDate = System.currentTimeMillis() - 86400000L * 4, // 4일 전
            abstractText = "Discovering new stable materials is computationally expensive. Here, we present a neural algorithmic framework that reduces density functional theory (DFT) calculation times by two orders of magnitude...",
            url = "https://doi.org/10.1038/s42256-024-00",
            fields = setOf(Field.AI, Field.SEMI),
            isBookmarked = false,
            isRead = true
        ),
        Paper(
            id = "10.1002/adma.2024001",
            title = "Flexible and scalable all-solid-state microbatteries for wearable electronics",
            authorsLine = "E. Chen 외 6인",
            journal = "Advanced Materials",
            publishedDate = System.currentTimeMillis() - 86400000L * 5,
            abstractText = "We demonstrate a novel fabrication method for flexible all-solid-state microbatteries with high areal energy density. The devices maintain stable performance even under extreme bending cycles...",
            url = "https://doi.org/10.1002/adma.2024001",
            fields = setOf(Field.ENERGY, Field.SEMI),
            isBookmarked = false,
            isRead = false
        ),
        Paper(
            id = "10.1109/TCOMM.2024.2",
            title = "Deep Learning-based Channel Estimation for 6G Massive MIMO Systems",
            authorsLine = "F. Zhang 외 3인",
            journal = "IEEE Transactions on Communications",
            publishedDate = System.currentTimeMillis() - 86400000L * 6,
            abstractText = null,
            url = "https://doi.org/10.1109/TCOMM.2024.2",
            fields = setOf(Field.COMM, Field.AI),
            isBookmarked = true,
            isRead = false
        ),
        Paper(
            id = "10.1038/s41586-024-00",
            title = "Quantum error correction beyond the break-even point",
            authorsLine = "G. Quantum 외 12인",
            journal = "Nature",
            publishedDate = System.currentTimeMillis() - 86400000L * 7,
            abstractText = "Realizing fault-tolerant quantum computing requires quantum error correction that extends the logical qubit lifetime beyond that of the constituent physical qubits. We report the experimental demonstration of...",
            url = "https://doi.org/10.1038/s41586-024-00",
            fields = setOf(Field.OTHER),
            isBookmarked = false,
            isRead = false
        ),
        Paper(
            id = "10.1016/j.joule.2024.01",
            title = "Direct air capture of CO2 using engineered metal-organic frameworks",
            authorsLine = "H. Green 외 5인",
            journal = "Joule",
            publishedDate = System.currentTimeMillis() - 86400000L * 8,
            abstractText = "Direct air capture is essential for achieving net-zero emissions. We present a scalable synthesis of a new metal-organic framework with unprecedented selectivity and capacity for CO2 under ambient conditions...",
            url = "https://doi.org/10.1016/j.joule.2024.01",
            fields = setOf(Field.ENERGY),
            isBookmarked = false,
            isRead = true
        ),
        Paper(
            id = "10.1126/sciadv.2024.1",
            title = "Neuromorphic vision sensors for high-speed object tracking",
            authorsLine = "I. Vision 외 4인",
            journal = "Science Advances",
            publishedDate = System.currentTimeMillis() - 86400000L * 9,
            abstractText = "Event-based neuromorphic sensors offer microsecond temporal resolution. We integrate a novel on-chip processing architecture that enables real-time, low-power object tracking at over 10,000 frames per second equivalent...",
            url = "https://doi.org/10.1126/sciadv.2024.1",
            fields = setOf(Field.SEMI, Field.AI),
            isBookmarked = false,
            isRead = false
        ),
        Paper(
            id = "10.1109/TPAMI.2024.1",
            title = "Foundation Models for Multi-modal Action Recognition in Video",
            authorsLine = "J. Vision 외 6인",
            journal = "IEEE Transactions on Pattern Analysis and Machine Intelligence",
            publishedDate = System.currentTimeMillis() - 86400000L * 10,
            abstractText = null,
            url = "https://doi.org/10.1109/TPAMI.2024.1",
            fields = setOf(Field.AI),
            isBookmarked = false,
            isRead = false
        ),
        Paper(
            id = "10.1038/s41566-024-00",
            title = "Integrated lithium niobate photonics for ultra-broadband modulation",
            authorsLine = "K. Light 외 4인",
            journal = "Nature Photonics",
            publishedDate = System.currentTimeMillis() - 86400000L * 11,
            abstractText = "Thin-film lithium niobate is emerging as a premier platform for integrated photonics. Here we demonstrate electro-optic modulators with bandwidths exceeding 100 GHz, enabling beyond 1-Tb/s optical communication links...",
            url = "https://doi.org/10.1038/s41566-024-00",
            fields = setOf(Field.SEMI, Field.COMM),
            isBookmarked = true,
            isRead = true
        ),
        Paper(
            id = "10.1002/aenm.2024002",
            title = "Solid-electrolyte interphase stabilization in lithium-metal batteries",
            authorsLine = "L. Battery 외 7인",
            journal = "Advanced Energy Materials",
            publishedDate = System.currentTimeMillis() - 86400000L * 12,
            abstractText = "The degradation of the solid-electrolyte interphase (SEI) limits the cycle life of lithium-metal batteries. By designing a dual-salt electrolyte with localized high concentration, we achieve a highly stable, fluorine-rich SEI...",
            url = "https://doi.org/10.1002/aenm.2024002",
            fields = setOf(Field.ENERGY, Field.SEMI),
            isBookmarked = false,
            isRead = false
        ),
        Paper(
            id = "10.1038/s41928-024-00",
            title = "In-memory computing using memristor crossbar arrays for edge AI",
            authorsLine = "M. Memory 외 5인",
            journal = "Nature Electronics",
            publishedDate = System.currentTimeMillis() - 86400000L * 12,
            abstractText = "Moving data between memory and processing units is a major energy bottleneck in AI hardware. We showcase a fully integrated memristor-based analog in-memory computing macro that accelerates deep neural network inference...",
            url = "https://doi.org/10.1038/s41928-024-00",
            fields = setOf(Field.SEMI, Field.AI),
            isBookmarked = false,
            isRead = false
        ),
        Paper(
            id = "10.1038/s41467-024-00",
            title = "Self-assembling supramolecular polymers for targeted drug delivery",
            authorsLine = "N. Bio 외 8인",
            journal = "Nature Communications",
            publishedDate = System.currentTimeMillis() - 86400000L * 13,
            abstractText = "Targeted delivery of therapeutics remains a challenge. We engineer a class of sequence-defined supramolecular polymers that self-assemble into nanostructures with precise control over morphology and targeting ligand display...",
            url = "https://doi.org/10.1038/s41467-024-00",
            fields = setOf(Field.OTHER),
            isBookmarked = false,
            isRead = true
        ),
        Paper(
            id = "10.1126/science.adi2025",
            title = "Observation of a novel fractional quantum Hall state in graphene",
            authorsLine = "O. Physics 외 4인",
            journal = "Science",
            publishedDate = System.currentTimeMillis() - 86400000L * 14,
            abstractText = "Electron-electron interactions in flat bands lead to exotic correlated phases. Using highly sensitive capacitance measurements on magic-angle twisted bilayer graphene, we observe a robust fractional quantum Hall state at a previously unexpected filling factor...",
            url = "https://doi.org/10.1126/science.adi2025",
            fields = setOf(Field.SEMI, Field.OTHER),
            isBookmarked = true,
            isRead = false
        )
    )

    private val papersState = MutableStateFlow(dummyPapers)

    override fun getPapers(): Flow<List<Paper>> = papersState

    override fun getBookmarkedPapers(): Flow<List<Paper>> = papersState.map { papers ->
        papers.filter { it.isBookmarked }
    }

    override fun getPaperById(id: String): Flow<Paper?> = papersState.map { papers ->
        papers.find { it.id == id }
    }

    override suspend fun toggleBookmark(id: String) {
        papersState.update { papers ->
            papers.map {
                if (it.id == id) it.copy(isBookmarked = !it.isBookmarked) else it
            }
        }
    }
}
