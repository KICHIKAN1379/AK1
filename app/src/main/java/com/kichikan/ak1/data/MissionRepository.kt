package com.kichikan.ak1.data

import android.content.Context
import com.kichikan.ak1.domain.model.*
import com.kichikan.ak1.domain.service.MissionService
import com.kichikan.ak1.notification.MissionNotifications

/** Owns mission definitions, completion, hidden-mission rules and request/notice workflow. */
internal class MissionRepository(
    private val app: AppRepository,
    private val newId: (String) -> String
) {
    fun updateMission(mission: Mission) { val i = app.missions.indexOfFirst { it.id == mission.id }; if (i >= 0) { app.missions[i] = mission; app.persist() } }
    fun deleteMission(missionId: String) { app.missions.removeAll { it.id == missionId }; app.missionCompletions.removeAll { it.missionId == missionId }; app.persist() }

    fun addMission(mission: Mission) {
        require(mission.xpReward >= 0 && mission.pointsReward >= 0 && mission.diamondReward >= 0)
        app.missions.removeAll { it.id == mission.id }
        app.missions += mission
        app.persist()
    }

    /** Members who already received this hidden mission. */
    fun hiddenReceivedIds(missionId: String): Set<String> =
        app.missionCompletions.filter { it.missionId == missionId }.flatMap { it.memberIds }.toSet()

    /** Date (epoch millis) a member received a hidden mission, or null. */
    fun hiddenReceivedAt(memberId: String, missionId: String): Long? =
        app.missionCompletions.filter { it.missionId == missionId && memberId in it.memberIds }.maxOfOrNull { it.completedAt }

    /** Default hidden app.missions, added once; the mentor can edit or delete them. They carry no points. */
    fun seedDefaultHiddenMissions() {
        val prefs = app.appContext.getSharedPreferences("ak1_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("hidden_missions_seeded", false)) return
        val defaults = listOf(
            "اولین کمک به تازه‌وارد" to "اولین بار به عضو جدید در کاری کمک کند.",
            "سکوت بی‌یادآوری" to "در یک جلسه، بدون یادآوری مربی، سکوت را رعایت کند.",
            "خلاصه برای دوست" to "یک کتاب را خلاصه کند و برای یکی از دوستانش توضیح دهد.",
            "زودتر از همه" to "در یک هفته، سه بار قبل از شروع جلسه حاضر شود.",
            "همدلی کوچک" to "کسی را که ناراحت است پیدا کند و یک کار کوچک برای او انجام دهد."
        )
        defaults.forEach { (title, desc) ->
            app.missions += Mission("hidden-" + title.hashCode().toString(16), title, desc, MissionType.INDIVIDUAL, hidden = true, active = true)
        }
        prefs.edit().putBoolean("hidden_missions_seeded", true).apply()
        app.persist()
    }

    fun completeMission(
        mission: Mission,
        memberIds: List<String>,
        reason: String? = null
    ): MissionCompletion {
        check(MissionService.canComplete(mission, System.currentTimeMillis())) {
            "این مأموریت در حال حاضر قابل ثبت نیست"
        }

        // A hidden mission is received only once per member.
        val alreadyReceived = if (mission.hidden) hiddenReceivedIds(mission.id) else emptySet()
        val validIds = memberIds.distinct().filter { it !in alreadyReceived }
        check(validIds.isNotEmpty()) { if (alreadyReceived.isNotEmpty()) "این مأموریت مخفی قبلاً برای این عضو ثبت شده است" else "حداقل یک عضو لازم است" }
        validIds.forEach { id ->
            check(app.members.any { it.id == id }) { "عضو نامعتبر است" }
        }

        val completion = MissionService.completion(
            mission,
            validIds,
            System.currentTimeMillis(),
            reason
        )
        app.missionCompletions += completion

        validIds.forEach { memberId ->
            if (mission.xpReward > 0) {
                app.recordXp(memberId, mission.xpReward, "ماموریت: " + mission.title, "mentor")
            }
            if (mission.pointsReward > 0) {
                app.recordPoints(memberId, mission.pointsReward, "ماموریت: " + mission.title, "mentor")
            }
            if (mission.diamondReward > 0) {
                app.recordDiamonds(memberId, mission.diamondReward, "ماموریت: " + mission.title, "mentor")
            }

            app.history += HistoryEvent(
                "mission-" + completion.id + "-" + memberId,
                memberId,
                HistoryType.MISSION_COMPLETED,
                null,
                mission.title,
                reason,
                completion.completedAt,
                "mentor"
            )
        }

        app.persist()
        return completion
    }


    fun requestMission(memberId: String, missionId: String, message: String = ""): MissionRequest {
        require(app.members.any { it.id == memberId }) { "عضو نامعتبر است" }
        val mission = app.missions.firstOrNull { it.id == missionId && !it.hidden } ?: error("مأموریت پیدا نشد")
        require(MissionService.canComplete(mission, System.currentTimeMillis())) { "این مأموریت اکنون فعال نیست" }
        require(mission.type == MissionType.INDIVIDUAL) { "درخواست فردی برای مأموریت گروهی پشتیبانی نمی‌شود" }
        require(app.missionRequests.none { it.missionId == missionId && it.memberId == memberId && it.status == MissionRequestStatus.PENDING }) { "برای این مأموریت درخواست در انتظار بررسی دارید" }
        require(app.missionCompletions.none { it.missionId == missionId && memberId in it.memberIds }) { "این مأموریت قبلاً برای شما ثبت شده است" }
        val now = System.currentTimeMillis()
        val memberName = app.members.first { it.id == memberId }.name
        val request = MissionRequest(newId("mission-request"), missionId, memberId, message.trim(), now)
        app.missionRequests += request
        val noticeText = "$memberName برای «${mission.title}» درخواست ثبت انجام فرستاد."
        addMissionNotice("درخواست تازه مأموریت", noticeText, request.id)
        app.persist()
        MissionNotifications.post(app.appContext, "درخواست تازه مأموریت", noticeText, request.id.hashCode())
        return request
    }

    fun reviewMissionRequest(requestId: String, approve: Boolean, mentorNote: String = ""): MissionRequest {
        val index = app.missionRequests.indexOfFirst { it.id == requestId }
        require(index >= 0) { "درخواست پیدا نشد" }
        val request = app.missionRequests[index]
        require(request.status == MissionRequestStatus.PENDING) { "این درخواست قبلاً بررسی شده است" }
        val mission = app.missions.firstOrNull { it.id == request.missionId } ?: error("مأموریت حذف شده است")
        val member = app.members.firstOrNull { it.id == request.memberId } ?: error("عضو پیدا نشد")
        if (approve) {
            require(app.missionCompletions.none { it.missionId == mission.id && request.memberId in it.memberIds }) {
                "این مأموریت پس از ثبت درخواست، قبلاً برای این عضو ثبت شده است؛ از ثبت پاداش تکراری جلوگیری شد."
            }
            completeMission(mission, listOf(request.memberId), mentorNote.ifBlank { "درخواست عضو تأیید شد" })
        }
        val reviewed = request.copy(
            status = if (approve) MissionRequestStatus.APPROVED else MissionRequestStatus.REJECTED,
            reviewedAt = System.currentTimeMillis(), mentorNote = mentorNote.trim().takeIf { it.isNotEmpty() }
        )
        app.missionRequests[index] = reviewed
        val title = if (approve) "درخواست مأموریت تأیید شد" else "درخواست مأموریت رد شد"
        val message = "درخواست «${mission.title}» برای ${member.name} ${if (approve) "تأیید" else "رد"} شد."
        addMissionNotice(title, message, request.id)
        app.persist()
        MissionNotifications.post(app.appContext, title, message, request.id.hashCode() + 1)
        return reviewed
    }

    private fun addMissionNotice(title: String, message: String, requestId: String?) {
        app.missionNotices.add(0, MissionNotice(newId("mission-notice"), title, message, System.currentTimeMillis(), requestId))
        while (app.missionNotices.size > 100) app.missionNotices.removeAt(app.missionNotices.lastIndex)
    }

    fun markMissionNoticesRead() {
        app.missionNotices.indices.forEach { i -> if (!app.missionNotices[i].read) app.missionNotices[i] = app.missionNotices[i].copy(read = true) }
        app.persist()
    }
}
