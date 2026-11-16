package com.kichikan.ak1.domain.assistant

import com.kichikan.ak1.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class AssistantQueryEngineTest {
    private val zone = ZoneId.of("UTC")
    private val now = 1_800_000_000_000L
    private fun member(id: String, registered: Long = now - 100L * 86_400_000L) =
        Member(id = id, ringId = "ring", name = id, registeredAtEpochMillis = registered,
            monitoringStartAtEpochMillis = registered)

    @Test fun membersInInitialThirtyDaysAreExcludedFromEveryQuery() {
        val old = member("old")
        val fresh = member("fresh", now - 5L * 86_400_000L)
        val result = AssistantQueryEngine.execute(AssistantQueryEngine.Query.SESSIONS_DUE,
            listOf(old, fresh), emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), now, zone)
        assertEquals(1, result.excludedDuringInitialMonitoring)
        assertEquals(listOf("old"), result.rows.map { it.memberId })
    }

    @Test fun threeCalendarMonthSessionQuestionUsesIndividualSessionsOnly() {
        val old = member("old")
        val recent = member("recent")
        val sessions = listOf(
            Session("s1", "old", "گفت‌وگو", startsAt = now - 100L * 86_400_000L),
            Session("s2", "recent", "گفت‌وگو", startsAt = now - 20L * 86_400_000L)
        )
        val result = AssistantQueryEngine.execute(AssistantQueryEngine.Query.SESSIONS_DUE,
            listOf(old, recent), emptyList(), sessions, emptyList(), emptyList(),
            emptyList(), emptyList(), now, zone)
        assertEquals(listOf("old"), result.rows.map { it.memberId })
    }

    @Test fun xpDeclineRequiresEnoughPriorPeriodData() {
        val old = member("old")
        val weak = member("weak")
        val events = listOf(
            HistoryEvent("1", "old", HistoryType.XP_EARNED, 40, "XP", createdAtEpochMillis = now - 20L * 86_400_000L),
            HistoryEvent("2", "old", HistoryType.XP_EARNED, 5, "XP", createdAtEpochMillis = now - 3L * 86_400_000L),
            HistoryEvent("3", "weak", HistoryType.XP_EARNED, 5, "XP", createdAtEpochMillis = now - 20L * 86_400_000L)
        )
        val result = AssistantQueryEngine.execute(AssistantQueryEngine.Query.XP_DECLINE,
            listOf(old, weak), events, emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), now, zone)
        assertEquals(listOf("old"), result.rows.map { it.memberId })
        assertEquals("87٪ کاهش", result.rows.single().value)
    }

    @Test fun missionQuestionCountsOnlyRecordedCompletionsAndDoesNotInventAssignments() {
        val one = member("one")
        val two = member("two")
        val completion = MissionCompletion("c1", "m1", listOf("one"), now - 2L * 86_400_000L)
        val result = AssistantQueryEngine.execute(AssistantQueryEngine.Query.MISSION_ACTIVITY,
            listOf(one, two), emptyList(), emptyList(), listOf(completion), emptyList(),
            emptyList(), emptyList(), now, zone)
        assertEquals(listOf("one"), result.rows.map { it.memberId })
        assertTrue(result.rows.single().detail.contains("ثبت‌شده"))
    }
}
