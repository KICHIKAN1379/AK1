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
    data class Metric(val label: String, val value: Double, val previous: Double, val isPercent: Boolean)

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

    /**
     * [growthUnlockMillis]: the moment each growth-map item was unlocked, for every member.
     * [growthItemsPerMember]: how many growth-map items exist in total (abilities plus character).
     */
    fun compute(
        kind: Kind,
        year: Int,
        month: Int,
        members: List<Member>,
        history: List<HistoryEvent>,
        growthUnlockMillis: List<Long>,
        growthItemsPerMember: Int
    ): Result {
        val cur = range(kind, year, month)
        val prev = previousRange(kind, year, month)
        val n = members.size.coerceAtLeast(1).toDouble()
        val items = growthItemsPerMember.coerceAtLeast(1).toDouble()

        fun inRange(t: Long, r: Range) = t >= r.start && t < r.end
        fun sum(r: Range, vararg types: HistoryType): Double =
            history.filter { it.type in types && inRange(it.createdAtEpochMillis, r) }.sumOf { (it.amount ?: 0).toDouble() }
        fun count(r: Range, vararg types: HistoryType): Double =
            history.count { it.type in types && inRange(it.createdAtEpochMillis, r) }.toDouble()
        fun xp(r: Range): Double = sum(r, HistoryType.XP_EARNED) - sum(r, HistoryType.XP_DECREASED)
        fun participation(r: Range): Double =
            history.filter { inRange(it.createdAtEpochMillis, r) }.map { it.memberId }.toSet().size / n * 100
        fun growth(r: Range): Double = growthUnlockMillis.count { inRange(it, r) } / (n * items) * 100

        val metrics = listOf(
            Metric("مأموریت برای هر عضو", count(cur, HistoryType.MISSION_COMPLETED) / n, count(prev, HistoryType.MISSION_COMPLETED) / n, false),
            Metric("XP برای هر عضو", xp(cur) / n, xp(prev) / n, false),
            Metric("امتیاز برای هر عضو", sum(cur, HistoryType.POINTS_EARNED) / n, sum(prev, HistoryType.POINTS_EARNED) / n, false),
            Metric("الماس برای هر عضو", sum(cur, HistoryType.DIAMONDS_EARNED) / n, sum(prev, HistoryType.DIAMONDS_EARNED) / n, false),
            Metric(
                "جوایز دریافتی برای هر عضو",
                count(cur, HistoryType.REWARD_RECEIVED, HistoryType.WHEEL_REWARD, HistoryType.BIRTHDAY_REWARD) / n,
                count(prev, HistoryType.REWARD_RECEIVED, HistoryType.WHEEL_REWARD, HistoryType.BIRTHDAY_REWARD) / n,
                false
            ),
            Metric("درصد مشارکت اعضا", participation(cur), participation(prev), true),
            Metric("درصد پیشرفت نقشه‌ی کمال", growth(cur), growth(prev), true)
        )

        val buckets: List<Pair<String, Range>> = when (kind) {
            Kind.YEARLY -> (1..12).map { m -> JalaliCalendar.MONTH_NAMES[m - 1] to range(kind, year, m) }
            Kind.MONTHLY -> (0 until 5).mapNotNull { w ->
                val start = cur.start + w * 7 * DAY
                val end = minOf(cur.start + (w + 1) * 7 * DAY, cur.end)
                if (start < end) "هفته ${w + 1}" to Range(start, end) else null
            }
        }
        val series = buckets.map { (label, r) -> label to xp(r) / n }
        return Result(title(kind, year, month), metrics, series)
    }
}
