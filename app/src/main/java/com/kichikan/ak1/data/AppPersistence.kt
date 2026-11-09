package com.kichikan.ak1.data

import android.content.Context
import com.kichikan.ak1.domain.model.*

/**
 * Persistence boundary for the mentor app's local ring data.
 *
 * This adapter deliberately delegates the existing schema and migration behavior to
 * [LocalStore]. Keeping serialization behind this type lets domain repositories depend
 * on a stable persistence contract without changing the version-9 backup format.
 */
internal class AppPersistence(context: Context) {
    private val store = LocalStore(context.applicationContext)

    fun load(): LoadedState? = store.load()

    fun save(
        ring: RingAccount?,
        members: List<Member>,
        history: List<HistoryEvent>,
        shop: List<ShopItem>,
        wheel: WheelConfig,
        missions: List<Mission>,
        missionCompletions: List<MissionCompletion>,
        sessions: List<Session>,
        attendance: List<Attendance>,
        assets: List<CustomAsset>,
        groups: List<Group>,
        archived: List<ArchivedMember>,
        missionRequests: List<MissionRequest>,
        missionNotices: List<MissionNotice>
    ) {
        store.save(
            ring, members, history, shop, wheel, missions, missionCompletions,
            sessions, attendance, assets, groups, archived, missionRequests, missionNotices
        )
    }

    fun exportJson(): String? = store.exportJson()

    fun importJson(raw: String) = store.importJson(raw)
}
