package com.kichikan.ak1.data

import com.kichikan.ak1.domain.assistant.*
import java.time.ZoneId
import com.kichikan.ak1.domain.calendar.BirthdayRules
import com.kichikan.ak1.domain.calendar.JalaliCalendar

/** Durable CRUD boundary for assistant alerts and mentor follow-ups. */
internal class AssistantRepository(private val app: AssistantRepositoryPort, private val newId: (String) -> String) {
    fun evaluate(now: Long = System.currentTimeMillis()) {
        val zone = ZoneId.systemDefault()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        app.members.forEach { member ->
            val birth = BirthdayRules.parseStored(member.birthDate)
            if (birth != null) {
                var next = BirthdayRules.birthdayInYear(birth, JalaliCalendar.fromGregorian(today).year)
                var birthdayDate = JalaliCalendar.toGregorian(next)
                if (birthdayDate.isBefore(today)) {
                    next = BirthdayRules.birthdayInYear(birth, next.year + 1)
                    birthdayDate = JalaliCalendar.toGregorian(next)
                }
                val reference = "birthday:" + member.id + ":" + next.year
                if (app.assistantFollowUps.none { it.sourceReference == reference }) {
                    val due = birthdayDate.minusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
                    app.assistantFollowUps += AssistantFollowUp(
                        id = newId("birthday-follow-up"), memberId = member.id,
                        title = "یادآوری تولد " + member.name, description = "سه روز تا تولد عضو باقی مانده است.",
                        dueAtEpochMillis = due, createdAtEpochMillis = now,
                        sourceType = AssistantFollowUpSource.BIRTHDAY, sourceReference = reference
                    )
                }
            }
        }
        val snapshot = AssistantAlertEngine.Input(
            app.members.toList(), app.history.toList(), app.sessions.toList(), app.missionCompletions.toList(),
            app.missionRequests.toList(), app.assistantFollowUps.toList(), app.assistantAlerts.toList(),
            AssistantDataProvider.growthLastAction(app.appContext, app.members.map { it.id }.toSet())
        )
        val evaluated = AssistantAlertEngine.evaluate(snapshot, now, zone)
        if (evaluated != app.assistantAlerts) {
            app.assistantAlerts.clear(); app.assistantAlerts.addAll(evaluated); app.persist()
        }
    }

    fun upsertAlert(alert: AssistantAlert) {
        require(app.members.any { it.id == alert.memberId } || app.archived.any { it.member.id == alert.memberId }) { "عضو پیدا نشد" }
        val byId = app.assistantAlerts.indexOfFirst { it.id == alert.id }
        if (byId >= 0) app.assistantAlerts[byId] = alert else {
            val openIndex = app.assistantAlerts.indexOfFirst {
                it.memberId == alert.memberId && it.ruleKey == alert.ruleKey &&
                    it.status == AssistantRecordStatus.OPEN && alert.status == AssistantRecordStatus.OPEN
            }
            if (openIndex >= 0) app.assistantAlerts[openIndex] = alert else app.assistantAlerts += alert
        }
        app.persist()
    }

    fun addFollowUp(memberId: String, title: String, description: String, dueAtEpochMillis: Long,
                    sourceType: AssistantFollowUpSource, sourceReference: String?): AssistantFollowUp {
        require(app.members.any { it.id == memberId } || app.archived.any { it.member.id == memberId }) { "عضو پیدا نشد" }
        require(title.isNotBlank()) { "عنوان پیگیری الزامی است" }
        require(dueAtEpochMillis > 0L) { "زمان سررسید معتبر نیست" }
        val item = AssistantFollowUp(newId("follow-up"), memberId, title.trim(), description.trim(),
            dueAtEpochMillis, createdAtEpochMillis = System.currentTimeMillis(),
            sourceType = sourceType, sourceReference = sourceReference)
        app.assistantFollowUps += item
        app.persist()
        return item
    }

    fun updateFollowUp(item: AssistantFollowUp) {
        require(item.title.isNotBlank()) { "عنوان پیگیری الزامی است" }
        val index = app.assistantFollowUps.indexOfFirst { it.id == item.id }
        require(index >= 0) { "پیگیری پیدا نشد" }
        app.assistantFollowUps[index] = item.copy(title = item.title.trim(), description = item.description.trim())
        app.persist()
    }

    fun completeFollowUp(id: String, completedAtEpochMillis: Long) {
        val index = app.assistantFollowUps.indexOfFirst { it.id == id }
        require(index >= 0) { "پیگیری پیدا نشد" }
        app.assistantFollowUps[index] = app.assistantFollowUps[index].copy(
            status = AssistantRecordStatus.DONE, completedAtEpochMillis = completedAtEpochMillis)
        app.persist()
    }

    fun deleteFollowUp(id: String) {
        app.assistantFollowUps.removeAll { it.id == id }
        app.persist()
    }

    fun markAlertDone(id: String, completedAtEpochMillis: Long) {
        val index = app.assistantAlerts.indexOfFirst { it.id == id }
        require(index >= 0) { "هشدار پیدا نشد" }
        app.assistantAlerts[index] = app.assistantAlerts[index].copy(
            status = AssistantRecordStatus.DONE, completedAtEpochMillis = completedAtEpochMillis)
        app.persist()
    }
}
