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
 */
internal interface EconomyMutationPort {
    fun recordXp(memberId: String, amount: Int, reason: String, actor: String? = null)
    fun recordPoints(memberId: String, amount: Int, reason: String, actor: String? = null)
    fun recordDiamonds(memberId: String, amount: Int, reason: String, actor: String? = null)
    fun spendPoints(memberId: String, amount: Int, reason: String, actor: String? = null)
}

internal interface MemberRepositoryPort : EconomyMutationPort {
    val ring: RingAccount?
    fun setRingState(value: RingAccount?)
    val members: MutableList<Member>
    val history: MutableList<HistoryEvent>
    val archived: MutableList<ArchivedMember>
    val assistantAlerts: MutableList<AssistantAlert>
    val assistantFollowUps: MutableList<AssistantFollowUp>
    val attendance: MutableList<Attendance>
    val sessions: MutableList<Session>
    val missionCompletions: MutableList<MissionCompletion>
    val groups: MutableList<Group>
    fun persist()
}

internal interface EconomyRepositoryPort {
    val members: MutableList<Member>
    val history: MutableList<HistoryEvent>
    val shop: MutableList<ShopItem>
    fun updateMember(member: Member)
}

internal interface MissionRepositoryPort : EconomyMutationPort {
    val appContext: Context
    val members: MutableList<Member>
    val history: MutableList<HistoryEvent>
    val missions: MutableList<Mission>
    val missionCompletions: MutableList<MissionCompletion>
    val missionNotices: MutableList<MissionNotice>
    val missionRequests: MutableList<MissionRequest>
    fun persist()
}

internal interface SessionRepositoryPort {
    val members: MutableList<Member>
    val sessions: MutableList<Session>
    val attendance: MutableList<Attendance>
    val history: MutableList<HistoryEvent>
    fun persist()
}

internal interface CatalogRepositoryPort : EconomyMutationPort {
    val assets: MutableList<CustomAsset>
    val history: MutableList<HistoryEvent>
    val members: MutableList<Member>
    val shop: MutableList<ShopItem>
    fun persist()
}

internal interface WheelRepositoryPort : EconomyMutationPort {
    val history: MutableList<HistoryEvent>
    val members: MutableList<Member>
    val shop: MutableList<ShopItem>
    val wheel: WheelConfig
    fun setWheelState(value: WheelConfig)
    fun persist()
}

internal interface AssistantRepositoryPort {
    val appContext: Context
    val archived: MutableList<ArchivedMember>
    val assistantAlerts: MutableList<AssistantAlert>
    val assistantFollowUps: MutableList<AssistantFollowUp>
    val history: MutableList<HistoryEvent>
    val members: MutableList<Member>
    val missionCompletions: MutableList<MissionCompletion>
    val missionRequests: MutableList<MissionRequest>
    val sessions: MutableList<Session>
    fun persist()
}

internal interface CatalogBootstrapperPort {
    val appContext: Context
    val groups: MutableList<Group>
    val history: MutableList<HistoryEvent>
    val members: MutableList<Member>
    val shop: MutableList<ShopItem>
    fun persist()
}
