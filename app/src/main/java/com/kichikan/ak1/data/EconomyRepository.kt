package com.kichikan.ak1.data

import com.kichikan.ak1.domain.model.*
import com.kichikan.ak1.domain.service.EconomyChange
import com.kichikan.ak1.domain.service.EconomyService
import com.kichikan.ak1.domain.service.RankSystem

/** Owns XP, spendable-point, diamond and level mutations with their ledger events. */
internal class EconomyRepository(private val app: AppRepository) {
    fun recordXp(memberId: String, amount: Int, reason: String, actor: String? = null) =
        mutate(memberId) {
            EconomyService.addXp(it, amount, reason, actor, System.currentTimeMillis())
        }

    fun recordPoints(memberId: String, amount: Int, reason: String, actor: String? = null) =
        mutate(memberId) {
            EconomyService.adjustPoints(it, amount, reason, actor, System.currentTimeMillis())
        }

    fun recordDiamonds(memberId: String, amount: Int, reason: String, actor: String? = null) =
        mutate(memberId) {
            EconomyService.adjustDiamonds(it, amount, reason, actor, System.currentTimeMillis())
        }

    fun decreaseXp(memberId: String, amount: Int, reason: String, actor: String? = null) =
        mutate(memberId) {
            EconomyService.decreaseXp(it, amount, reason, actor, System.currentTimeMillis())
        }

    fun spendPoints(memberId: String, amount: Int, reason: String, actor: String? = null) =
        mutate(memberId) {
            EconomyService.spendPoints(it, amount, reason, actor, System.currentTimeMillis())
        }

    fun decreaseLevel(memberId: String, targetLevel: Int, reason: String, actor: String? = null) =
        mutate(memberId) {
            EconomyService.decreaseLevel(it, targetLevel, reason, actor, System.currentTimeMillis())
        }

    private fun mutate(
        memberId: String,
        operation: (MemberEconomy) -> EconomyChange
    ): Member {
        val member = app.members.first { it.id == memberId }
        val change = operation(member.economy)

        app.history += change.events.map { it.copy(memberId = memberId) }

        // Military badges are earned automatically when a level gate is crossed. They are
        // recorded as owned inventory; the mentor still chooses which badge is displayed.
        val levelUpEvents = change.events.filter {
            it.type == HistoryType.LEVEL_CHANGED && it.title.startsWith("رسیدن به سطح ")
        }
        levelUpEvents.forEach { levelEvent ->
            val level = levelEvent.amount ?: return@forEach
            val badge = RankSystem.badgesUnlockedBetween(level - 1, level).firstOrNull() ?: return@forEach
            val itemId = RankSystem.badgeId(badge)
            val item = app.shop.firstOrNull { it.id == itemId && it.active } ?: return@forEach
            val eventId = "level-badge-$memberId-$level"
            if (app.history.none { it.id == eventId }) {
                app.history += HistoryEvent(
                    id = eventId,
                    memberId = memberId,
                    type = HistoryType.REWARD_RECEIVED,
                    amount = null,
                    title = "نشان ${badge.name} به دست آمد",
                    reason = "رسیدن به سطح $level",
                    createdAtEpochMillis = levelEvent.createdAtEpochMillis,
                    createdBy = "system",
                    metadata = mapOf("itemId" to item.id, "type" to "BADGE", "acquisition" to "LEVEL_AUTO")
                )
            }
        }

        // Earning a badge never equips it. The active badge remains the mentor's choice.
        val updated = member.copy(economy = change.economy)
        app.updateMember(updated)
        return updated
    }
}
