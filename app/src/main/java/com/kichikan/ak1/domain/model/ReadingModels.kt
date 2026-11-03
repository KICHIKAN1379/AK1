package com.kichikan.ak1.domain.model

import kotlin.math.min
import kotlin.math.round

/**
 * One finished-book record for a member.
 *
 * Reading is deliberately separate from the AK1 XP ladder: every member has their own
 * reading level, page total and reading score. The level is derived from the pages, so
 * editing a record can never leave the level and the page total out of sync.
 */
data class ReadingBook(
    val id: String,
    val title: String,
    /** Average minutes the member read this book per day. */
    val dailyMinutes: Int,
    val pages: Int,
    /** Number of answer sessions completed for the book (1..3). */
    val answers: Int,
    val completedAtEpochMillis: Long,
    val levelBefore: Int = 1,
    val levelAfter: Int = 1,
    val points: Double = 0.0
)

data class ReadingProgress(
    val totalPages: Int = 0,
    val level: Int = 1,
    val points: Double = 0.0,
    val pagesInLevel: Int = 0,
    val pagesPerLevel: Int = ReadingRules.PAGES_PER_LEVEL
) {
    val progress: Float
        get() = (pagesInLevel.toFloat() / pagesPerLevel.coerceAtLeast(1)).coerceIn(0f, 1f)

    val pagesToNextLevel: Int
        get() = (pagesPerLevel - pagesInLevel).coerceAtLeast(0)
}

data class ReadingCalculation(
    val books: List<ReadingBook>,
    val progress: ReadingProgress
)

/**
 * AK1 reading ladder:
 *  - each 600 pages opens one reading level;
 *  - level 1 pays 1.0 point per 100 pages;
 *  - level 2 pays 1.5, level 3 pays 2.0, and so on (+0.5 per level).
 *
 * Points are calculated page-by-page in 100-page rate steps. When one book crosses a
 * 600-page level boundary, the part before the boundary is paid at the old level and the
 * part after it at the new level.
 */
object ReadingRules {
    const val PAGES_PER_LEVEL = 600
    const val PAGES_PER_RATE_STEP = 100

    fun levelForPages(totalPages: Int): Int =
        1 + totalPages.coerceAtLeast(0) / PAGES_PER_LEVEL

    /** Points paid for 100 pages at [level]. */
    fun rateForLevel(level: Int): Double =
        0.5 + level.coerceAtLeast(1) * 0.5

    fun pointsForPages(pages: Int, startLevel: Int, startTotalPages: Int): Double {
        if (pages <= 0) return 0.0
        var remaining = pages
        var level = startLevel.coerceAtLeast(1)
        var consumed = startTotalPages.coerceAtLeast(0)
        var points = 0.0
        while (remaining > 0) {
            val insideLevel = consumed % PAGES_PER_LEVEL
            val take = min(remaining, PAGES_PER_LEVEL - insideLevel)
            points += take.toDouble() / PAGES_PER_RATE_STEP * rateForLevel(level)
            remaining -= take
            consumed += take
            level = levelForPages(consumed)
        }
        return round2(points)
    }

    fun recalculate(books: List<ReadingBook>): ReadingCalculation {
        var totalPages = 0
        var points = 0.0
        val updated = books.sortedBy { it.completedAtEpochMillis }.map { book ->
            val levelBefore = levelForPages(totalPages)
            val bookPoints = pointsForPages(book.pages, levelBefore, totalPages)
            totalPages += book.pages
            points += bookPoints
            book.copy(
                levelBefore = levelBefore,
                levelAfter = levelForPages(totalPages),
                points = bookPoints
            )
        }
        return ReadingCalculation(
            books = updated,
            progress = ReadingProgress(
                totalPages = totalPages,
                level = levelForPages(totalPages),
                points = round2(points),
                pagesInLevel = totalPages % PAGES_PER_LEVEL
            )
        )
    }

    fun progressFor(books: List<ReadingBook>): ReadingProgress =
        recalculate(books).progress

    private fun round2(value: Double): Double = round(value * 100.0) / 100.0
}

/** Persian formatting for a reading score that may contain a half point. */
fun formatReadingPoints(value: Double): String {
    val rounded = round(value * 100.0) / 100.0
    val whole = rounded.toInt()
    val fraction = ((rounded - whole) * 100.0).toInt()
    val wholeText = whole.toString().map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")
    return when {
        fraction == 0 -> wholeText
        fraction % 10 == 0 -> "$wholeText٫${fraction / 10}"
        else -> "$wholeText٫${fraction.toString().padStart(2, '0')}"
    }
}
