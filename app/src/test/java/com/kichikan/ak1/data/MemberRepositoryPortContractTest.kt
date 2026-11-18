package com.kichikan.ak1.data

import com.kichikan.ak1.domain.assistant.AssistantAlert
import com.kichikan.ak1.domain.assistant.AssistantFollowUp
import com.kichikan.ak1.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberRepositoryPortContractTest {
    @Test
    fun memberLifecycleUsesCollectionCommandsAndPreservesOrder() {
        val port = FakeMemberRepositoryPort()
        var nextId = 0
        val repository = MemberRepository(port) { prefix -> "$prefix-${++nextId}" }

        val first = repository.addMember("عضو اول")
        val second = repository.addMember("عضو دوم")

        assertEquals(listOf(first.id, second.id), port.members.map { it.id })
        assertEquals(2, port.persistCount)
        assertFalse(port.members is MutableList<*>)

        assertTrue(repository.archiveMember(first.id))
        assertEquals(listOf(second.id), port.members.map { it.id })
        assertEquals(first.id, port.archived.single().member.id)

        assertTrue(repository.restoreArchivedMember(first.id))
        assertEquals(listOf(second.id, first.id), port.members.map { it.id })
        assertTrue(port.archived.isEmpty())
        assertEquals(4, port.persistCount)
    }

    private class FakeMemberRepositoryPort : MemberRepositoryPort {
        private var currentRing: RingAccount? = RingAccount("ring-1", "حلقه آزمون", "test")
        override val ring: RingAccount?
            get() = currentRing

        override fun setRingState(value: RingAccount?) {
            currentRing = value
        }

        override val members = RepositoryCollection(mutableListOf<Member>())
        override val history = RepositoryCollection(mutableListOf<HistoryEvent>())
        override val archived = RepositoryCollection(mutableListOf<ArchivedMember>())
        override val assistantAlerts = RepositoryCollection(mutableListOf<AssistantAlert>())
        override val assistantFollowUps = RepositoryCollection(mutableListOf<AssistantFollowUp>())
        override val attendance = RepositoryCollection(mutableListOf<Attendance>())
        override val sessions = RepositoryCollection(mutableListOf<Session>())
        override val missionCompletions = RepositoryCollection(mutableListOf<MissionCompletion>())
        override val groups = RepositoryCollection(mutableListOf<Group>())

        var persistCount = 0
            private set

        override fun persist() {
            persistCount++
        }

        override fun recordXp(memberId: String, amount: Int, reason: String, actor: String?): Member =
            error("Not used by this contract test")

        override fun recordPoints(memberId: String, amount: Int, reason: String, actor: String?): Member =
            error("Not used by this contract test")

        override fun recordDiamonds(memberId: String, amount: Int, reason: String, actor: String?): Member =
            error("Not used by this contract test")

        override fun spendPoints(memberId: String, amount: Int, reason: String, actor: String?): Member =
            error("Not used by this contract test")
    }
}
