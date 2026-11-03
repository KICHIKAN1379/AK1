package com.kichikan.ak1.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankSystemTest {
    @Test fun avatar_tiers_advance_one_level_at_a_time() {
        assertEquals(15, RankSystem.tiers.size)
        RankSystem.tiers.forEachIndexed { index, tier ->
            assertEquals(index + 1, tier.index)
            assertEquals(index + 1, tier.minLevel)
        }
        assertEquals("سرباز", RankSystem.forLevel(0).name)
        assertEquals("سرباز", RankSystem.forLevel(1).name)
        assertEquals("جنگجو", RankSystem.forLevel(2).name)
        assertEquals("اسطوره", RankSystem.forLevel(14).name)
        assertEquals("اسطوره جاودان", RankSystem.forLevel(15).name)
        assertEquals("اسطوره جاودان", RankSystem.forLevel(70).name)
    }

    @Test fun military_badges_cover_soldier_to_general() {
        assertEquals(21, RankSystem.badges.size)
        assertEquals("سرباز سوم", RankSystem.badgeForLevel(0).name)
        assertEquals("سرباز سوم", RankSystem.badgeForLevel(1).name)
        assertEquals("ارتشبد", RankSystem.badgeForLevel(21).name)
        assertEquals("ارتشبد", RankSystem.badgeForLevel(80).name)
        assertEquals(21, RankSystem.badges.map { it.id }.distinct().size)
        assertTrue(RankSystem.badges.all { it.minLevel == it.index })
    }
}
