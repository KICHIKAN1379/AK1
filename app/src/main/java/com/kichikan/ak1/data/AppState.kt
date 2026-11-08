package com.kichikan.ak1.data

import com.kichikan.ak1.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class HomeDataState(
    val ring: RingAccount? = null,
    val members: List<Member> = emptyList(),
    val history: List<HistoryEvent> = emptyList(),
    val attendance: List<Attendance> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val missions: List<Mission> = emptyList(),
    val missionCompletions: List<MissionCompletion> = emptyList()
)

internal data class MembersDataState(
    val members: List<Member> = emptyList(),
    val groups: List<Group> = emptyList(),
    val shop: List<ShopItem> = emptyList(),
    val history: List<HistoryEvent> = emptyList(),
    val archived: List<ArchivedMember> = emptyList(),
    val assets: List<CustomAsset> = emptyList()
)

internal data class MissionDataState(
    val members: List<Member> = emptyList(),
    val missions: List<Mission> = emptyList(),
    val completions: List<MissionCompletion> = emptyList(),
    val requests: List<MissionRequest> = emptyList(),
    val notices: List<MissionNotice> = emptyList(),
    val history: List<HistoryEvent> = emptyList()
)

internal data class SessionsDataState(
    val members: List<Member> = emptyList(),
    val groups: List<Group> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val attendance: List<Attendance> = emptyList(),
    val shop: List<ShopItem> = emptyList()
)

internal data class CatalogDataState(
    val members: List<Member> = emptyList(),
    val shop: List<ShopItem> = emptyList(),
    val history: List<HistoryEvent> = emptyList(),
    val assets: List<CustomAsset> = emptyList(),
    val wheel: WheelConfig = WheelConfig()
)

internal data class ProgressDataState(
    val members: List<Member> = emptyList(),
    val groups: List<Group> = emptyList(),
    val history: List<HistoryEvent> = emptyList(),
    val missions: List<Mission> = emptyList(),
    val completions: List<MissionCompletion> = emptyList(),
    val wheel: WheelConfig = WheelConfig(),
    val sessions: List<Session> = emptyList(),
    val attendance: List<Attendance> = emptyList(),
    val shop: List<ShopItem> = emptyList()
)

/**
 * Publishes immutable, domain-scoped snapshots.
 *
 * StateFlow equality suppresses emissions for domains whose data did not change, so
 * a write to one area does not automatically invalidate every observing destination.
 */
internal class AppStatePublisher {
    private val _home = MutableStateFlow(HomeDataState())
    val home: StateFlow<HomeDataState> = _home.asStateFlow()

    private val _members = MutableStateFlow(MembersDataState())
    val members: StateFlow<MembersDataState> = _members.asStateFlow()

    private val _missions = MutableStateFlow(MissionDataState())
    val missions: StateFlow<MissionDataState> = _missions.asStateFlow()

    private val _sessions = MutableStateFlow(SessionsDataState())
    val sessions: StateFlow<SessionsDataState> = _sessions.asStateFlow()

    private val _catalog = MutableStateFlow(CatalogDataState())
    val catalog: StateFlow<CatalogDataState> = _catalog.asStateFlow()

    private val _progress = MutableStateFlow(ProgressDataState())
    val progress: StateFlow<ProgressDataState> = _progress.asStateFlow()

    fun publish(state: RepositoryState) {
        // Copy each mutable collection once; snapshots share these immutable list instances.
        val members = state.members.toList()
        val groups = state.groups.toList()
        val history = state.history.toList()
        val shop = state.shop.toList()
        val missions = state.missions.toList()
        val completions = state.missionCompletions.toList()
        val requests = state.missionRequests.toList()
        val notices = state.missionNotices.toList()
        val sessions = state.sessions.toList()
        val attendance = state.attendance.toList()
        val assets = state.assets.toList()
        val archived = state.archived.toList()
        val wheel = state.wheel

        _home.value = HomeDataState(state.ring, members, history, attendance, sessions, missions, completions)
        _members.value = MembersDataState(members, groups, shop, history, archived, assets)
        _missions.value = MissionDataState(members, missions, completions, requests, notices, history)
        _sessions.value = SessionsDataState(members, groups, sessions, attendance, shop)
        _catalog.value = CatalogDataState(members, shop, history, assets, wheel)
        _progress.value = ProgressDataState(
            members, groups, history, missions, completions, wheel, sessions, attendance, shop
        )
    }
}
