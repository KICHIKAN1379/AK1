package com.kichikan.ak1.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingRulesTest {
    private fun book(id: String, pages: Int, at: Long = 1L) = ReadingBook(
        id = id,
        title = id,
        dailyMinutes = 20,
        pages = pages,
        answers = 3,
        completedAtEpochMillis = at
    )

    @Test
    fun levelStartsAtOneAndOpensEverySixHundredPages() {
        assertEquals(1, ReadingRules.levelForPages(0))
        assertEquals(1, ReadingRules.levelForPages(599))
        assertEquals(2, ReadingRules.levelForPages(600))
        assertEquals(3, ReadingRules.levelForPages(1200))
    }

    @Test
    fun levelOnePaysOnePointPerHundredPages() {
        assertEquals(1.0, ReadingRules.pointsForPages(100, 1, 0), 0.001)
        assertEquals(6.0, ReadingRules.pointsForPages(600, 1, 0), 0.001)
    }

    @Test
    fun crossingALevelBoundarySplitsTheBookAtTheOldAndNewRates() {
        // 600 pages at level 1 (1.0/100) + 100 pages at level 2 (1.5/100)
        assertEquals(7.5, ReadingRules.pointsForPages(700, 1, 0), 0.001)
        // Starting at 500 pages: 100 at level 1 + 100 at level 2
        assertEquals(2.5, ReadingRules.pointsForPages(200, 1, 500), 0.001)
    }

    @Test
    fun thirdLevelPaysTwoPointsPerHundredPages() {
        assertEquals(2.0, ReadingRules.rateForLevel(3), 0.001)
        assertEquals(2.0, ReadingRules.pointsForPages(100, 3, 1200), 0.001)
    }

    @Test
    fun recalculationRecordsBookLevelsAndTotals() {
        val result = ReadingRules.recalculate(
            listOf(
                book("a", 500, 1),
                book("b", 200, 2)
            )
        )
        assertEquals(700, result.progress.totalPages)
        assertEquals(2, result.progress.level)
        assertEquals(100, result.progress.pagesInLevel)
        assertEquals(7.5, result.progress.points, 0.001)
        assertEquals(1, result.books[0].levelBefore)
        assertEquals(1, result.books[0].levelAfter)
        assertEquals(1, result.books[1].levelBefore)
        assertEquals(2, result.books[1].levelAfter)
    }

    @Test
    fun editingPagesRecalculatesTheWholeHistory() {
        val first = ReadingRules.recalculate(listOf(book("a", 600)))
        assertEquals(2, first.progress.level)
        assertEquals(6.0, first.progress.points, 0.001)

        val edited = ReadingRules.recalculate(listOf(book("a", 1200)))
        assertEquals(3, edited.progress.level)
        assertEquals(15.0, edited.progress.points, 0.001)
    }
}
