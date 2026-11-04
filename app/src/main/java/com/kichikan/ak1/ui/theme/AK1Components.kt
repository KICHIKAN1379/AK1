package com.kichikan.ak1.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Style 2: neon-gradient button (magenta -> violet -> cyan) with a dual neon glow and glossy top. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val glow = if (enabled) Modifier.shadow(14.dp, shape, ambientColor = Neon.Magenta.copy(alpha = 0.55f), spotColor = Neon.Cyan.copy(alpha = 0.75f)) else Modifier
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = Motion.Press,
        label = "button-press"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(glow),
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        contentColor = if (enabled) Neon.Navy else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        interactionSource = interaction
    ) {
        val fill = if (enabled) LocalButtonBrush.current else Brush.linearGradient(listOf(Color(0x1FFFFFFF), Color(0x1FFFFFFF)))
        Box(
            Modifier
                .background(if (enabled) MatteFill else Color.Transparent, shape)
                .then(if (enabled) Modifier.background(fill, shape).alpha(0.94f) else Modifier.background(fill, shape))
                .then(
                    if (enabled) Modifier.background(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent, Color.Transparent)),
                        shape
                    ) else Modifier
                )
                .defaultMinSize(minHeight = 42.dp)
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, content = content)
            }
        }
    }
}

/** Frosted layer: a dark, mostly opaque veil so the background behind cards and buttons reads softly. */
private val MatteFill: Color = Color(0xEB0A0622)

private fun glassFill(accent: Color?) = Brush.linearGradient(
    listOf(
        (accent ?: Neon.Violet).copy(alpha = if (accent != null) 0.26f else 0.16f),
        Color.White.copy(alpha = 0.04f),
        (accent ?: Neon.Cyan).copy(alpha = 0.08f)
    )
)

private fun glassEdge(accent: Color?) = BorderStroke(
    1.dp,
    Brush.linearGradient(
        listOf(
            (accent ?: Neon.Cyan).copy(alpha = 0.75f),
            Neon.Violet.copy(alpha = 0.30f),
            (accent ?: Neon.Magenta).copy(alpha = 0.55f)
        )
    )
)

/** Style 4: layered-glass card (translucent gradient fill, bright glass edge). */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    colors: CardColors = CardDefaults.cardColors(containerColor = Color.Transparent),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.material3.Card(
        modifier = modifier.background(MatteFill, shape).background(glassFill(accent), shape),
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border ?: glassEdge(accent),
        content = content
    )
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    colors: CardColors = CardDefaults.cardColors(containerColor = Color.Transparent),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = Motion.Press,
        label = "card-press"
    )
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .background(glassFill(accent), shape),
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border ?: glassEdge(accent),
        interactionSource = interaction,
        content = content
    )
}
