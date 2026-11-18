package com.kichikan.ak1.data

import android.content.Context
import com.kichikan.ak1.domain.assistant.AssistantAlert
import com.kichikan.ak1.domain.assistant.AssistantFollowUp
import com.kichikan.ak1.domain.model.*

/**
 * Narrow contracts between the compatibility facade and domain repositories.
 *
 * Each repository receives only the state and cross-domain operations it currently needs.
 * These ports are internal migration seams, not public app APIs.
 *
 * Collections expose a List-only query surface. Mutations go through RepositoryCollection
 * copy-on-write commands; the backing MutableList is never returned to a consumer.
 */
internal interface EconomyMutationPort {
    fun recordXp(memberId: String, amount: Int, reason: String, actor: String? = null): Member
    fun recordPoints(memberId: String, amount: Int, reason: String, actor: String? = null): Member
    fun recordDiamonds(memberId: String, amount: Int, reason: String, actor: String? = null): Member
    fun spendPoints(memberId: String, amount: Int, reason: String, actor: String? = null): Member
}

internal interface MemberRepositoryPort : EconomyMutationPort {
    val ring: RingAccount?
    fun setRingState(value: RingAccount?)
    val members: RepositoryCollection<Member>
    val history: RepositoryCollection<HistoryEvent>
    val archived: RepositoryCollection<ArchivedMember>
    val assistantAlerts: RepositoryCollection<AssistantAlert>
    val assistantFollowUps: RepositoryCollection<AssistantFollowUp>
    val attendance: RepositoryCollection<Attendance>
    val sessions: RepositoryCollection<Session>
    val missionCompletions: RepositoryCollection<MissionCompletion>
    val groups: RepositoryCollection<Group>
    fun persist()
}

internal interface EconomyRepositoryPort {
    val members: RepositoryCollection<Member>
    val history: RepositoryCollection<HistoryEvent>
    val shop: RepositoryCollection<ShopItem>
    fun updateMember(member: Member)
}

internal interface MissionRepositoryPort : EconomyMutationPort {
    val appContext: Context
    val members: RepositoryCollection<Member>
    val history: RepositoryCollection<HistoryEvent>
    val missions: RepositoryCollection<Mission>
    val missionCompletions: RepositoryCollection<MissionCompletion>
    val missionNotices: RepositoryCollection<MissionNotice>
    val missionRequests: RepositoryCollection<MissionRequest>
    fun persist()
}

internal interface SessionRepositoryPort {
    val members: RepositoryCollection<Member>
    val sessions: RepositoryCollection<Session>
    val attendance: RepositoryCollection<Attendance>
    val history: RepositoryCollection<HistoryEvent>
    fun persist()
}

internal interface CatalogRepositoryPort : EconomyMutationPort {
    val assets: RepositoryCollection<CustomAsset>
    val history: RepositoryCollection<HistoryEvent>
    val members: RepositoryCollection<Member>
    val shop: RepositoryCollection<ShopItem>
    fun persist()
}

internal interface WheelRepositoryPort : EconomyMutationPort {
    val history: RepositoryCollection<HistoryEvent>
    val members: RepositoryCollection<Member>
    val shop: RepositoryCollection<ShopItem>
    val wheel: WheelConfig
    fun setWheelState(value: WheelConfig)
    fun persist()
}

internal interface AssistantRepositoryPort {
    val appContext: Context
    val archived: RepositoryCollection<ArchivedMember>
    val assistantAlerts: RepositoryCollection<AssistantAlert>
    val assistantFollowUps: RepositoryCollection<AssistantFollowUp>
    val history: RepositoryCollection<HistoryEvent>
    val members: RepositoryCollection<Member>
    val missionCompletions: RepositoryCollection<MissionCompletion>
    val missionRequests: RepositoryCollection<MissionRequest>
    val sessions: RepositoryCollection<Session>
    fun persist()
}

internal interface CatalogBootstrapperPort {
    val appContext: Context
    val groups: RepositoryCollection<Group>
    val history: RepositoryCollection<HistoryEvent>
    val members: RepositoryCollection<Member>
    val shop: RepositoryCollection<ShopItem>
    fun persist()
}

/** Startup work kept outside the repository constructor and Compose lifecycle. */
internal interface AppInitializationPort {
    fun seedDefaultHiddenMissions()
    fun persist()
    fun processBirthdayRewards(now: Long = System.currentTimeMillis()): Int
    fun evaluateAssistantAlerts(now: Long = System.currentTimeMillis())
}
