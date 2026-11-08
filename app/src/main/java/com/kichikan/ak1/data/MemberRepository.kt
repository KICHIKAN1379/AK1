package com.kichikan.ak1.data

import com.kichikan.ak1.domain.calendar.BirthdayRules
import com.kichikan.ak1.domain.calendar.JalaliCalendar
import com.kichikan.ak1.domain.model.*
import com.kichikan.ak1.domain.service.EconomyService
import com.kichikan.ak1.domain.model.ReadingRules
import java.time.Instant
import java.time.ZoneId

/**
 * Owns ring, member, group, archive and reading operations.
 *
 * AppRepository remains the compatibility facade for existing screens while these
 * operations are moved behind domain-specific boundaries.
 */
internal class MemberRepository(
    private val app: AppRepository,
    private val newId: (String) -> String
) {
    fun createRing(name: String, username: String): RingAccount {
        require(name.isNotBlank()) { "نام حلقه الزامی است" }
        require(username.isNotBlank()) { "نام کاربری حلقه الزامی است" }
        check(app.ring == null) { "حلقه قبلاً ایجاد شده است" }

        val account = RingAccount(
            newId("app.ring"),
            name.trim(),
            username.trim(),
            false
        )
        app.ring = account
        app.persist()
        return account
    }

    fun addMember(name: String, birthDate: String? = null, responsibility: String = ""): Member {
        check(app.ring != null) { "ابتدا حلقه را ایجاد کنید" }
        require(name.isNotBlank()) { "نام عضو الزامی است" }
        val normalizedBirthDate = birthDate?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { BirthdayRules.normalize(it, java.time.LocalDate.now()) }

        val member = Member(
            newId("member"), app.ring!!.ringId, name.trim(), birthDate = normalizedBirthDate,
            responsibility = responsibility.trim()
        )
        app.members += member
        app.persist()
        return member
    }

    /**
     * Processes the annual 30-diamond birthday reward exactly once per Jalali year.
     * Birthdays are Jalali dates (see [BirthdayRules]).
     */
    fun processBirthdayRewards(now: Long = System.currentTimeMillis()): Int {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val jalaliYear = JalaliCalendar.fromGregorian(today).year
        var granted = 0
        app.members.forEach { member ->
            val birth = BirthdayRules.parseStored(member.birthDate) ?: return@forEach
            if (!BirthdayRules.isBirthday(birth, today)) return@forEach
            // Judged by the event date, so rewards recorded by older versions still count.
            val alreadyGranted = app.history.any {
                it.memberId == member.id && it.type == HistoryType.BIRTHDAY_REWARD &&
                    JalaliCalendar.fromEpochMillis(it.createdAtEpochMillis, zone).year == jalaliYear
            }
            if (alreadyGranted) return@forEach
            app.recordDiamonds(member.id, 30, "پاداش تولد سال $jalaliYear", "system")
            app.history += HistoryEvent(
                id = "birthday-${member.id}-$jalaliYear", memberId = member.id,
                type = HistoryType.BIRTHDAY_REWARD, amount = 30,
                title = "پاداش تولد: ۳۰ الماس", reason = "روز تولد عضو",
                createdAtEpochMillis = now, createdBy = "system",
                metadata = mapOf("year" to jalaliYear.toString(), "calendar" to "jalali")
            )
            granted++
        }
        if (granted > 0) app.persist()
        return granted
    }

    fun addGroup(name: String): Group {
        require(app.ring != null) { "ابتدا حلقه را ایجاد کنید" }
        require(name.isNotBlank()) { "نام گروه الزامی است" }
        val group = Group(newId("group"), app.ring!!.ringId, name.trim())
        app.groups += group; app.persist(); return group
    }

    fun updateGroup(group: Group) {
        val i = app.groups.indexOfFirst { it.id == group.id }
        if (i >= 0) {
            val validIds = group.memberIds.filter { id -> app.members.any { it.id == id } }.distinct()
            app.groups[i] = group.copy(memberIds = validIds, leaderMemberId = group.leaderMemberId?.takeIf { id -> validIds.contains(id) })
            app.members.indices.forEach { index ->
                val member = app.members[index]
                if (validIds.contains(member.id)) app.members[index] = member.copy(groupId = group.id)
                else if (member.groupId == group.id) app.members[index] = member.copy(groupId = null)
            }
            app.groups.indices.forEach { gi ->
                if (app.groups[gi].id != group.id && app.groups[gi].memberIds.any { validIds.contains(it) }) {
                    app.groups[gi] = app.groups[gi].copy(memberIds = app.groups[gi].memberIds.filterNot { validIds.contains(it) },
                        leaderMemberId = app.groups[gi].leaderMemberId?.takeUnless { validIds.contains(it) })
                }
            }
            app.persist()
        }
    }
    fun deleteGroup(groupId: String) {
        app.groups.removeAll { it.id == groupId }
        app.members.indices.reversed().forEach { i -> if (app.members[i].groupId == groupId) app.members[i] = app.members[i].copy(groupId = null) }
        app.persist()
    }
    fun assignMemberToGroup(memberId: String, groupId: String?) {
        val i = app.members.indexOfFirst { it.id == memberId }
        if (i >= 0) {
            app.members[i] = app.members[i].copy(groupId = groupId)
            app.groups.indices.forEach { gi ->
                val g = app.groups[gi]
                app.groups[gi] = if (g.id == groupId) g.copy(memberIds = (g.memberIds + memberId).distinct())
                else g.copy(memberIds = g.memberIds - memberId, leaderMemberId = g.leaderMemberId?.takeUnless { it == memberId })
            }
            app.persist()
        }
    }

    fun adjustGroupScore(groupId: String, pointsDelta: Int, diamondsDelta: Int, reason: String) {
        require(reason.isNotBlank()) { "دلیل تغییر امتیاز گروه الزامی است" }
        val index = app.groups.indexOfFirst { it.id == groupId }
        require(index >= 0) { "گروه پیدا نشد" }
        val group = app.groups[index]
        val economy = group.economy
        require(economy.spendablePoints + pointsDelta >= 0) { "امتیاز گروه نمی‌تواند منفی شود" }
        require(economy.diamonds + diamondsDelta >= 0) { "الماس گروه نمی‌تواند منفی شود" }
        app.groups[index] = group.copy(economy = economy.copy(
            spendablePoints = economy.spendablePoints + pointsDelta,
            diamonds = economy.diamonds + diamondsDelta
        ))
        val now = System.currentTimeMillis()
        fun log(delta: Int, type: HistoryType, label: String) {
            if (delta != 0) app.history += HistoryEvent(
                id = "group-${now}-${groupId}-${type.name}", memberId = groupId, type = type,
                amount = kotlin.math.abs(delta), title = "${label} گروه ${group.name}",
                reason = reason, createdAtEpochMillis = now, createdBy = "mentor",
                metadata = mapOf("entityType" to "GROUP", "groupId" to groupId, "delta" to delta.toString())
            )
        }
        log(pointsDelta, if (pointsDelta >= 0) HistoryType.POINTS_EARNED else HistoryType.POINTS_DECREASED, "امتیاز")
        log(diamondsDelta, if (diamondsDelta >= 0) HistoryType.DIAMONDS_EARNED else HistoryType.DIAMONDS_DECREASED, "الماس")
        app.persist()
    }

    fun deleteMember(memberId: String) {
        app.members.removeAll { it.id == memberId }
        app.history.removeAll { it.memberId == memberId }
        app.attendance.removeAll { it.memberId == memberId }
        app.sessions.removeAll { it.memberId == memberId }
        app.missionCompletions.indices.reversed().forEach { index ->
            val completion = app.missionCompletions[index]
            val remainingMemberIds = completion.memberIds.filterNot { it == memberId }
            if (remainingMemberIds.isEmpty()) app.missionCompletions.removeAt(index)
            else if (remainingMemberIds.size != completion.memberIds.size) {
                app.missionCompletions[index] = completion.copy(memberIds = remainingMemberIds)
            }
        }
        app.groups.indices.forEach { index ->
            val group = app.groups[index]
            app.groups[index] = group.copy(
                memberIds = group.memberIds.filterNot { it == memberId },
                leaderMemberId = group.leaderMemberId?.takeUnless { it == memberId }
            )
        }
        app.persist()
    }

    /**
     * Removes a member from the app.ring but keeps the full record in [app.archived], together with their
     * app.history, app.attendance and app.sessions (those rows are keyed by member id and stay untouched).
     */
    fun archiveMember(memberId: String): Boolean {
        val member = app.members.firstOrNull { it.id == memberId } ?: return false
        app.members.removeAll { it.id == memberId }
        app.groups.indices.forEach { index ->
            val group = app.groups[index]
            app.groups[index] = group.copy(
                memberIds = group.memberIds.filterNot { it == memberId },
                leaderMemberId = group.leaderMemberId?.takeUnless { it == memberId }
            )
        }
        app.archived.removeAll { it.member.id == memberId }
        app.archived += ArchivedMember(member, System.currentTimeMillis())
        app.persist()
        return true
    }

    /** Brings an app.archived member back; a group that no longer exists is dropped from the record. */
    fun restoreArchivedMember(memberId: String): Boolean {
        val entry = app.archived.firstOrNull { it.member.id == memberId } ?: return false
        app.archived.removeAll { it.member.id == memberId }
        val existingGroup = entry.member.groupId?.takeIf { id -> app.groups.any { group -> group.id == id } }
        val restored = entry.member.copy(groupId = existingGroup)
        if (app.members.none { it.id == restored.id }) app.members += restored
        app.persist()
        return true
    }

    /** Deletes an app.archived member for good, including their app.history, app.attendance and app.sessions. */
    fun purgeArchivedMember(memberId: String): Boolean {
        val removed = app.archived.any { it.member.id == memberId }
        if (!removed) return false
        app.archived.removeAll { it.member.id == memberId }
        app.history.removeAll { it.memberId == memberId }
        app.attendance.removeAll { it.memberId == memberId }
        app.sessions.removeAll { it.memberId == memberId }
        app.persist()
        return true
    }

    fun updateMember(member: Member) {
        val index = app.members.indexOfFirst { it.id == member.id }
        if (index >= 0) {
            app.members[index] = member
            app.groups.indices.forEach { gi ->
                val group = app.groups[gi]
                app.groups[gi] = when {
                    group.id == member.groupId -> group.copy(memberIds = (group.memberIds + member.id).distinct())
                    else -> group.copy(memberIds = group.memberIds - member.id,
                        leaderMemberId = group.leaderMemberId?.takeUnless { it == member.id })
                }
            }
            app.persist()
        }
    }

    // ───────────────────────── reading / کتاب‌خوانی ─────────────────────────

    fun readingProgressFor(member: Member): ReadingProgress =
        ReadingRules.progressFor(member.readingBooks)

    fun readingProgressFor(memberId: String): ReadingProgress? =
        app.members.firstOrNull { it.id == memberId }?.let(::readingProgressFor)

    /**
     * Adds a finished-book record and immediately transfers the newly earned whole reading
     * points to the member's spendable balance. The fractional part remains visible in the
     * reading score until a later record completes it.
     */
    fun addReadingBook(
        memberId: String,
        title: String,
        dailyMinutes: Int,
        pages: Int,
        answers: Int,
        completedAtEpochMillis: Long = System.currentTimeMillis()
    ): Member {
        require(title.isNotBlank()) { "نام کتاب الزامی است" }
        require(pages > 0) { "تعداد صفحه باید بیشتر از صفر باشد" }
        require(dailyMinutes >= 0) { "مدت زمان مطالعه نمی‌تواند منفی باشد" }
        require(answers in 1..3) { "تعداد پاسخ باید بین ۱ تا ۳ باشد" }
        val member = app.members.firstOrNull { it.id == memberId } ?: error("عضو پیدا نشد")
        val book = ReadingBook(
            id = "reading-${java.util.UUID.randomUUID()}",
            title = title.trim(),
            dailyMinutes = dailyMinutes,
            pages = pages,
            answers = answers,
            completedAtEpochMillis = completedAtEpochMillis
        )
        return applyReadingBooks(member, member.readingBooks + book, "ثبت کتاب: ${book.title}")
    }

    fun updateReadingBook(memberId: String, book: ReadingBook): Member {
        require(book.title.isNotBlank()) { "نام کتاب الزامی است" }
        require(book.pages > 0) { "تعداد صفحه باید بیشتر از صفر باشد" }
        require(book.dailyMinutes >= 0) { "مدت زمان مطالعه نمی‌تواند منفی باشد" }
        require(book.answers in 1..3) { "تعداد پاسخ باید بین ۱ تا ۳ باشد" }
        val member = app.members.firstOrNull { it.id == memberId } ?: error("عضو پیدا نشد")
        require(member.readingBooks.any { it.id == book.id }) { "کتاب پیدا نشد" }
        val books = member.readingBooks.map { if (it.id == book.id) book else it }
        return applyReadingBooks(member, books, "ویرایش کتاب: ${book.title}")
    }

    fun deleteReadingBook(memberId: String, bookId: String): Member {
        val member = app.members.firstOrNull { it.id == memberId } ?: error("عضو پیدا نشد")
        val removed = member.readingBooks.firstOrNull { it.id == bookId } ?: error("کتاب پیدا نشد")
        val books = member.readingBooks.filterNot { it.id == bookId }
        return applyReadingBooks(member, books, "حذف کتاب: ${removed.title}")
    }

    /**
     * Recomputes levels and scores from the complete book list. Editing/deleting a book never
     * takes already-spent points away: only positive differences are transferred to economy.
     * This keeps the feature safe when a member has already spent the points.
     */
    private fun applyReadingBooks(member: Member, books: List<ReadingBook>, reason: String): Member {
        val index = app.members.indexOfFirst { it.id == member.id }
        if (index < 0) error("عضو پیدا نشد")
        val calculation = ReadingRules.recalculate(books)
        val dueWholePoints = calculation.progress.points.toInt()
        val delta = (dueWholePoints - member.readingAwardedPoints).coerceAtLeast(0)
        var economy = member.economy
        val events = mutableListOf<HistoryEvent>()
        if (delta > 0) {
            val change = EconomyService.adjustPoints(
                economy, delta, reason, "mentor", System.currentTimeMillis()
            )
            economy = change.economy
            events += change.events
        }
        val updated = member.copy(
            readingBooks = calculation.books,
            readingAwardedPoints = maxOf(member.readingAwardedPoints, dueWholePoints),
            economy = economy
        )
        app.history += events.map { it.copy(memberId = member.id) }
        app.history += HistoryEvent(
            id = "reading-${System.currentTimeMillis()}-${member.id}-${calculation.books.size}",
            memberId = member.id,
            type = HistoryType.READING_LOGGED,
            amount = calculation.progress.totalPages,
            title = reason,
            reason = "سطح کتاب‌خوانی ${calculation.progress.level} • ${calculation.progress.pagesInLevel} صفحه در این سطح",
            createdAtEpochMillis = System.currentTimeMillis(),
            createdBy = "mentor",
            metadata = mapOf(
                "readingLevel" to calculation.progress.level.toString(),
                "readingPages" to calculation.progress.totalPages.toString(),
                "readingPoints" to calculation.progress.points.toString()
            )
        )
        app.members[index] = updated
        app.persist()
        return updated
    }

    /** Saves the mentor-editable group profile fields. */
    fun updateGroupProfile(groupId: String, description: String, profileImagePath: String?) {
        val index = app.groups.indexOfFirst { it.id == groupId }
        require(index >= 0) { "گروه پیدا نشد" }
        app.groups[index] = app.groups[index].copy(
            description = description.trim(),
            profileImagePath = profileImagePath
        )
        app.persist()
    }
}
