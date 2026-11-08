package com.kichikan.ak1.data

import com.kichikan.ak1.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class AppStatePublisherTest {
    @Test
    fun publishesImmutableSnapshotsForEachDomain() {
        val state = RepositoryState()
        state.ring = RingAccount("ring-1", "حلقه آزمایشی", "test")
        state.members += Member("member-1", "ring-1", "عضو آزمایشی")
        state.groups += Group("group-1", "ring-1", "گروه یک")
        state.missions += Mission("mission-1", "مأموریت یک")
        state.sessions += Session("session-1", "member-1", "جلسه یک", startsAt = 1L)
        state.attendance += Attendance("attendance-1", "member-1", 1L, AttendanceStatus.PRESENT)

        val publisher = AppStatePublisher()
        publisher.publish(state)

        assertEquals("حلقه آزمایشی", publisher.home.value.ring?.ringName)
        assertEquals("عضو آزمایشی", publisher.home.value.members.single().name)
        assertEquals("گروه یک", publisher.members.value.groups.single().name)
        assertEquals("مأموریت یک", publisher.missions.value.missions.single().title)
        assertEquals("جلسه یک", publisher.sessions.value.sessions.single().title)
        assertEquals(AttendanceStatus.PRESENT, publisher.sessions.value.attendance.single().status)
    }

    @Test
    fun laterMutationsDoNotChangePreviouslyPublishedSnapshots() {
        val state = RepositoryState()
        state.members += Member("member-1", "ring-1", "عضو اول")

        val publisher = AppStatePublisher()
        publisher.publish(state)
        val previous = publisher.members.value

        state.members += Member("member-2", "ring-1", "عضو دوم")
        publisher.publish(state)

        assertEquals(1, previous.members.size)
        assertEquals(2, publisher.members.value.members.size)
        assertEquals(0, publisher.sessions.value.sessions.size)
    }
}
