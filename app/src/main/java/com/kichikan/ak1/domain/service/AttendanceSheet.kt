package com.kichikan.ak1.domain.service

import com.kichikan.ak1.domain.calendar.JalaliCalendar
import com.kichikan.ak1.domain.calendar.JalaliDate
import com.kichikan.ak1.domain.model.Attendance
import com.kichikan.ak1.domain.model.AttendanceStatus
import com.kichikan.ak1.domain.model.Member
import java.time.Instant
import java.time.ZoneId

/** The monthly attendance table (members × Jalali days) used by the Excel export. */
object AttendanceSheet {
    fun label(status: AttendanceStatus?): String = when (status) {
        AttendanceStatus.PRESENT -> "حاضر"
        AttendanceStatus.LATE -> "تأخیر"
        AttendanceStatus.ABSENT -> "غایب"
        AttendanceStatus.EXCUSED -> "غیبت موجه"
        else -> ""
    }

    fun rows(members: List<Member>, attendance: List<Attendance>, year: Int, month: Int, zone: ZoneId): List<List<String>> {
        val days = (1..JalaliCalendar.daysInMonth(year, month)).map { JalaliCalendar.toGregorian(JalaliDate(year, month, it)) }
        val header = listOf("عضو") + days.indices.map { (it + 1).toString() } + listOf("حاضر", "تأخیر", "غایب", "غیبت موجه")
        val byMemberDay = attendance.filter { it.sessionId == null }
            .groupBy { it.memberId to Instant.ofEpochMilli(it.dateEpochMillis).atZone(zone).toLocalDate() }
        val body = members.map { member ->
            val records = days.map { day -> byMemberDay[member.id to day]?.maxByOrNull { it.createdAt } }
            val cells = records.map { r ->
                val text = label(r?.status)
                if (text.isNotEmpty() && !r?.note.isNullOrBlank()) "$text — ${r?.note}" else text
            }
            val counts = listOf(AttendanceStatus.PRESENT, AttendanceStatus.LATE, AttendanceStatus.ABSENT, AttendanceStatus.EXCUSED)
                .map { s -> records.count { it?.status == s }.toString() }
            listOf(member.name) + cells + counts
        }
        return listOf(header) + body
    }
}
