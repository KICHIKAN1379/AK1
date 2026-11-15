package com.kichikan.ak1.domain.assistant

import com.kichikan.ak1.domain.model.*
import java.time.Instant
import java.time.ZoneId

/** Deterministic, read-only answers built only from persisted AK1 records. */
object AssistantQueryEngine {
    enum class Query(val title: String, val description: String) {
        SESSIONS_DUE("با چه اعضایی بیش از سه ماه جلسه نداشته‌ایم؟", "جلسه فردی ثبت‌شده در سه ماه تقویمی اخیر"),
        XP_DECLINE("روند XP کدام اعضا افت کرده است؟", "مقایسه دو بازه متوالی ۱۴روزه؛ فقط با داده کافی در بازه قبل"),
        MISSION_ACTIVITY("در ۳۰ روز اخیر چه کسانی مأموریت بیشتری تکمیل کرده‌اند؟", "بر اساس رکوردهای واقعی تکمیل مأموریت؛ بدون فرض مأموریت تخصیص‌یافته"),
        ATTENDANCE("وضعیت حضور اعضا در پنج جلسه اخیر چگونه بوده؟", "فقط اعضایی که حداقل یک رکورد حضور دارند"),
        READING("در ۳۰ روز اخیر چه میزان کتاب‌خوانی ثبت شده؟", "بر اساس کتاب‌های تکمیل‌شده و صفحات ثبت‌شده"),
        OPEN_ALERTS("کدام اعضا هشدار باز دارند؟", "هشدارهایی که مربی هنوز «انجام شد» نزده است"),
        OVERDUE_FOLLOW_UPS("کدام پیگیری‌ها از موعدشان گذشته‌اند؟", "پیگیری‌های باز با سررسید قبل از زمان اجرا"),
        XP_LEADERS("بیشترین XP در ۳۰ روز اخیر برای چه کسانی ثبت شده؟", "جمع XP مثبت ثبت‌شده در ۳۰ روز اخیر")
    }

    data class Row(val memberId: String, val memberName: String, val detail: String,
                   val value: String, val sortValue: Long = 0L)
    data class Result(val query: Query, val generatedAtEpochMillis: Long, val summary: String,
                      val rows: List<Row>, val eligibleMemberCount: Int,
                      val excludedDuringInitialMonitoring: Int)

    fun execute(query: Query, members: List<Member>, history: List<HistoryEvent>,
        sessions: List<Session>, completions: List<MissionCompletion>, attendance: List<Attendance>,
        alerts: List<AssistantAlert>, followUps: List<AssistantFollowUp>,
        now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Result {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val locked = members.filter { isMonitoringLocked(it, now) }
        val eligible = members.filterNot { isMonitoringLocked(it, now) }
        val day14 = today.minusDays(14).atStartOfDay(zone).toInstant().toEpochMilli()
        val day28 = today.minusDays(28).atStartOfDay(zone).toInstant().toEpochMilli()
        val day30 = today.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
        val threeMonths = today.minusMonths(3).atStartOfDay(zone).toInstant().toEpochMilli()
        val rows = when (query) {
            Query.SESSIONS_DUE -> eligible.mapNotNull { member ->
                val latest = sessions.filter { it.memberId == member.id }.maxOfOrNull { it.startsAt }
                if (latest == null || latest < threeMonths) {
                    val days = latest?.let { (now - it).coerceAtLeast(0L) / 86_400_000L }
                    Row(member.id, member.name,
                        if (latest == null) "هیچ جلسه فردی ثبت نشده است" else "آخرین جلسه: " + days + " روز پیش",
                        days?.let { it.toString() + " روز" } ?: "بدون سابقه", days ?: Long.MAX_VALUE)
                } else null
            }.sortedByDescending { it.sortValue }

            Query.XP_DECLINE -> eligible.mapNotNull { member ->
                val events = history.filter { it.memberId == member.id && it.type == HistoryType.XP_EARNED }
                val previous = events.filter { it.createdAtEpochMillis in day28 until day14 }.sumOf { it.amount ?: 0 }
                val recent = events.filter { it.createdAtEpochMillis >= day14 && it.createdAtEpochMillis <= now }.sumOf { it.amount ?: 0 }
                if (previous >= 10 && recent * 2 < previous) {
                    val drop = ((previous - recent).toDouble() * 100.0 / previous).toInt()
                    Row(member.id, member.name, "XP بازه قبل: " + previous + " • بازه اخیر: " + recent,
                        drop.toString() + "٪ کاهش", drop.toLong())
                } else null
            }.sortedByDescending { it.sortValue }

            Query.MISSION_ACTIVITY -> eligible.mapNotNull { member ->
                val count = completions.count { member.id in it.memberIds && it.completedAt in day30..now }
                if (count > 0) Row(member.id, member.name, "تکمیل مأموریت ثبت‌شده در ۳۰ روز اخیر",
                    count.toString() + " مأموریت", count.toLong()) else null
            }.sortedByDescending { it.sortValue }

            Query.ATTENDANCE -> eligible.mapNotNull { member ->
                val recent = attendance.filter { it.memberId == member.id && it.status != AttendanceStatus.UNMARKED }
                    .sortedByDescending { it.dateEpochMillis }.take(5)
                if (recent.isEmpty()) null else {
                    val present = recent.count { it.status == AttendanceStatus.PRESENT }
                    val late = recent.count { it.status == AttendanceStatus.LATE }
                    val absent = recent.count { it.status == AttendanceStatus.ABSENT }
                    Row(member.id, member.name, "از " + recent.size + " رکورد اخیر: حاضر " + present + " • تأخیر " + late + " • غیبت " + absent,
                        present.toString() + "/" + recent.size + " حضور", present.toLong() * 100L / recent.size)
                }
            }.sortedBy { it.sortValue }

            Query.READING -> eligible.mapNotNull { member ->
                val books = member.readingBooks.filter { it.completedAtEpochMillis in day30..now }
                val pages = books.sumOf { it.pages }
                if (books.isNotEmpty()) Row(member.id, member.name,
                    books.size.toString() + " کتاب تکمیل‌شده در ۳۰ روز اخیر", pages.toString() + " صفحه", pages.toLong()) else null
            }.sortedByDescending { it.sortValue }

            Query.OPEN_ALERTS -> eligible.mapNotNull { member ->
                val memberAlerts = alerts.filter { it.memberId == member.id && it.status == AssistantRecordStatus.OPEN }
                if (memberAlerts.isNotEmpty()) Row(member.id, member.name,
                    memberAlerts.groupingBy { it.tab }.eachCount().entries.joinToString(" • ") { it.key.label() + ": " + it.value },
                    memberAlerts.size.toString() + " هشدار", memberAlerts.size.toLong()) else null
            }.sortedByDescending { it.sortValue }

            Query.OVERDUE_FOLLOW_UPS -> eligible.flatMap { member ->
                followUps.filter { it.memberId == member.id && it.status == AssistantRecordStatus.OPEN && it.dueAtEpochMillis < now }
                    .map { item ->
                        val days = ((now - item.dueAtEpochMillis) / 86_400_000L).coerceAtLeast(0L)
                        Row(member.id, member.name, item.title + " • " + days + " روز از سررسید گذشته",
                            "پیگیری عقب‌افتاده", days)
                    }
            }.sortedByDescending { it.sortValue }

            Query.XP_LEADERS -> eligible.mapNotNull { member ->
                val total = history.filter {
                    it.memberId == member.id && it.type == HistoryType.XP_EARNED &&
                        it.createdAtEpochMillis in day30..now
                }.sumOf { it.amount ?: 0 }
                if (total > 0) Row(member.id, member.name, "XP مثبت ثبت‌شده در ۳۰ روز اخیر",
                    total.toString() + " XP", total.toLong()) else null
            }.sortedByDescending { it.sortValue }
        }
        val summary = when {
            eligible.isEmpty() -> "هیچ عضوی هنوز از دوره شروع ۳۰روزه عبور نکرده است."
            rows.isEmpty() -> "با داده‌های ثبت‌شده، موردی برای این پرسش پیدا نشد."
            else -> rows.size.toString() + " نتیجه از میان " + eligible.size + " عضوِ خارج از دوره شروع."
        }
        return Result(query, now, summary, rows, eligible.size, locked.size)
    }

    private fun isMonitoringLocked(member: Member, now: Long): Boolean {
        val start = member.monitoringStartAtEpochMillis ?: member.registeredAtEpochMillis
        return start <= 0L || now < start + 30L * 86_400_000L
    }

    private fun AssistantAlertTab.label(): String = when (this) {
        AssistantAlertTab.MEMBER_ACTIVITY -> "فعالیت"
        AssistantAlertTab.XP_TREND -> "XP"
        AssistantAlertTab.MISSION -> "مأموریت"
        AssistantAlertTab.GROWTH_MAP -> "نقشه کمال"
        AssistantAlertTab.SESSIONS -> "جلسات"
        AssistantAlertTab.READING -> "کتاب‌خوانی"
        AssistantAlertTab.FOLLOW_UP -> "پیگیری"
    }
}
