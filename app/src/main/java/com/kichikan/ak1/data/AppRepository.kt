package com.kichikan.ak1.data

import android.content.Context
import com.kichikan.ak1.domain.calendar.BirthdayRules
import com.kichikan.ak1.domain.calendar.JalaliCalendar
import com.kichikan.ak1.domain.model.*
import com.kichikan.ak1.domain.service.EconomyChange
import com.kichikan.ak1.domain.service.EconomyService
import com.kichikan.ak1.domain.service.HistoryEvents
import com.kichikan.ak1.domain.service.MissionService
import com.kichikan.ak1.domain.service.AssetArt
import com.kichikan.ak1.domain.service.ProfilePack
import com.kichikan.ak1.domain.service.RankArt
import com.kichikan.ak1.domain.service.RankSystem
import com.kichikan.ak1.domain.service.ShopService
import com.kichikan.ak1.domain.service.WheelService
import com.kichikan.ak1.notification.MissionNotifications
import java.time.Instant
import java.time.ZoneId

class AppRepository(context: Context) {
    private fun newId(prefix: String): String = "$prefix-${java.util.UUID.randomUUID()}"
    internal val appContext = context.applicationContext
    private val persistence = AppPersistence(appContext)

    var ring: RingAccount? = null
        internal set

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
        internal set

    init {
        persistence.load()?.let { state ->
            ring = state.ring
            members += state.members
            history += HistoryEvents.dedupeNewest(state.history)
            shop += state.shop
            wheel = state.wheel
            missions += state.missions
            missionCompletions += state.missionCompletions
            missionRequests += state.missionRequests
            missionNotices += state.missionNotices
            sessions += state.sessions
            attendance += state.attendance
            assets += state.assets
            groups += state.groups
            archived += state.archived
        }
        val catalogPrefs = appContext.getSharedPreferences("ak1_settings", Context.MODE_PRIVATE)
        if (!catalogPrefs.getBoolean("starter_catalog_created", false)) {
            seedStarterCatalog(regenerate = false)
            catalogPrefs.edit().putBoolean("starter_catalog_created", true).putInt("starter_art_version", AssetArt.ART_VERSION).apply()
        } else if (catalogPrefs.getInt("starter_art_version", 0) < AssetArt.ART_VERSION) {
            seedStarterCatalog(regenerate = true)
            regenerateLevelAvatars()
            catalogPrefs.edit().putInt("starter_art_version", AssetArt.ART_VERSION).apply()
        }
        // PNGs dropped into assets/art/profiles become giftable avatars/frames (see ProfilePack).
        runCatching { syncProfilePack() }
        runCatching { seedDefaultHiddenMissions() }
        // The fifteen-rank pack is copied out of the APK and registered as manual claims.
        runCatching { RankArt.ensure(appContext) }
        runCatching { syncRankPack() }
        runCatching { syncGroupPack() }
        runCatching { upgradeStarterLooks() }
        runCatching { migrateNoFreeLooks() }
        // Deliberately no automatic avatar/frame grants here: level only unlocks a manual claim.
        persist()
        // The 09:00 alarm used to be the only caller, so a birthday that fell on a day the phone
        // was off was never rewarded. processBirthdayRewards() is idempotent per Jalali year,
        // which makes this catch-up safe to run on every start.
        runCatching { processBirthdayRewards() }
    }

    private val bootstrapper by lazy(LazyThreadSafetyMode.NONE) { CatalogBootstrapper(this) }

    private fun seedStarterCatalog(regenerate: Boolean) = bootstrapper.seedStarterCatalog(regenerate)
    private fun syncProfilePack() = bootstrapper.syncProfilePack()
    private fun syncRankPack() = bootstrapper.syncRankPack()
    private fun syncGroupPack() = bootstrapper.syncGroupPack()
    private fun regenerateLevelAvatars() = bootstrapper.regenerateLevelAvatars()
    private fun upgradeStarterLooks() = bootstrapper.upgradeStarterLooks()
    private fun migrateNoFreeLooks() = bootstrapper.migrateNoFreeLooks()
    fun setGroupLook(groupId: String, crestItemId: String?, cardItemId: String?) =
        bootstrapper.setGroupLook(groupId, crestItemId, cardItemId)

    private val memberDomain by lazy(LazyThreadSafetyMode.NONE) { MemberRepository(this, ::newId) }

    fun createRing(name: String, username: String): RingAccount = memberDomain.createRing(name, username)
    fun addMember(name: String, birthDate: String? = null, responsibility: String = ""): Member =
        memberDomain.addMember(name, birthDate, responsibility)
    fun processBirthdayRewards(now: Long = System.currentTimeMillis()): Int = memberDomain.processBirthdayRewards(now)
    fun addGroup(name: String): Group = memberDomain.addGroup(name)
    fun updateGroup(group: Group) = memberDomain.updateGroup(group)
    fun deleteGroup(groupId: String) = memberDomain.deleteGroup(groupId)
    fun assignMemberToGroup(memberId: String, groupId: String?) = memberDomain.assignMemberToGroup(memberId, groupId)
    fun adjustGroupScore(groupId: String, pointsDelta: Int, diamondsDelta: Int, reason: String) =
        memberDomain.adjustGroupScore(groupId, pointsDelta, diamondsDelta, reason)
    fun deleteMember(memberId: String) = memberDomain.deleteMember(memberId)
    fun archiveMember(memberId: String): Boolean = memberDomain.archiveMember(memberId)
    fun restoreArchivedMember(memberId: String): Boolean = memberDomain.restoreArchivedMember(memberId)
    fun purgeArchivedMember(memberId: String): Boolean = memberDomain.purgeArchivedMember(memberId)
    fun updateMember(member: Member) = memberDomain.updateMember(member)
    fun readingProgressFor(member: Member): ReadingProgress = memberDomain.readingProgressFor(member)
    fun readingProgressFor(memberId: String): ReadingProgress? = memberDomain.readingProgressFor(memberId)
    fun addReadingBook(
        memberId: String, title: String, dailyMinutes: Int, pages: Int, answers: Int,
        completedAtEpochMillis: Long = System.currentTimeMillis()
    ): Member = memberDomain.addReadingBook(memberId, title, dailyMinutes, pages, answers, completedAtEpochMillis)
    fun updateReadingBook(memberId: String, book: ReadingBook): Member = memberDomain.updateReadingBook(memberId, book)
    fun deleteReadingBook(memberId: String, bookId: String): Member = memberDomain.deleteReadingBook(memberId, bookId)
    fun updateGroupProfile(groupId: String, description: String, profileImagePath: String?) =
        memberDomain.updateGroupProfile(groupId, description, profileImagePath)

    private val economyDomain by lazy(LazyThreadSafetyMode.NONE) { EconomyRepository(this) }

    fun recordXp(memberId: String, amount: Int, reason: String, actor: String? = null) =
        economyDomain.recordXp(memberId, amount, reason, actor)
    fun recordPoints(memberId: String, amount: Int, reason: String, actor: String? = null) =
        economyDomain.recordPoints(memberId, amount, reason, actor)
    fun recordDiamonds(memberId: String, amount: Int, reason: String, actor: String? = null) =
        economyDomain.recordDiamonds(memberId, amount, reason, actor)
    fun decreaseXp(memberId: String, amount: Int, reason: String, actor: String? = null) =
        economyDomain.decreaseXp(memberId, amount, reason, actor)
    fun spendPoints(memberId: String, amount: Int, reason: String, actor: String? = null) =
        economyDomain.spendPoints(memberId, amount, reason, actor)
    fun decreaseLevel(memberId: String, targetLevel: Int, reason: String, actor: String? = null) =
        economyDomain.decreaseLevel(memberId, targetLevel, reason, actor)

    private val wheelDomain by lazy(LazyThreadSafetyMode.NONE) { WheelRepository(this) }

    fun updateWheelItem(item: WheelItem) = wheelDomain.updateWheelItem(item)
    fun deleteWheelItem(itemId: String) = wheelDomain.deleteWheelItem(itemId)
    fun setWheel(config: WheelConfig) = wheelDomain.setWheel(config)

    private val missionDomain by lazy(LazyThreadSafetyMode.NONE) { MissionRepository(this, ::newId) }

    fun updateMission(mission: Mission) = missionDomain.updateMission(mission)
    fun deleteMission(missionId: String) = missionDomain.deleteMission(missionId)
    fun addMission(mission: Mission) = missionDomain.addMission(mission)
    fun hiddenReceivedIds(missionId: String): Set<String> = missionDomain.hiddenReceivedIds(missionId)
    fun hiddenReceivedAt(memberId: String, missionId: String): Long? = missionDomain.hiddenReceivedAt(memberId, missionId)
    private fun seedDefaultHiddenMissions() = missionDomain.seedDefaultHiddenMissions()
    fun completeMission(mission: Mission, memberIds: List<String>, reason: String? = null): MissionCompletion =
        missionDomain.completeMission(mission, memberIds, reason)
    fun requestMission(memberId: String, missionId: String, message: String = ""): MissionRequest =
        missionDomain.requestMission(memberId, missionId, message)
    fun reviewMissionRequest(requestId: String, approve: Boolean, mentorNote: String = ""): MissionRequest =
        missionDomain.reviewMissionRequest(requestId, approve, mentorNote)
    fun markMissionNoticesRead() = missionDomain.markMissionNoticesRead()

    private val sessionDomain by lazy(LazyThreadSafetyMode.NONE) { SessionRepository(this, ::newId) }

    fun addSession(memberId: String, title: String, topic: String = "", location: String? = null): Session =
        sessionDomain.addSession(memberId, title, topic, location)
    fun updateSession(session: Session) = sessionDomain.updateSession(session)
    fun deleteSession(sessionId: String) = sessionDomain.deleteSession(sessionId)
    fun addSession(session: Session) = sessionDomain.addSession(session)
    fun recordAttendance(item: Attendance) = sessionDomain.recordAttendance(item)
    fun recordWeeklyAttendance(memberId: String, dateEpochMillis: Long, status: AttendanceStatus, note: String? = null) =
        sessionDomain.recordWeeklyAttendance(memberId, dateEpochMillis, status, note)

    private val catalogDomain by lazy(LazyThreadSafetyMode.NONE) { CatalogRepository(this) }

    fun grantShopItem(memberId: String, itemId: String, reason: String): Boolean =
        catalogDomain.grantShopItem(memberId, itemId, reason)
    fun purchaseShopItem(memberId: String, itemId: String): Boolean =
        catalogDomain.purchaseShopItem(memberId, itemId)
    fun equipShopItem(memberId: String, itemId: String) = catalogDomain.equipShopItem(memberId, itemId)
    fun chooseLook(memberId: String, type: ShopItemType, itemId: String?) =
        catalogDomain.chooseLook(memberId, type, itemId)
    fun updateShopItem(item: ShopItem) = catalogDomain.updateShopItem(item)
    fun deleteShopItem(itemId: String) = catalogDomain.deleteShopItem(itemId)
    fun addShopItem(item: ShopItem) = catalogDomain.addShopItem(item)
    fun addAsset(asset: CustomAsset) = catalogDomain.addAsset(asset)

    fun spinWheel(memberId: String): WheelItem? = wheelDomain.spinWheel(memberId)

    fun persist() {
        normaliseHistoryIds()
        persistence.save(
            ring, members, history, shop, wheel, missions, missionCompletions,
            sessions, attendance, assets, groups, archived, missionRequests, missionNotices
        )
    }

    /**
     * Guarantees a unique id per event before writing. The member ledger uses ids as list keys,
     * so two events sharing one (same-millisecond XP, the same attendance day) would crash it.
     */
    private fun normaliseHistoryIds() {
        val used = mutableSetOf<String>()
        for (index in history.indices) {
            val event = history[index]
            val unique = HistoryEvents.unique(event, used)
            if (unique !== event) history[index] = unique
        }
    }

    fun exportBackup(): String {
        return persistence.exportJson() ?: run {
            persist()
            persistence.exportJson() ?: "{}"
        }
    }

    fun importBackup(raw: String) {
        persistence.importJson(raw)

        members.clear()
        history.clear()
        shop.clear()
        missions.clear()
        missionCompletions.clear()
        missionRequests.clear()
        missionNotices.clear()
        sessions.clear()
        attendance.clear()
        assets.clear()
        groups.clear()
        archived.clear()
        wheel = WheelConfig()

        persistence.load()?.let { state ->
            ring = state.ring
            members += state.members
            history += HistoryEvents.dedupeNewest(state.history)
            shop += state.shop
            wheel = state.wheel
            missions += state.missions
            missionCompletions += state.missionCompletions
            missionRequests += state.missionRequests
            missionNotices += state.missionNotices
            sessions += state.sessions
            attendance += state.attendance
            assets += state.assets
            groups += state.groups
            archived += state.archived
        }
    }
}