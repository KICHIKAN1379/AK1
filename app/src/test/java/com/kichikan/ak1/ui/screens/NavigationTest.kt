package com.kichikan.ak1.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTest {
    @Test
    fun assistant_returns_to_home_parent() {
        assertEquals(Tab.HOME, parentOf(Tab.ASSISTANT))
    }

    @Test
    fun weekly_pulse_returns_to_home_parent() {
        assertEquals(Tab.HOME, parentOf(Tab.WEEKLY_PULSE))
    }

    @Test
    fun mission_center_returns_to_services_parent() {
        assertEquals(Tab.SERVICES, parentOf(Tab.MISSION_ACHIEVEMENTS))
    }
}
