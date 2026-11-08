package com.kichikan.ak1.data

import com.kichikan.ak1.domain.model.*
import java.time.Instant
import java.time.ZoneId

/** Owns session lifecycle and attendance records. */
internal class SessionRepository(
    private val app: AppRepository,
    private val newId: (String) -> String
) {
    fun addSession(memberId: String, title: String, topic: String = "", location: String? = null): Session {
        require(app.members.any { it.id == memberId }) { "عضو نامعتبر است" }
        require(title.isNotBlank()) { "عنوان جلسه الزامی است" }
        val session = Session(newId("session"), memberId, title.trim(), topic.trim(), System.currentTimeMillis(), location?.trim()?.takeIf { it.isNotEmpty() })
        app.sessions += session
        app.persist()
        return session
    }

    fun updateSession(session: Session) { val i = app.sessions.indexOfFirst { it.id == session.id }; if (i >= 0) { app.sessions[i] = session; app.persist() } }
    fun deleteSession(sessionId: String) { app.sessions.removeAll { it.id == sessionId }; app.attendance.removeAll { it.sessionId == sessionId }; app.persist() }

    fun addSession(session: Session) {
        app.sessions.removeAll { it.id == session.id }
        app.sessions += session
        app.persist()
    }

    fun recordAttendance(item: Attendance) {
        check(app.members.any { it.id == item.memberId }) { "عضو نامعتبر است" }
        require(
            item.status == AttendanceStatus.PRESENT ||
                item.status == AttendanceStatus.LATE ||
                item.status == AttendanceStatus.UNMARKED ||
                !item.note.isNullOrBlank()
        ) { "برای ثبت غیبت، وارد کردن دلیل الزامی است" }
        app.attendance.removeAll { it.memberId == item.memberId && it.dateEpochMillis == item.dateEpochMillis }
        app.attendance += item
        // One ledger row per member and class day: re-marking the same day updates that row instead
        // of appending a second event with the same id, which used to break the app.history list.
        val eventId = "app.attendance-${item.memberId}-${item.dateEpochMillis}"
        app.history.removeAll { it.id == eventId || it.id == "app.attendance-" + item.id }
        app.history += HistoryEvent(eventId, item.memberId, HistoryType.ATTENDANCE, null,
            "حضور و غیاب", item.note, item.createdAt, "mentor",
            mapOf("status" to item.status.name, "date" to item.dateEpochMillis.toString()))
        app.persist()
    }

    fun recordWeeklyAttendance(memberId: String, dateEpochMillis: Long, status: AttendanceStatus, note: String? = null) {
        val zone = ZoneId.systemDefault()
        val targetDate = Instant.ofEpochMilli(dateEpochMillis).atZone(zone).toLocalDate()
        app.attendance.removeAll { item ->
            item.memberId == memberId && item.sessionId == null &&
                Instant.ofEpochMilli(item.dateEpochMillis).atZone(zone).toLocalDate() == targetDate
        }
        recordAttendance(Attendance("app.attendance-$memberId-$dateEpochMillis", memberId, dateEpochMillis, status, null, note, System.currentTimeMillis()))
    }
}
