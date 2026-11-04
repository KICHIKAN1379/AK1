package com.kichikan.ak1.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kichikan.ak1.domain.model.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import com.kichikan.ak1.ui.theme.Button
import com.kichikan.ak1.ui.theme.Card
import com.kichikan.ak1.ui.theme.Motion
import com.kichikan.ak1.ui.theme.Neon
import com.kichikan.ak1.data.AppRepository

/**
 * First-run flow: a welcome screen, then a short setup step that names the ring.
 *
 * It reports success through [onReady] instead of mutating a counter, because the caller's
 * early return used to mean the counter was never read, so the first tap looked like it did
 * nothing and the second one crashed on `check(ring == null)`.
 */
@Composable
internal fun WelcomeScreen(repo: AppRepository, onReady: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var ringName by remember { mutableStateOf("ابوتراب") }
    var username by remember { mutableStateOf("مربی") }
    var error by remember { mutableStateOf<String?>(null) }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(step) {
        enter.snapTo(0f)
        enter.animateTo(1f, tween(Motion.Slow, easing = Motion.Emphasized))
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 32.dp)
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * 28.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (step == 0) {
                WelcomeLogo()
                Spacer(Modifier.height(28.dp))
                Text(
                    "خوش آمدید",
                    style = MaterialTheme.typography.displaySmall,
                    color = androidx.compose.ui.graphics.Color.White
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "مجموعه فرهنگی تربیتی ابوتراب علیه السلام تقدیم می‌کند",
                    style = MaterialTheme.typography.titleMedium,
                    color = Neon.Gold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(44.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WelcomeChip("کاملاً آفلاین", Neon.Green, Icons.Filled.WorkspacePremium, Modifier.weight(1f))
                    WelcomeChip("مدیریت اعضا", Neon.Cyan, Icons.Filled.Groups, Modifier.weight(1f))
                    WelcomeChip("حضور و جلسات", Neon.Violet, Icons.Filled.Star, Modifier.weight(1f))
                }
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = { step = 1 },
                    modifier = Modifier.fillMaxWidth().height(58.dp)
                ) {
                    Text("ورود", style = MaterialTheme.typography.titleLarge, color = Neon.Navy)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Filled.Login, contentDescription = null, tint = Neon.Navy)
                }
            } else {
                Text("مشخصات حلقه", style = MaterialTheme.typography.headlineSmall, color = androidx.compose.ui.graphics.Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "این اطلاعات فقط روی همین گوشی ذخیره می‌شود.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Card(Modifier.fillMaxWidth(), accent = Neon.Cyan) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(ringName, { ringName = it }, label = { Text("نام حلقه") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(username, { username = it }, label = { Text("نام کاربری محلی مربی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = {
                        try {
                            repo.createRing(ringName, username)
                            onReady()
                        } catch (e: Exception) {
                            error = e.message ?: "شروع کار انجام نشد"
                        }
                    },
                    enabled = ringName.isNotBlank() && username.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Text("شروع کار آفلاین", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = { step = 0; error = null }) { Text("→ بازگشت") }
            }
        }
    }
}

@Composable
private fun WelcomeChip(text: String, accent: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = accent, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

/** Gold-ringed K1 badge drawn in code, matching the artwork's emblem. */
@Composable
private fun WelcomeLogo() {
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val gold = androidx.compose.ui.graphics.Color(0xFFF5C84B)
            drawCircle(
                Brush.radialGradient(listOf(androidx.compose.ui.graphics.Color(0xFF2B3F8F), androidx.compose.ui.graphics.Color(0xFF0C1438))),
                radius = size.minDimension * 0.40f
            )
            drawCircle(gold, radius = size.minDimension * 0.40f, style = Stroke(width = 5.dp.toPx()))
            drawCircle(gold.copy(alpha = 0.35f), radius = size.minDimension * 0.48f, style = Stroke(width = 1.5.dp.toPx()))
        }
        Text("K1", style = MaterialTheme.typography.displaySmall, color = androidx.compose.ui.graphics.Color(0xFFF5C84B))
    }
}
