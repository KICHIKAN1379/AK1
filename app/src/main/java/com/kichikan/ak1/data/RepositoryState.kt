package com.kichikan.ak1.data

import com.kichikan.ak1.domain.model.*

/**
 * In-memory source of truth for the offline ring.
 *
 * The state holder owns the mutable collections; AppRepository exposes read-compatible
 * accessors while domain repositories are migrated to narrower state dependencies.
 */
internal class RepositoryState {
    var ring: RingAccount? = null

    val members = mutableListOf<Member>()
    val history = mutableListOf<HistoryEvent>()
    val shop = mutableListOf<ShopItem>()
    val missions = mutableListOf<Mission>()
    val missionCompletions = mutableListOf<MissionCompletion>()
    val missionRequests = mutableListOf<MissionRequest>()
    val missionNotices = mutableListOf<MissionNotice>()
    val sessions = mutableListOf<Session>()
    val attendance = mutableListOf<Attendance>()
    val assets = mutableListOf<CustomAsset>()
    val groups = mutableListOf<Group>()
    val archived = mutableListOf<ArchivedMember>()

    var wheel = WheelConfig()
}
