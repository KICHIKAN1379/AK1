package com.kichikan.ak1.ui

import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kichikan.ak1.domain.service.RankArt
import com.kichikan.ak1.domain.service.RankSystem
import com.kichikan.ak1.domain.service.RankTier
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val cardCache = object : LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(8) {}

@Composable
fun RankTierChip(level: Int, modifier: Modifier = Modifier) {
    val tier = remember(level) { RankSystem.forLevel(level) }
    val accent = Color(tier.accent)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)) {
            Text(tier.name, style = MaterialTheme.typography.labelSmall, color = accent)
            Spacer(Modifier.width(5.dp))
            Text("${tier.index}/۱۵", style = MaterialTheme.typography.labelSmall, color = accent.copy(alpha = 0.72f))
        }
    }
}

@Composable
fun RankCardSurface(
    level: Int,
    modifier: Modifier = Modifier,
    cardAvatarPath: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val tier = remember(level) { RankSystem.forLevel(level) }
    val path = remember(tier.index) { RankArt.cardPath(context, tier) }
    val ring = remember(tier.index) { RankArt.cardRing(tier) }
    ArtCardSurface(
        path = path,
        accent = Color(tier.accent),
        modifier = modifier,
        cardAvatarPath = cardAvatarPath,
        cardRing = ring,
        content = content
    )
}

@Composable
fun ArtCardSurface(
    path: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    cardAvatarPath: String? = null,
    cardRing: RankArt.CardRing? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val image = remember(path) {
        path?.let { file ->
            cardCache.get(file) ?: AvatarRender.decodeSampled(file, 1400)?.asImageBitmap()?.also { cardCache.put(file, it) }
        }
    }
    val cardAvatar = remember(cardAvatarPath) {
        cardAvatarPath?.let { AvatarRender.decodeSampled(it, 512)?.asImageBitmap() }
    }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF111721), Color(0xFF070A0F))
                )
            )
            .border(1.dp, accent.copy(alpha = 0.30f), shape)
    ) {
        image?.let { card ->
            Canvas(Modifier.fillMaxSize()) {
                val scale = max(size.width / card.width, size.height / card.height)
                val dstW = card.width * scale
                val dstH = card.height * scale
                val left = (size.width - dstW) / 2f
                val top = (size.height - dstH) / 2f
                drawImage(
                    image = card,
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt()),
                    filterQuality = FilterQuality.Medium
                )
                val avatar = cardAvatar
                val ring = cardRing
                if (avatar != null && ring != null) {
                    val center = Offset(left + ring.centerX * dstW, top + ring.centerY * dstH)
                    val radius = ring.radius * dstW * 0.95f
                    val hole = Path().apply {
                        addOval(Rect(center - Offset(radius, radius), Size(radius * 2f, radius * 2f)))
                    }
                    val side = min(avatar.width, avatar.height)
                    clipPath(hole) {
                        drawImage(
                            image = avatar,
                            srcOffset = IntOffset((avatar.width - side) / 2, (avatar.height - side) / 2),
                            srcSize = IntSize(side, side),
                            dstOffset = IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt()),
                            dstSize = IntSize((radius * 2f).roundToInt(), (radius * 2f).roundToInt()),
                            filterQuality = FilterQuality.Medium
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.56f),
                            Color.Black.copy(alpha = 0.18f),
                            Color.Black.copy(alpha = 0.04f)
                        )
                    )
                )
        )
        content()
    }
}

@Composable
fun RankBadge(path: String?, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    if (path.isNullOrBlank()) return
    val bitmap = remember(path) { AvatarRender.decodeSampled(path, 128)?.asImageBitmap() }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "نشان رتبه",
            modifier = modifier.size(size)
        )
    }
}

@Composable
fun RankedAvatar(
    avatarPath: String?,
    framePath: String?,
    level: Int,
    size: Dp,
    letter: String = "",
    modifier: Modifier = Modifier
) {
    val tier = remember(level) { RankSystem.forLevel(level) }
    val accent = Color(tier.accent)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        MemberAvatar(avatarPath, framePath, size, letter)
        if (framePath.isNullOrBlank()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(1.dp)
                    .border(2.dp, accent.copy(alpha = 0.78f), CircleShape)
            )
        }
    }
}

@Composable
fun GroupCrest(crestItemId: String?, repo: com.kichikan.ak1.data.AppRepository, size: Dp, modifier: Modifier = Modifier) {
    val path = remember(crestItemId) {
        crestItemId?.let { id -> repo.shop.firstOrNull { it.id == id }?.imagePath }
    }
    val bitmap = remember(path) { path?.let { AvatarRender.decodeSampled(it, 256)?.asImageBitmap() } }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "نشان گروه",
            modifier = modifier.size(size).clip(CircleShape)
        )
    }
}

fun rankName(level: Int): String = RankSystem.forLevel(level).name

fun rankTier(level: Int): RankTier = RankSystem.forLevel(level)
