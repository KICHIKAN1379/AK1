package com.kichikan.ak1.domain.service

/**
 * The fifteen-step AK1 honour ladder.
 *
 * The ladder is deliberately data-only: the shop, the member cards, the exports and the
 * date strip all read the same list, so a rank can never mean two different things in two
 * screens. The level gates are claim gates, not gifts: reaching a gate only *unlocks* the
 * manual claim in the store.
 */
data class RankTier(
    val index: Int,
    val key: String,
    val name: String,
    val minLevel: Int,
    val accent: Long,
    val accentSoft: Long
) {
    val id: String get() = index.toString().padStart(2, '0')
}

/**
 * A 21-step military insignia ladder. It is intentionally separate from the 15 visual
 * member ranks: the avatar/frame/card change once per rank, while the nameplate badge
 * follows the Iranian military grades from soldier to general.
 */
data class MilitaryBadge(
    val index: Int,
    val name: String
) {
    val id: String get() = index.toString().padStart(2, '0')
    val minLevel: Int get() = index
}

object RankSystem {
    /**
     * Ranks 1..15, one member level each. Level 0 is still shown as a soldier and level
     * 15 or above is the eternal legend. The last tier intentionally has no upper bound.
     */
    val tiers: List<RankTier> = listOf(
        RankTier(1, "soldier", "سرباز", 1, 0xFFA9C7E8, 0xFFEEF7FF),
        RankTier(2, "warrior", "جنگجو", 2, 0xFF58D49A, 0xFFD2FFE9),
        RankTier(3, "brave", "دلاور", 3, 0xFF3FE0CC, 0xFFD0FFF9),
        RankTier(4, "ranger", "رزم‌آور", 4, 0xFF5A9DFF, 0xFFD8E7FF),
        RankTier(5, "elite", "نخبه", 5, 0xFFB47CFF, 0xFFF0E2FF),
        RankTier(6, "paladin", "پهلوان", 6, 0xFFE0B25C, 0xFFFFF0CD),
        RankTier(7, "champion", "قهرمان", 7, 0xFFFFD166, 0xFFFFF8DD),
        RankTier(8, "commander", "سردار", 8, 0xFFFF635B, 0xFFFFD8D3),
        RankTier(9, "warlord", "سپهسالار", 9, 0xFFDCB76A, 0xFFFDEFCB),
        RankTier(10, "zulfiqar", "ذوالفقار", 10, 0xFF3FE0A0, 0xFFD2FFEC),
        RankTier(11, "khyber", "خیبرشکن", 11, 0xFFFF554D, 0xFFFFCFC7),
        RankTier(12, "world", "جهان‌پهلوان", 12, 0xFF5A9DFF, 0xFFDCEBFF),
        RankTier(13, "peak", "فاتح قله", 13, 0xFFFFD166, 0xFFFFF9DD),
        RankTier(14, "legend", "اسطوره", 14, 0xFFC07BFF, 0xFFF5E4FF),
        RankTier(15, "eternal", "اسطوره جاودان", 15, 0xFFFFE7A6, 0xFFFFFEF8)
    )

    /** Iranian military grades from private third class to general (ارتشبد). */
    val badges: List<MilitaryBadge> = listOf(
        MilitaryBadge(1, "سرباز سوم"),
        MilitaryBadge(2, "سرباز دوم"),
        MilitaryBadge(3, "سرباز یکم"),
        MilitaryBadge(4, "سرجوخه"),
        MilitaryBadge(5, "گروهبان سوم"),
        MilitaryBadge(6, "گروهبان دوم"),
        MilitaryBadge(7, "گروهبان یکم"),
        MilitaryBadge(8, "استوار دوم"),
        MilitaryBadge(9, "استوار یکم"),
        MilitaryBadge(10, "ستوان سوم"),
        MilitaryBadge(11, "ستوان دوم"),
        MilitaryBadge(12, "ستوان یکم"),
        MilitaryBadge(13, "سروان"),
        MilitaryBadge(14, "سرگرد"),
        MilitaryBadge(15, "سرهنگ دوم"),
        MilitaryBadge(16, "سرهنگ"),
        MilitaryBadge(17, "سرتیپ دوم"),
        MilitaryBadge(18, "سرتیپ"),
        MilitaryBadge(19, "سرلشکر"),
        MilitaryBadge(20, "سپهبد"),
        MilitaryBadge(21, "ارتشبد")
    )

    /** A separate heraldic set for groups; these are not member ranks. */
    data class GroupDesign(val key: String, val name: String, val accent: Long)

    val groupDesigns: List<GroupDesign> = listOf(
        GroupDesign("lion", "شیر", 0xFFE0A84F),
        GroupDesign("eagle", "عقاب", 0xFF6FA8FF),
        GroupDesign("mountain", "کوه", 0xFF62D39B),
        GroupDesign("flame", "شعله", 0xFFFF724F),
        GroupDesign("wave", "موج", 0xFF43D6DF),
        GroupDesign("sword", "شمشیر", 0xFFC3CFDF),
        GroupDesign("crescent", "هلال", 0xFF55E0B4),
        GroupDesign("sun", "خورشید", 0xFFFFD166)
    )

    fun forLevel(level: Int): RankTier {
        val safe = level.coerceAtLeast(0)
        return tiers.lastOrNull { safe >= it.minLevel } ?: tiers.first()
    }

    fun badgeForLevel(level: Int): MilitaryBadge {
        val safe = level.coerceAtLeast(0)
        return badges.lastOrNull { safe >= it.minLevel } ?: badges.first()
    }

    fun forIndex(index: Int): RankTier = tiers.getOrElse(index.coerceIn(1, tiers.size) - 1) { tiers.first() }

    fun next(tier: RankTier): RankTier? = tiers.getOrNull(tier.index)

    /** 0f at the start of the tier, 1f on the day the next tier opens. */
    fun progress(level: Int, tier: RankTier = forLevel(level)): Float {
        val next = next(tier) ?: return 1f
        val span = (next.minLevel - tier.minLevel).coerceAtLeast(1)
        return ((level - tier.minLevel).toFloat() / span).coerceIn(0f, 1f)
    }

    fun avatarId(tier: RankTier): String = "rank-avatar-${tier.id}"
    fun frameId(tier: RankTier): String = "rank-frame-${tier.id}"
    fun badgeId(badge: MilitaryBadge): String = "rank-badge-${badge.id}"
    fun groupCrestId(index: Int): String = "group-crest-${(index + 1).toString().padStart(2, '0')}"
    fun groupCardId(index: Int): String = "group-card-${(index + 1).toString().padStart(2, '0')}"
}
