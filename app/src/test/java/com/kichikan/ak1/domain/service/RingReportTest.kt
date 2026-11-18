package com.kichikan.ak1.domain.service

import com.kichikan.ak1.domain.model.HistoryEvent
import com.kichikan.ak1.domain.model.HistoryType
import com.kichikan.ak1.domain.model.Member
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingReportTest {
    private val sel = RingReport.Selection(1405, setOf(1, 3))

    private fun ring(size: Int): Pair<List<Member>, List<HistoryEvent>> {
        val start = RingReport.range(RingReport.Kind.MONTHLY, 1405, 1).start + 1000
        val members = (1..size).map { Member("m$it", "r", "عضو $it") }
        // Every member earns 10 XP and completes one mission, whatever the ring size.
        val history = members.flatMap { m ->
            listOf(
                HistoryEvent("x-${m.id}", m.id, HistoryType.XP_EARNED, 10, "xp", createdAtEpochMillis = start),
                HistoryEvent("t-${m.id}", m.id, HistoryType.MISSION_COMPLETED, 1, "mission", createdAtEpochMillis = start),
            )
        }
        return members to history
    }

    @Test
    fun ringOfFiveAndFifteenCompareEqually() {
        val (m5, h5) = ring(5)
        val (m15, h15) = ring(15)
        val a = RingReport.compute(sel, m5, h5, emptyList(), 10)
        val b = RingReport.compute(sel, m15, h15, emptyList(), 10)
        assertEquals(a.metrics.map { it.value }, b.metrics.map { it.value })
        assertEquals(10.0, a.metrics.first { it.label == "XP برای هر عضو" }.value, 1e-9)
        assertEquals(100.0, a.metrics.first { it.label == "درصد مشارکت اعضا" }.value, 1e-9)
    }

    @Test
    fun memberReportUsesTheMembersOwnTotalsWithoutPerMemberWording() {
        val (members, history) = ring(5)
        val r = RingReport.compute(sel, members.take(1), history.filter { it.memberId == "m1" }, emptyList(), 10, subject = "علی")
        assertTrue(r.title.startsWith("کارنامه علی"))
        assertTrue(r.metrics.none { it.label.contains("برای هر عضو") })
        assertEquals(10.0, r.metrics.first { it.label == "XP" }.value, 1e-9)
        assertTrue(r.metrics.none { it.label.contains("سوابق") })
        assertTrue(r.metrics.any { it.label == "کتاب‌خوانی (کتاب)" })
        assertTrue(r.metrics.any { it.label == "سطح کتاب‌خوانی" })
    }

    @Test
    fun monthsMayBeConsecutiveOrSeparate() {
        assertEquals("کارنامه حلقه — فروردین تا خرداد 1405", RingReport.title(RingReport.Selection(1405, setOf(1, 2, 3)), null))
        assertEquals("کارنامه حلقه — فروردین، خرداد 1405", RingReport.title(sel, null))
        assertEquals("کارنامه حلقه — سال 1405", RingReport.title(RingReport.Selection(1405, (1..12).toSet()), null))
        assertEquals(2, RingReport.ranges(sel).size)
        // several months compare with the same months of the previous year
        assertEquals(RingReport.range(RingReport.Kind.MONTHLY, 1404, 1), RingReport.previousRanges(sel).first())
    }

    @Test
    fun membersRegisteredAfterThePeriodDoNotDiluteIt() {
        val (members, history) = ring(5)
        val late = members.map { it.copy(registeredAtEpochMillis = Long.MAX_VALUE / 2) }
        val withLate = members + late.map { it.copy(id = it.id + "-late") }
        val a = RingReport.compute(sel, members, history, emptyList(), 10)
        val b = RingReport.compute(sel, withLate, history, emptyList(), 10)
        assertEquals(a.metrics.map { it.value }, b.metrics.map { it.value })
        assertFalse(b.metrics.isEmpty())
    }
}
