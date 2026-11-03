package com.kichikan.ak1.domain.service

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Copies the bundled rank artwork out of the APK once and returns stable file paths.
 *
 * The frames keep a transparent centre. [com.kichikan.ak1.ui.AvatarRender] discovers that
 * opening from the pixels, so every rank frame clips any avatar without special-casing.
 */
object RankArt {
    const val VERSION = 5
    private const val ASSET_DIR = "art/ranks"
    private const val DIR_NAME = "rank_art"
    private const val CARD_WIDTH = 1376f
    private const val CARD_HEIGHT = 768f
    private val cache = ConcurrentHashMap<String, String>()

    /**
     * The portrait opening baked into each of the new 1376x768 card artworks.
     *
     * Values are normalised to the card bitmap so the same circle can be projected onto any
     * Compose/Canvas size, including cover-cropped cards. [radius] is a fraction of the card
     * width; [centerX] and [centerY] are fractions of width and height.
     */
    data class CardRing(val centerX: Float, val centerY: Float, val radius: Float)

    private val cardRings: List<CardRing> = listOf(
        CardRing(1175.2f / CARD_WIDTH, 393.4f / CARD_HEIGHT, 118.1f / CARD_WIDTH),
        CardRing(1144.8f / CARD_WIDTH, 386.7f / CARD_HEIGHT, 140.1f / CARD_WIDTH),
        CardRing(1100.5f / CARD_WIDTH, 375.2f / CARD_HEIGHT, 153.4f / CARD_WIDTH),
        CardRing(1171.5f / CARD_WIDTH, 394.2f / CARD_HEIGHT, 115.5f / CARD_WIDTH),
        CardRing(1140.8f / CARD_WIDTH, 380.7f / CARD_HEIGHT, 133.1f / CARD_WIDTH),
        CardRing(1108.2f / CARD_WIDTH, 374.2f / CARD_HEIGHT, 142.0f / CARD_WIDTH),
        CardRing(1210.1f / CARD_WIDTH, 386.9f / CARD_HEIGHT, 103.1f / CARD_WIDTH),
        CardRing(1110.6f / CARD_WIDTH, 394.8f / CARD_HEIGHT, 138.7f / CARD_WIDTH),
        CardRing(1162.0f / CARD_WIDTH, 383.9f / CARD_HEIGHT, 139.8f / CARD_WIDTH),
        CardRing(1125.4f / CARD_WIDTH, 385.6f / CARD_HEIGHT, 149.4f / CARD_WIDTH),
        CardRing(1119.2f / CARD_WIDTH, 389.1f / CARD_HEIGHT, 154.4f / CARD_WIDTH),
        CardRing(1090.6f / CARD_WIDTH, 384.9f / CARD_HEIGHT, 144.9f / CARD_WIDTH),
        CardRing(1109.2f / CARD_WIDTH, 382.6f / CARD_HEIGHT, 148.3f / CARD_WIDTH),
        CardRing(1143.2f / CARD_WIDTH, 346.3f / CARD_HEIGHT, 125.9f / CARD_WIDTH),
        CardRing(1117.4f / CARD_WIDTH, 371.8f / CARD_HEIGHT, 127.9f / CARD_WIDTH)
    )

    private data class Asset(val name: String, val extension: String)

    private fun asset(kind: String, id: String): Asset = when (kind) {
        // The rank portraits were replaced with transparent-corner PNGs. Keep the
        // extension explicit so an old cached JPG is never reused.
        "avatar" -> Asset("avatar_$id", "png")
        "frame" -> Asset("frame_$id", "png")
        "badge" -> Asset("badge_$id", "png")
        "card" -> Asset("card_$id", "jpg")
        "group_crest" -> Asset("group_crest_$id", "png")
        "group_card" -> Asset("group_card_$id", "jpg")
        else -> Asset(kind, "png")
    }

    @Synchronized
    fun ensure(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("ak1_rank_art", Context.MODE_PRIVATE)
        val directory = File(app.filesDir, DIR_NAME)
        if (!directory.exists() && !directory.mkdirs()) return
        val refreshAll = prefs.getInt("version", 0) < VERSION
        val jobs = buildList {
            RankSystem.tiers.forEach { tier ->
                add(asset("avatar", tier.id))
                add(asset("frame", tier.id))
                add(asset("card", tier.id))
            }
            RankSystem.badges.forEach { badge ->
                add(asset("badge", badge.id))
            }
            RankSystem.groupDesigns.indices.forEach { index ->
                val id = (index + 1).toString().padStart(2, '0')
                add(asset("group_crest", id))
                add(asset("group_card", id))
            }
        }
        var copied = 0
        jobs.forEach { item ->
            val target = File(directory, "${item.name}.${item.extension}")
            if (!refreshAll && target.isFile && target.length() > 0L) return@forEach
            runCatching {
                app.assets.open("$ASSET_DIR/${item.name}.${item.extension}").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                copied++
            }
        }
        prefs.edit().putInt("version", VERSION).putInt("copied", copied).apply()
    }

    private fun path(context: Context, item: Asset): String? {
        cache[item.name]?.let { cached -> if (File(cached).isFile) return cached }
        val target = File(context.applicationContext.filesDir, "$DIR_NAME/${item.name}.${item.extension}")
        if (!target.isFile || target.length() == 0L) {
            runCatching { ensure(context) }
        }
        return target.takeIf { it.isFile && it.length() > 0L }?.absolutePath?.also { cache[item.name] = it }
    }

    fun avatarPath(context: Context, tier: RankTier): String? = path(context, asset("avatar", tier.id))
    fun framePath(context: Context, tier: RankTier): String? = path(context, asset("frame", tier.id))
    fun badgePath(context: Context, badge: MilitaryBadge): String? = path(context, asset("badge", badge.id))
    fun cardPath(context: Context, tier: RankTier): String? = path(context, asset("card", tier.id))

    fun cardPathForLevel(context: Context, level: Int): String? =
        cardPath(context, RankSystem.forLevel(level))

    fun cardRing(tier: RankTier): CardRing = cardRings.getOrElse(tier.index - 1) { cardRings.first() }

    fun cardRingForLevel(level: Int): CardRing = cardRing(RankSystem.forLevel(level))

    fun badgePathForLevel(context: Context, level: Int): String? =
        badgePath(context, RankSystem.badgeForLevel(level))

    fun groupCrestPath(context: Context, index: Int): String? {
        val id = (index + 1).toString().padStart(2, '0')
        return path(context, asset("group_crest", id))
    }

    fun groupCardPath(context: Context, index: Int): String? {
        val id = (index + 1).toString().padStart(2, '0')
        return path(context, asset("group_card", id))
    }
}
