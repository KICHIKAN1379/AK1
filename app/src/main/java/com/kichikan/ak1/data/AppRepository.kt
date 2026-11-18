package com.kichikan.ak1.data

import android.content.Context
import com.kichikan.ak1.domain.calendar.BirthdayRules
import com.kichikan.ak1.domain.calendar.JalaliCalendar
import com.kichikan.ak1.domain.model.*
import com.kichikan.ak1.domain.assistant.*
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

internal class AppRepository(context: Context) :
    MemberRepositoryPort,
    EconomyRepositoryPort,
    MissionRepositoryPort,
    SessionRepositoryPort,
    CatalogRepositoryPort,
    WheelRepositoryPort,
    AssistantRepositoryPort,
    CatalogBootstrapperPort,
    AppInitializationPort {
    private fun newId(prefix: String): String = "$prefix-${java.util.UUID.randomUUID()}"
    override val appContext = context.applicationContext
    private val persistence = AppPersistence(appContext)

    private val state = RepositoryState()
    private val statePublisher = AppStatePublisher()

    internal val homeState get() = statePublisher.home
    internal val membersState get() = statePublisher.members
    internal val missionsState get() = statePublisher.missions
    internal val sessionsState get() = statePublisher.sessions
    internal val catalogState get() = statePublisher.catalog
    internal val progressState get() = statePublisher.progress
    internal val assistantState get() = statePublisher.assistant

    override var ring: RingAccount?
        get() = state.ring
        internal set(value) { state.ring = value }

    override val members: RepositoryCollection<Member> get() = RepositoryCollection(state.members)
    override val history: RepositoryCollection<HistoryEvent> get() = RepositoryCollection(state.history)
    override val shop: RepositoryCollection<ShopItem> get() = RepositoryCollection(state.shop)
    override val missions: RepositoryCollection<Mission> get() = RepositoryCollection(state.missions)
    override val missionCompletions: RepositoryCollection<MissionCompletion> get() = RepositoryCollection(state.missionCompletions)
    override val missionRequests: RepositoryCollection<MissionRequest> get() = RepositoryCollection(state.missionRequests)
    override val missionNotices: RepositoryCollection<MissionNotice> get() = RepositoryCollection(state.missionNotices)
    override val sessions: RepositoryCollection<Session> get() = RepositoryCollection(state.sessions)
    override val attendance: RepositoryCollection<Attendance> get() = RepositoryCollection(state.attendance)
    override val assets: RepositoryCollection<CustomAsset> get() = RepositoryCollection(state.assets)
    override val groups: RepositoryCollection<Group> get() = RepositoryCollection(state.groups)
    override val archived: RepositoryCollection<ArchivedMember> get() = RepositoryCollection(state.archived)
    override val assistantAlerts: RepositoryCollection<AssistantAlert> get() = RepositoryCollection(state.assistantAlerts)
    override val assistantFollowUps: RepositoryCollection<AssistantFollowUp> get() = RepositoryCollection(state.assistantFollowUps)
    override fun setRingState(value: RingAccount?) { ring = value }
    override fun setWheelState(value: WheelConfig) { wheel = value }

    private val assistantDomain by lazy(LazyThreadSafetyMode.NONE) { AssistantRepository(this, ::newId) }

    override var wheel: WheelConfig
        get() = state.wheel
        internal set(value) { state.wheel = value }

    private val bootstrapper by lazy(LazyThreadSafetyMode.NONE) { CatalogBootstrapper(this) }
    private val memberDomain by lazy(LazyThreadSafetyMode.NONE) { MemberRepository(this, ::newId) }
    private val economyDomain by lazy(LazyThreadSafetyMode.NONE) { EconomyRepository(this) }
    private val wheelDomain by lazy(LazyThreadSafetyMode.NONE) { WheelRepository(this) }
    private val missionDomain by lazy(LazyThreadSafetyMode.NONE) { MissionRepository(this, ::newId) }
    private val sessionDomain by lazy(LazyThreadSafetyMode.NONE) { SessionRepository(this, ::newId) }
    private val catalogDomain by lazy(LazyThreadSafetyMode.NONE) { CatalogRepository(this) }

    init {
        persistence.load()?.let { loaded ->
            ring = loaded.ring
            members += loaded.members
            history += HistoryEvents.dedupeNewest(loaded.history)
            shop += loaded.shop
            wheel = loaded.wheel
            missions += loaded.missions
            missionCompletions += loaded.missionCompletions
            missionRequests += loaded.missionRequests
            missionNotices += loaded.missionNotices
            sessions += loaded.sessions
            attendance += loaded.attendance
            assets += loaded.assets
            groups += loaded.groups
            archived += loaded.archived
            assistantAlerts += loaded.assistantAlerts
            assistantFollowUps += loaded.assistantFollowUps
        }
    }

    fun setGroupLook(groupId: String, crestItemId: String?, cardItemId: String?) =
        bootstrapper.setGroupLook(groupId, crestItemId, cardItemId)

    fun createRing(name: String, username: String): RingAccount = memberDomain.createRing(name, username)
    fun updateRingProfile(name: String, username: String): RingAccount = memberDomain.updateRingProfile(name, username)
    fun addMember(name: String, birthDate: String? = null, responsibility: String = ""): Member =
        memberDomain.addMember(name, birthDate, responsibility)
    override fun processBirthdayRewards(now: Long): Int = memberDomain.processBirthdayRewards(now)
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
    override fun updateMember(member: Member) = memberDomain.updateMember(member)
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

    override fun recordXp(memberId: String, amount: Int, reason: String, actor: String?) =
        economyDomain.recordXp(memberId, amount, reason, actor)
    override fun recordPoints(memberId: String, amount: Int, reason: String, actor: String?) =
        economyDomain.recordPoints(memberId, amount, reason, actor)
    override fun recordDiamonds(memberId: String, amount: Int, reason: String, actor: String?) =
        economyDomain.recordDiamonds(memberId, amount, reason, actor)
    fun decreaseXp(memberId: String, amount: Int, reason: String, actor: String? = null) =
        economyDomain.decreaseXp(memberId, amount, reason, actor)
    override fun spendPoints(memberId: String, amount: Int, reason: String, actor: String?) =
        economyDomain.spendPoints(memberId, amount, reason, actor)
    fun decreaseLevel(memberId: String, targetLevel: Int, reason: String, actor: String? = null) =
        economyDomain.decreaseLevel(memberId, targetLevel, reason, actor)

    fun updateWheelItem(item: WheelItem) = wheelDomain.updateWheelItem(item)
    fun deleteWheelItem(itemId: String) = wheelDomain.deleteWheelItem(itemId)
    fun setWheel(config: WheelConfig) = wheelDomain.setWheel(config)

    fun updateMission(mission: Mission) = missionDomain.updateMission(mission)
    fun deleteMission(missionId: String) = missionDomain.deleteMission(missionId)
    fun addMission(mission: Mission) = missionDomain.addMission(mission)
    fun hiddenReceivedIds(missionId: String): Set<String> = missionDomain.hiddenReceivedIds(missionId)
    fun hiddenReceivedAt(memberId: String, missionId: String): Long? = missionDomain.hiddenReceivedAt(memberId, missionId)
    override fun seedDefaultHiddenMissions() = missionDomain.seedDefaultHiddenMissions()
    fun completeMission(mission: Mission, memberIds: List<String>, reason: String? = null): MissionCompletion =
        missionDomain.completeMission(mission, memberIds, reason)
    fun requestMission(memberId: String, missionId: String, message: String = ""): MissionRequest =
        missionDomain.requestMission(memberId, missionId, message)
    fun reviewMissionRequest(requestId: String, approve: Boolean, mentorNote: String = ""): MissionRequest =
        missionDomain.reviewMissionRequest(requestId, approve, mentorNote)
    fun markMissionNoticesRead() = missionDomain.markMissionNoticesRead()

    fun addSession(memberId: String, title: String, topic: String = "", location: String? = null): Session =
        sessionDomain.addSession(memberId, title, topic, location)
    fun updateSession(session: Session) = sessionDomain.updateSession(session)
    fun deleteSession(sessionId: String) = sessionDomain.deleteSession(sessionId)
    fun addSession(session: Session) = sessionDomain.addSession(session)
    fun recordAttendance(item: Attendance) = sessionDomain.recordAttendance(item)
    fun recordWeeklyAttendance(memberId: String, dateEpochMillis: Long, status: AttendanceStatus, note: String? = null) =
        sessionDomain.recordWeeklyAttendance(memberId, dateEpochMillis, status, note)

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

    fun addAssistantFollowUp(memberId: String, title: String, description: String, dueAtEpochMillis: Long,
                            sourceType: AssistantFollowUpSource = AssistantFollowUpSource.MANUAL,
                            sourceReference: String? = null): AssistantFollowUp =
        assistantDomain.addFollowUp(memberId, title, description, dueAtEpochMillis, sourceType, sourceReference)
    fun updateAssistantFollowUp(item: AssistantFollowUp) = assistantDomain.updateFollowUp(item)
    fun completeAssistantFollowUp(id: String, completedAtEpochMillis: Long = System.currentTimeMillis()) =
        assistantDomain.completeFollowUp(id, completedAtEpochMillis)
    fun deleteAssistantFollowUp(id: String) = assistantDomain.deleteFollowUp(id)
    fun upsertAssistantAlert(alert: AssistantAlert) = assistantDomain.upsertAlert(alert)
    fun markAssistantAlertDone(id: String, completedAtEpochMillis: Long = System.currentTimeMillis()) =
        assistantDomain.markAlertDone(id, completedAtEpochMillis)
    override fun evaluateAssistantAlerts(now: Long) = assistantDomain.evaluate(now)

    override fun persist() {
        normaliseHistoryIds()
        persistence.save(
            ring, members, history, shop, wheel, missions, missionCompletions,
            sessions, attendance, assets, groups, archived, missionRequests, missionNotices,
            assistantAlerts, assistantFollowUps
        )
        statePublisher.publish(state)
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
        assistantAlerts.clear()
        assistantFollowUps.clear()
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
            assistantAlerts += state.assistantAlerts
            assistantFollowUps += state.assistantFollowUps
        }
        statePublisher.publish(state)
    }
}