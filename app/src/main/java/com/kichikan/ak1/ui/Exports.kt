package com.kichikan.ak1.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.kichikan.ak1.data.AppRepository
import com.kichikan.ak1.domain.calendar.JalaliCalendar
import com.kichikan.ak1.domain.model.Currency
import com.kichikan.ak1.domain.model.Member
import com.kichikan.ak1.domain.model.ShopItem
import com.kichikan.ak1.domain.model.ShopItemType
import com.kichikan.ak1.domain.service.RankArt
import com.kichikan.ak1.domain.service.RankSystem

/** One line of a ranking export; avatar/frame/badge are drawn together. */
data class RankRow(
    val title: String,
    val subtitle: String,
    val avatarPath: String? = null,
    val framePath: String? = null,
    val letter: String = "",
    val badgePath: String? = null,
    val cardPath: String? = null,
    val level: Int = 0,
    val rankTitle: String = "",
    val crestPath: String? = null,
    val cardRing: RankArt.CardRing? = null
)

object Exports {
    private fun paint(color: Int, size: Float, bold: Boolean = false, align: Paint.Align = Paint.Align.CENTER) =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = align
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun fa(number: Int): String =
        number.toString().map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

    private fun background(canvas: Canvas, w: Int, h: Int, accent: Int = 0xFF1EFCE5.toInt()) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF02050B.toInt(), 0xFF0A1220.toInt(), 0xFF03060A.toInt()),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = RadialGradient(
            w * 0.82f, h * 0.15f, w * 0.85f,
            (accent and 0x00FFFFFF) or 0x30000000, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = RadialGradient(
            w * 0.08f, h * 0.88f, w * 0.9f,
            0x1FB197FF, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        // Faint diamond lattice, printed-poster feel without competing with the cards.
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x0CFFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        val step = (w / 26f).coerceAtLeast(42f)
        var x = -h.toFloat()
        while (x < w + h) {
            canvas.drawLine(x, 0f, x + h, h.toFloat(), line)
            canvas.drawLine(x, h.toFloat(), x + h, 0f, line)
            x += step
        }
    }

    private fun frame(canvas: Canvas, w: Int, h: Int, accent: Int, inset: Float = 34f) {
        val outer = RectF(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(outer, 46f, 46f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x0DFFFFFF
        })
        canvas.drawRoundRect(outer, 46f, 46f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.4f
            color = (accent and 0x00FFFFFF) or 0x66000000
        })
        val inner = RectF(inset + 13f, inset + 13f, w - inset - 13f, h - inset - 13f)
        canvas.drawRoundRect(inner, 38f, 38f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = 0x22FFFFFF
        })
        // Corner brackets.
        val c = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = (accent and 0x00FFFFFF) or 0x88000000.toInt()
        }
        val len = 72f
        listOf(
            floatArrayOf(inset, inset, 1f, 1f),
            floatArrayOf(w - inset, inset, -1f, 1f),
            floatArrayOf(inset, h - inset, 1f, -1f),
            floatArrayOf(w - inset, h - inset, -1f, -1f)
        ).forEach { corner ->
            val cx = corner[0]
            val cy = corner[1]
            val sx = corner[2]
            val sy = corner[3]
            canvas.drawLine(cx, cy, cx + sx * len, cy, c)
            canvas.drawLine(cx, cy, cx, cy + sy * len, c)
        }
    }

    private fun drawCover(canvas: Canvas, bitmap: Bitmap?, dst: RectF, alpha: Int = 255) {
        if (bitmap == null || bitmap.isRecycled) return
        val scale = maxOf(dst.width() / bitmap.width, dst.height() / bitmap.height)
        val sw = (dst.width() / scale).toInt().coerceIn(1, bitmap.width)
        val sh = (dst.height() / scale).toInt().coerceIn(1, bitmap.height)
        val sx = (bitmap.width - sw) / 2
        val sy = (bitmap.height - sh) / 2
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }
        canvas.drawBitmap(bitmap, Rect(sx, sy, sx + sw, sy + sh), dst, p)
    }

    /**
     * Draws a rank card as a cover image and puts the member's plain avatar inside the circular
     * opening that is already baked into the card artwork. The member's own frame is deliberately
     * not used here; callers draw a second framed copy so both looks remain available.
     */
    private fun drawCardWithRing(
        canvas: Canvas,
        art: Bitmap?,
        dst: RectF,
        avatarPath: String?,
        ring: RankArt.CardRing?,
        alpha: Int = 255
    ) {
        drawCover(canvas, art, dst, alpha)
        if (art == null || art.isRecycled || ring == null || avatarPath.isNullOrBlank()) return
        val scale = maxOf(dst.width() / art.width, dst.height() / art.height)
        val sw = dst.width() / scale
        val sh = dst.height() / scale
        val sx = (art.width - sw) / 2f
        val sy = (art.height - sh) / 2f
        val cx = dst.left + (ring.centerX * art.width - sx) * scale
        val cy = dst.top + (ring.centerY * art.height - sy) * scale
        val radius = ring.radius * art.width * scale * 0.95f
        val avatar = AvatarRender.decodeSampled(avatarPath, (radius * 2f).toInt().coerceAtLeast(256))
            ?: return
        val side = minOf(avatar.width, avatar.height)
        val src = Rect(
            (avatar.width - side) / 2,
            (avatar.height - side) / 2,
            (avatar.width + side) / 2,
            (avatar.height + side) / 2
        )
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) })
        canvas.drawBitmap(
            avatar,
            src,
            RectF(cx - radius, cy - radius, cx + radius, cy + radius),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }
        )
        canvas.restore()
    }

    private fun drawAvatar(
        canvas: Canvas,
        avatarPath: String?,
        framePath: String?,
        letter: String,
        left: Float,
        top: Float,
        size: Int,
        accent: Int = 0xFF1EFCE5.toInt()
    ) {
        if (framePath.isNullOrBlank()) {
            canvas.drawCircle(
                left + size / 2f, top + size / 2f, size / 2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (accent and 0x00FFFFFF) or 0x33000000 }
            )
        }
        val bitmap = AvatarRender.compose(avatarPath, framePath, size, letter)
        canvas.drawBitmap(bitmap, left, top, null)
        if (framePath.isNullOrBlank()) {
            canvas.drawCircle(
                left + size / 2f, top + size / 2f, size / 2f - 1.5f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                    color = accent
                }
            )
        }
    }

    private fun drawBadge(canvas: Canvas, path: String?, left: Float, top: Float, size: Float) {
        if (path.isNullOrBlank()) return
        val bitmap = AvatarRender.decodeSampled(path, size.toInt().coerceAtLeast(64)) ?: return
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + size, top + size), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun wrap(canvas: Canvas, text: String, paint: Paint, centerX: Float, startY: Float, maxWidth: Float, lineHeight: Float) {
        val words = text.trim().split(" ")
        var line = ""
        var y = startY
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, centerX, y, paint)
                line = word
                y += lineHeight
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) canvas.drawText(line, centerX, y, paint)
    }

    fun memberCard(repo: AppRepository, member: Member, context: Context): Bitmap {
        val w = 1200
        val h = 1680
        val tier = RankSystem.forLevel(member.economy.level)
        val accent = tier.accent.toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        background(canvas, w, h, accent)

        val avatarPath = repo.shop.firstOrNull { it.id == member.avatarItemId }?.imagePath
        val framePath = repo.shop.firstOrNull { it.id == member.frameItemId }?.imagePath
        val badgePath = repo.shop.firstOrNull { it.id == member.badgeItemId }?.imagePath

        // Rank card used as a ceremonial banner, not stretched over the whole portrait. The
        // plain avatar sits in the card's own ring; the large framed portrait below is the
        // second, frame-free card copy requested by the design.
        val art = RankArt.cardPath(context, tier)?.let { AvatarRender.decodeSampled(it, 1400) }
        drawCardWithRing(
            canvas,
            art,
            RectF(48f, 48f, w - 48f, 430f),
            avatarPath,
            RankArt.cardRing(tier),
            alpha = 224
        )
        canvas.drawRect(
            48f, 250f, w - 48f, 440f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 250f, 0f, 440f,
                    intArrayOf(0x00000000, 0xCC03060A.toInt()), null, Shader.TileMode.CLAMP
                )
            }
        )

        frame(canvas, w, h, accent)
        canvas.drawText("شناسنامه متربی", w / 2f, 110f, paint(0xCCFFFFFF.toInt(), 34f, bold = true))
        canvas.drawText(repo.ring?.ringName ?: "حلقه", w / 2f, 158f, paint(0xAAFEC856.toInt(), 26f))

        val size = 430
        val avatarTop = 220f
        drawAvatar(canvas, avatarPath, framePath, member.name, (w - size) / 2f, avatarTop, size, accent)

        // Rank plaque under the portrait.
        val plaque = RectF(w / 2f - 210f, 690f, w / 2f + 210f, 764f)
        canvas.drawRoundRect(plaque, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (accent and 0x00FFFFFF) or 0x33000000
            style = Paint.Style.FILL
        })
        canvas.drawRoundRect(plaque, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = accent
        })
        canvas.drawText(
            "رتبه ${fa(tier.index)} • ${tier.name}",
            w / 2f, 741f, paint(accent, 34f, bold = true)
        )

        val namePaint = paint(Color.WHITE, 66f, bold = true)
        canvas.drawText(member.name, w / 2f, 872f, namePaint)
        if (badgePath != null) {
            val badgeSize = 66f
            val nameHalf = namePaint.measureText(member.name) / 2f
            drawBadge(canvas, badgePath, w / 2f - nameHalf - badgeSize - 18f, 812f, badgeSize)
        }
        canvas.drawText(
            tier.name,
            w / 2f, 936f, paint(tier.accentSoft.toInt(), 34f, bold = true)
        )
        repo.groups.firstOrNull { it.id == member.groupId }?.let { group ->
            canvas.drawText(group.name, w / 2f, 990f, paint(0xFFC3D3F2.toInt(), 28f))
        }

        val statsTop = 1040f
        val stats = listOf(
            "سطح" to fa(member.economy.level),
            "XP" to fa(member.economy.xp),
            "امتیاز" to fa(member.economy.spendablePoints),
            "الماس" to fa(member.economy.diamonds)
        )
        val gap = 18f
        val panelWidth = (w - 96f - gap * 3f) / 4f
        stats.forEachIndexed { index, (label, value) ->
            val left = 48f + index * (panelWidth + gap)
            val top = statsTop
            val panel = RectF(left, top, left + panelWidth, top + 150f)
            canvas.drawRoundRect(panel, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x12FFFFFF })
            canvas.drawRoundRect(panel, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.2f; color = 0x2AFFFFFF
            })
            canvas.drawText(label, left + panelWidth / 2f, top + 52f, paint(0x99FFFFFF.toInt(), 25f))
            canvas.drawText(value, left + panelWidth / 2f, top + 118f, paint(accent, 42f, bold = true))
        }

        val lineY = 1280f
        canvas.drawLine(90f, lineY, w - 90f, lineY, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(90f, 0f, w - 90f, 0f, intArrayOf(Color.TRANSPARENT, accent, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            strokeWidth = 1.5f
        })
        canvas.drawText(
            repo.ring?.ringName ?: "حلقه",
            w / 2f, 1388f, paint(0xFFC3D3F2.toInt(), 32f, bold = true)
        )
        canvas.drawText(
            "تاریخ: ${JalaliCalendar.format(JalaliCalendar.fromGregorian(java.time.LocalDate.now()))}",
            w / 2f, 1446f, paint(0x99FFFFFF.toInt(), 25f)
        )
        return bitmap
    }

    fun rankingCard(
        title: String,
        rows: List<RankRow>,
        subtitle: String = "بر اساس سطح، XP و تلاش؛ هر رتبه کارت و نشان خودش را دارد"
    ): Bitmap {
        val width = 1600
        val rowHeight = 196
        val header = 360
        val height = (header + rows.size.coerceAtLeast(1) * rowHeight + 110).coerceAtMost(9000)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        background(canvas, width, height, 0xFFFEC856.toInt())
        frame(canvas, width, height, 0xFFFEC856.toInt())
        canvas.drawText(title, width / 2f, 120f, paint(0xFFFEC856.toInt(), 72f, bold = true))
        canvas.drawText(
            subtitle,
            width / 2f, 184f, paint(0xFFC3D3F2.toInt(), 30f)
        )
        canvas.drawLine(width * 0.18f, 226f, width * 0.82f, 226f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                width * 0.18f, 0f, width * 0.82f, 0f,
                intArrayOf(Color.TRANSPARENT, 0xFF1EFCE5.toInt(), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
            strokeWidth = 3f
        })
        if (rows.isEmpty()) {
            canvas.drawText("هنوز عضوی برای نمایش در این فهرست نیست.", width / 2f, header + 80f, paint(Color.WHITE, 36f))
        }

        val rankColors = listOf(0xFFFEC856.toInt(), 0xFFD6DEEB.toInt(), 0xFFE0955F.toInt())
        rows.forEachIndexed { i, row ->
            val top = header + i * rowHeight
            val card = RectF(54f, top + 10f, width - 54f, top + rowHeight - 10f)
            // Card art fills the row and fades under a dark reading scrim.
            val art = row.cardPath?.let { AvatarRender.decodeSampled(it, 1600) }
            if (art != null) {
                canvas.save()
                canvas.clipRect(card)
                drawCardWithRing(canvas, art, card, row.avatarPath, row.cardRing, alpha = 190)
                canvas.drawRect(card, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        card.left, 0f, card.right, 0f,
                        intArrayOf(0xE605080C.toInt(), 0xB005080C.toInt(), 0x5505080C),
                        null, Shader.TileMode.CLAMP
                    )
                })
                canvas.restore()
            } else {
                canvas.drawRoundRect(card, 38f, 38f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1AFFFFFF })
            }
            canvas.drawRoundRect(card, 38f, 38f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = (rankColors.getOrElse(i) { 0xFF1EFCE5.toInt() } and 0x00FFFFFF) or 0x55000000
            })

            val rankColor = rankColors.getOrElse(i) { 0xFF1EFCE5.toInt() }
            val badgeX = 130f
            val badgeY = top + rowHeight / 2f
            canvas.drawCircle(badgeX, badgeY, 45f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (rankColor and 0x00FFFFFF) or 0x44000000 })
            canvas.drawCircle(badgeX, badgeY, 45f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 3f; color = rankColor
            })
            canvas.drawText("${i + 1}", badgeX, badgeY + 18f, paint(rankColor, 46f, bold = true))

            val avatarSize = 136
            if (!row.crestPath.isNullOrBlank()) {
                val crest = AvatarRender.decodeSampled(row.crestPath, avatarSize)
                if (crest != null) {
                    canvas.save()
                    canvas.clipPath(android.graphics.Path().apply {
                        addCircle(220f + avatarSize / 2f, top + rowHeight / 2f, avatarSize / 2f, android.graphics.Path.Direction.CW)
                    })
                    canvas.drawBitmap(crest, null, RectF(220f, top + (rowHeight - avatarSize) / 2f, 220f + avatarSize, top + (rowHeight + avatarSize) / 2f), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    canvas.restore()
                }
            } else {
                drawAvatar(canvas, row.avatarPath, row.framePath, row.letter, 220f, top + (rowHeight - avatarSize) / 2f, avatarSize, rankColor)
            }

            val textLeft = 392f
            val namePaint = paint(Color.WHITE, 46f, bold = true, align = Paint.Align.LEFT)
            canvas.drawText(row.title, textLeft, top + 78f, namePaint)
            val nameWidth = namePaint.measureText(row.title)
            row.badgePath?.let { path ->
                drawBadge(canvas, path, textLeft + nameWidth + 16f, top + 34f, 54f)
            }
            val subtitle = if (row.rankTitle.isNotBlank()) "${row.subtitle} • ${row.rankTitle}" else row.subtitle
            canvas.drawText(subtitle, textLeft, top + 134f, paint(0xFFC3D3F2.toInt(), 31f, align = Paint.Align.LEFT))
        }
        return bitmap
    }

    /** Poster-style card for a shop item: picture, how to get it, price and description. */
    fun shopItemCard(repo: AppRepository, item: ShopItem): Bitmap {
        val w = 1200
        val h = 1560
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val typeColor = when (item.type) {
            ShopItemType.AVATAR -> 0xFF1EFCE5.toInt()
            ShopItemType.FRAME -> 0xFFB197FF.toInt()
            ShopItemType.BADGE -> 0xFFFEC856.toInt()
            ShopItemType.REWARD -> 0xFFFEC856.toInt()
            ShopItemType.GROUP_CREST, ShopItemType.GROUP_CARD -> 0xFFB197FF.toInt()
        }
        background(canvas, w, h, typeColor)
        frame(canvas, w, h, typeColor, inset = 48f)
        val typeLabel = when (item.type) {
            ShopItemType.AVATAR -> "آواتار"
            ShopItemType.FRAME -> "قاب تصویر"
            ShopItemType.BADGE -> "نشان نام"
            ShopItemType.REWARD -> "جایزه"
            ShopItemType.GROUP_CREST -> "نشان گروه"
            ShopItemType.GROUP_CARD -> "کارت گروه"
        }
        val tag = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (typeColor and 0x00FFFFFF) or 0x22000000 }
        canvas.drawRoundRect(RectF(w / 2f - 150f, 96f, w / 2f + 150f, 168f), 36f, 36f, tag)
        canvas.drawText(typeLabel, w / 2f, 146f, paint(typeColor, 35f, bold = true))

        val imageSize = 700
        val left = (w - imageSize) / 2f
        val top = 210f
        canvas.drawRoundRect(RectF(left - 24f, top - 24f, left + imageSize + 24f, top + imageSize + 24f), 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x10FFFFFF })
        if (item.imagePath != null) {
            when (item.type) {
                ShopItemType.AVATAR -> drawAvatar(canvas, item.imagePath, null, item.name, left, top, imageSize, typeColor)
                ShopItemType.FRAME -> drawAvatar(canvas, null, item.imagePath, item.name, left, top, imageSize, typeColor)
                ShopItemType.BADGE, ShopItemType.GROUP_CREST,
                ShopItemType.GROUP_CARD, ShopItemType.REWARD -> {
                    val art = AvatarRender.decodeSampled(item.imagePath, imageSize)
                    if (art != null) {
                        val side = minOf(art.width, art.height)
                        val src = Rect((art.width - side) / 2, (art.height - side) / 2, (art.width + side) / 2, (art.height + side) / 2)
                        canvas.drawBitmap(art, src, RectF(left, top, left + imageSize, top + imageSize), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    }
                }
            }
        } else {
            canvas.drawCircle(left + imageSize / 2f, top + imageSize / 2f, imageSize / 2.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = typeColor and 0x33FFFFFF })
            canvas.drawText(item.name.take(1), left + imageSize / 2f, top + imageSize / 2f + 80f, paint(Color.WHITE, 220f, bold = true))
        }

        canvas.drawText(item.name, w / 2f, 1048f, paint(Color.WHITE, 66f, bold = true))
        val description = item.description.ifBlank { "بدون توضیح" }
        wrap(canvas, description, paint(0xFFC3D3F2.toInt(), 32f), w / 2f, 1114f, w - 240f, 46f)

        val manualOnly = com.kichikan.ak1.domain.model.AcquisitionMethod.MANUAL in item.methods &&
            com.kichikan.ak1.domain.model.AcquisitionMethod.DIRECT_PURCHASE !in item.methods &&
            com.kichikan.ak1.domain.model.AcquisitionMethod.LEVEL_UNLOCK !in item.methods
        val acquisition = when {
            manualOnly -> "فقط با هدیه دستی مربی"
            item.price == 0 && item.currency == Currency.NONE &&
                com.kichikan.ak1.domain.model.AcquisitionMethod.LEVEL_UNLOCK in item.methods ->
                "بازشدن با سطح ${fa(item.minimumLevel ?: 0)} • دریافت دستی"
            item.currency == Currency.POINTS -> "${fa(item.price)} امتیاز"
            item.currency == Currency.DIAMONDS -> "${fa(item.price)} الماس"
            else -> "بدون هزینه"
        }
        canvas.drawText("روش دریافت: $acquisition", w / 2f, 1278f, paint(0xFF1EFCE5.toInt(), 36f, bold = true))
        if (item.methods.isNotEmpty()) {
            canvas.drawText(item.methods.joinToString(" • ") { it.name }, w / 2f, 1334f, paint(0xFFC3D3F2.toInt(), 26f))
        }
        canvas.drawText(repo.ring?.ringName ?: "حلقه", w / 2f, 1450f, paint(0xFFFEC856.toInt(), 32f, bold = true))
        return bitmap
    }
}
