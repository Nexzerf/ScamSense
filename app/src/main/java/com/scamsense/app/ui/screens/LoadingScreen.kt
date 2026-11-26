package com.scamsense.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scamsense.app.StartupState
import com.scamsense.app.ui.components.BrandShield
import com.scamsense.app.ui.theme.Brand
import com.scamsense.app.ui.theme.BrandDeep

/**
 * Branded loading screen shown while encrypted data is opened and the detection engine
 * warms up. Continues seamlessly from the system splash (same colour + mark).
 */
@Composable
fun LoadingScreen(state: StartupState, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "loading")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "ring")
    val breathe by t.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val progress by animateFloatAsState(state.progress, tween(350, easing = FastOutSlowInEasing), label = "progress")

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Brand, BrandDeep))),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2
                    for (k in 0..2) {
                        val phase = (ring + k / 3f) % 1f
                        drawCircle(Color.White.copy(alpha = (1f - phase) * 0.22f), radius = r * (0.45f + 0.55f * phase))
                    }
                    drawCircle(Color.White.copy(alpha = 0.35f), radius = r * 0.62f, style = Stroke(2.dp.toPx()))
                    drawArc(
                        Color.White, startAngle = ring * 360f - 90f, sweepAngle = 70f, useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(r * 0.38f, r * 0.38f),
                        size = androidx.compose.ui.geometry.Size(r * 1.24f, r * 1.24f),
                        style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                Box(
                    Modifier.size(104.dp).scale(breathe).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    BrandShield(Modifier.size(76.dp), shieldColor = Color.White, checkColor = Brand)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("ScamSense", style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "ผู้ช่วยรู้ทันมิจฉาชีพ สำหรับคุณและครอบครัว",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.88f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(0.7f).height(8.dp).clip(RoundedCornerShape(8.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(14.dp))
            AnimatedContent(
                targetState = state.status,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "status",
            ) { status ->
                Text(
                    status,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "ทำงานในเครื่อง 100% • ไม่ส่งข้อมูลออกไปไหน",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
