package com.kichikan.ak1.domain.service

import com.kichikan.ak1.domain.calendar.JalaliCalendar
import com.kichikan.ak1.domain.calendar.JalaliDate
import com.kichikan.ak1.domain.model.HistoryEvent
import com.kichikan.ak1.domain.model.HistoryType
import com.kichikan.ak1.domain.model.Member
import java.time.ZoneId

/**
 * Ring report (کارنامه). Every number is relative (per member, or a percentage), so a ring of 5 and a ring
 * of 50 can be compared fairly. Periods use the Jalali calendar and are half-open ranges [start, end).
 */
object RingReport {
    enum class Kind { MONTHLY, YEARLY }

    data class Range(val start: Long, val end: Long)

    /** [isPercent] metrics are percentages; the others are averages per member. */
    data class Metric(val label: String, val value: Double, val previous: Double, val isPercent: Boolean, val isWhole: Boolean = false)

    data class Result(
        val title: String,
        val metrics: List<Metric>,
        /** Bar/line series: label and XP per member for each bucket of the period. */
        val series: List<Pair<String, Double>>
    )

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private const val DAY = 86_400_000L

    private fun startOf(year: Int, month: Int): Long =
        JalaliCalendar.toGregorian(JalaliDate(year, month, 1)).atStartOfDay(zone).toInstant().toEpochMilli()

    fun range(kind: Kind, year: Int, month: Int): Range = when (kind) {
        Kind.MONTHLY -> Range(startOf(year, month), if (month == 12) startOf(year + 1, 1) else startOf(year, month + 1))
        Kind.YEARLY -> Range(startOf(year, 1), startOf(year + 1, 1))
    }

    fun previousRange(kind: Kind, year: Int, month: Int): Range = when (kind) {
        Kind.MONTHLY -> if (month == 1) range(kind, year - 1, 12) else range(kind, year, month - 1)
        Kind.YEARLY -> range(kind, year - 1, 1)
    }

    fun title(kind: Kind, year: Int, month: Int): String = when (kind) {
        Kind.MONTHLY -> "کارنامه ${JalaliCalendar.MONTH_NAMES[month - 1]} $year"
        Kind.YEARLY -> "کارنامه سال $year"
    }

    /** A Jalali year plus any set of its months (consecutive or not); all 12 months is the whole year. */
    data class Selection(val year: Int, val months: Set<Int>) {
        val sorted: List<Int> get() = months.filter { it in 1..12 }.sorted()
        val isWholeYear: Boolean get() = sorted.size == 12
    }

    fun ranges(sel: Selection): List<Range> = sel.sorted.map { range(Kind.MONTHLY, sel.year, it) }

    /**
     * Comparison period: a single month is compared with the month before it; several months (or the
     * whole year) are compared with the same months of the previous year, so the two periods always
     * have the same length.
     */
    fun previousRanges(sel: Selection): List<Range> =
        if (sel.sorted.size == 1) listOf(previousRange(Kind.MONTHLY, sel.year, sel.sorted.first()))
        else sel.sorted.map { range(Kind.MONTHLY, sel.year - 1, it) }

    fun title(sel: Selection, subject: String?): String {
        val months = sel.sorted
        val names = JalaliCalendar.MONTH_NAMES
        val span = when {
            sel.isWholeYear -> "سال ${sel.year}"
            months.size == 1 -> "${names[months[0] - 1]} ${sel.year}"
            months.size > 2 && months.last() - months.first() == months.size - 1 ->
                "${names[months.first() - 1]} تا ${names[months.last() - 1]} ${sel.year}"
            else -> months.joinToString("، ") { names[it - 1] } + " ${sel.year}"
        }
        return if (subject == null) "کارنامه حلقه — $span" else "کارنامه $subject — $span"
    }

    /**
     * Size-independent report. Every number is either an average per member of the ring or a percentage
     * of its members, and only members already registered by the end of the period count, so a ring of
     * 5 and a ring of 15 compare on equal terms. With [subject] set the report is for that one member and
     * the numbers are his/her own totals (no "per member" wording).
     */
    fun compute(
        sel: Selection,
        members: List<Member>,
        history: List<HistoryEvent>,
        growthUnlockMillis: List<Long>,
        growthItemsPerMember: Int,
        subject: String? = null,
    ): Result {
        val cur = ranges(sel)
        val prev = previousRanges(sel)
        val items = growthItemsPerMember.coerceAtLeast(1).toDouble()
        val forMember = subject != null

        fun inRanges(t: Long, rs: List<Range>) = rs.any { t >= it.start && t < it.end }
        /** Members that existed during the period (legacy records with no registration time always count). */
        fun nOf(rs: List<Range>): Double {
            val end = rs.maxOfOrNull { it.end } ?: Long.MAX_VALUE
            return members.count { it.registeredAtEpochMillis == 0L || it.registeredAtEpochMillis < end }.coerceAtLeast(1).toDouble()
        }
        fun sum(rs: List<Range>, vararg types: HistoryType): Double =
            history.filter { it.type in types && inRanges(it.createdAtEpochMillis, rs) }.sumOf { (it.amount ?: 0).toDouble() }
        fun count(rs: List<Range>, vararg types: HistoryType): Double =
            history.count { it.type in types && inRanges(it.createdAtEpochMillis, rs) }.toDouble()
        fun xp(rs: List<Range>): Double = sum(rs, HistoryType.XP_EARNED) - sum(rs, HistoryType.XP_DECREASED)
        fun participation(rs: List<Range>): Double =
            history.filter { inRanges(it.createdAtEpochMillis, rs) }.map { it.memberId }.toSet().size / nOf(rs) * 100
        fun growth(rs: List<Range>): Double = growthUnlockMillis.count { inRanges(it, rs) } / (nOf(rs) * items) * 100
        fun booksIn(rs: List<Range>): Double =
            members.sumOf { m -> m.readingBooks.count { inRanges(it.completedAtEpochMillis, rs) } }.toDouble()
        val readingLevel = members.maxOfOrNull { com.kichikan.ak1.domain.model.ReadingRules.progressFor(it.readingBooks).level }?.toDouble() ?: 1.0
        val rewardTypes = arrayOf(HistoryType.REWARD_RECEIVED, HistoryType.WHEEL_REWARD, HistoryType.BIRTHDAY_REWARD)

        val metrics = if (forMember) listOf(
            Metric("مأموریت انجام‌شده", count(cur, HistoryType.MISSION_COMPLETED), count(prev, HistoryType.MISSION_COMPLETED),false, true),
            Metric("XP", xp(cur), xp(prev),false, true),
            Metric("امتیاز", sum(cur, HistoryType.POINTS_EARNED), sum(prev, HistoryType.POINTS_EARNED),false, true),
            Metric("الماس", sum(cur, HistoryType.DIAMONDS_EARNED), sum(prev, HistoryType.DIAMONDS_EARNED),false, true),
            Metric("جوایز دریافتی", count(cur, *rewardTypes), count(prev, *rewardTypes),false, true),
            Metric("کتاب‌خوانی (کتاب)", booksIn(cur), booksIn(prev), false, true),
            Metric("سطح کتاب‌خوانی", readingLevel, 0.0, false, true),
            Metric("درصد پیشرفت نقشه‌ی کمال", growth(cur), growth(prev), true)
        ) else listOf(
            Metric("مأموریت برای هر عضو", count(cur, HistoryType.MISSION_COMPLETED) / nOf(cur), count(prev, HistoryType.MISSION_COMPLETED) / nOf(prev), false),
            Metric("XP برای هر عضو", xp(cur) / nOf(cur), xp(prev) / nOf(prev), false),
            Metric("امتیاز برای هر عضو", sum(cur, HistoryType.POINTS_EARNED) / nOf(cur), sum(prev, HistoryType.POINTS_EARNED) / nOf(prev), false),
            Metric("الماس برای هر عضو", sum(cur, HistoryType.DIAMONDS_EARNED) / nOf(cur), sum(prev, HistoryType.DIAMONDS_EARNED) / nOf(prev), false),
            Metric("جوایز دریافتی برای هر عضو", count(cur, *rewardTypes) / nOf(cur), count(prev, *rewardTypes) / nOf(prev), false),
            Metric("درصد مشارکت اعضا", participation(cur), participation(prev), true),
            Metric("درصد پیشرفت نقشه‌ی کمال", growth(cur), growth(prev), true)
        )

        val n = nOf(cur)
        val divisor = if (forMember) 1.0 else n
        val buckets: List<Pair<String, Range>> = if (sel.sorted.size == 1) {
            val whole = cur.first()
            (0 until 5).mapNotNull { w ->
                val start = whole.start + w * 7 * DAY
                val end = minOf(whole.start + (w + 1) * 7 * DAY, whole.end)
                if (start < end) "هفته ${w + 1}" to Range(start, end) else null
            }
        } else sel.sorted.map { m -> JalaliCalendar.MONTH_NAMES[m - 1] to range(Kind.MONTHLY, sel.year, m) }
        val series = buckets.map { (label, r) -> label to xp(listOf(r)) / divisor }
        return Result(title(sel, subject), metrics, series)
    }

    /** Legacy single-month / whole-year entry point. */
    fun compute(
        kind: Kind,
        year: Int,
        month: Int,
        members: List<Member>,
        history: List<HistoryEvent>,
        growthUnlockMillis: List<Long>,
        growthItemsPerMember: Int
    ): Result = compute(
        Selection(year, if (kind == Kind.YEARLY) (1..12).toSet() else setOf(month)),
        members, history, growthUnlockMillis, growthItemsPerMember, null,
    )
}
