package com.scamsense.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scamsense.app.ui.theme.Electric
import com.scamsense.app.ui.theme.Lime
import com.scamsense.app.ui.theme.Mint
import com.scamsense.app.ui.theme.Navy800
import com.scamsense.app.ui.theme.Navy950
import com.scamsense.app.ui.theme.Sky
import com.scamsense.app.ui.theme.label
import com.scamsense.core.engine.RiskLevel
import kotlin.math.cos
import kotlin.math.sin

/**
 * Deep navy surface lit by two soft glows (electric blue top-right, mint/lime bottom-left).
 * Pure drawing: no blur effect, so it renders the same on Android 6 and 15.
 */
fun Modifier.glowBackdrop(
    top: Color = Navy800,
    bottom: Color = Navy950,
    glowA: Color = Electric,
    glowB: Color = Mint,
): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(top, bottom)))
    drawCircle(
        Brush.radialGradient(listOf(glowA.copy(alpha = 0.55f), Color.Transparent),
            center = Offset(size.width * 0.92f, size.height * 0.05f), radius = size.maxDimension * 0.65f),
        radius = size.maxDimension * 0.65f, center = Offset(size.width * 0.92f, size.height * 0.05f),
    )
    drawCircle(
        Brush.radialGradient(listOf(glowB.copy(alpha = 0.28f), Color.Transparent),
            center = Offset(size.width * 0.05f, size.height * 0.95f), radius = size.maxDimension * 0.55f),
        radius = size.maxDimension * 0.55f, center = Offset(size.width * 0.05f, size.height * 0.95f),
    )
}

/** Frosted card for light pages: white, hairline border, generous radius. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val colors = CardDefaults.cardColors(containerColor = container)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border, elevation = elevation) {
            Column(Modifier.padding(20.dp), content = content)
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border, elevation = elevation) {
            Column(Modifier.padding(20.dp), content = content)
        }
    }
}

/**
 * The brand mark as a glowing orb: halo rings, a sky→mint core and a glassy shield with a
 * check. [glow] tints the halo (risk heroes pass their accent).
 */
@Composable
fun ShieldOrb(modifier: Modifier = Modifier, size: Dp = 160.dp, glow: Color = Sky, pulse: Float = 1f) {
    Canvas(modifier.size(size)) {
        val c = center
        val r = this.size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.35f), Color.Transparent), c, r), r, c)
        for (k in 1..2) drawCircle(Color.White.copy(alpha = 0.10f), r * (0.62f + 0.17f * k) * pulse, c, style = Stroke(1.dp.toPx()))
        val core = r * 0.62f * pulse
        drawCircle(Brush.linearGradient(listOf(Sky.copy(alpha = 0.95f), Mint.copy(alpha = 0.85f), Lime.copy(alpha = 0.75f)),
            start = Offset(c.x - core, c.y - core), end = Offset(c.x + core, c.y + core)), core, c)
        drawCircle(Color.White.copy(alpha = 0.35f), core, c, style = Stroke(1.5.dp.toPx()))

        // shield
        val s = core * 1.25f / 48f
        fun p(x: Float, y: Float) = Offset(c.x + (x - 24f) * s, c.y + (y - 24f) * s)
        val shield = Path().apply {
            p(24f, 3f).let { moveTo(it.x, it.y) }
            p(41f, 9.5f).let { lineTo(it.x, it.y) }
            p(41f, 22f).let { lineTo(it.x, it.y) }
            val a = p(41f, 33f); val b = p(33.5f, 42f); val e = p(24f, 45f)
            cubicTo(a.x, a.y, b.x, b.y, e.x, e.y)
            val a2 = p(14.5f, 42f); val b2 = p(7f, 33f); val e2 = p(7f, 22f)
            cubicTo(a2.x, a2.y, b2.x, b2.y, e2.x, e2.y)
            p(7f, 9.5f).let { lineTo(it.x, it.y) }
            close()
        }
        drawPath(shield, Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.78f)), p(0f, 3f).y, p(0f, 45f).y))
        val check = Path().apply {
            p(16f, 24f).let { moveTo(it.x, it.y) }
            p(22f, 30f).let { lineTo(it.x, it.y) }
            p(32.5f, 18.5f).let { lineTo(it.x, it.y) }
        }
        drawPath(check, Navy800, style = Stroke(4.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Score gauge made of rounded segments on a 220° arc; lit segments run from a soft tint to
 * the risk accent. Animates from 0 to [score].
 */
@Composable
fun SegmentedGauge(
    score: Int,
    level: RiskLevel,
    accent: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 250.dp,
    onColor: Color = Color.White,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(score) {
        anim.snapTo(0f)
        anim.animateTo(score.toFloat(), tween(durationMillis = 1000, easing = FastOutSlowInEasing))
    }
    Box(
        modifier
            .size(width = diameter, height = diameter * 0.78f)
            .semantics { contentDescription = "คะแนนความเสี่ยง $score จาก 100 ระดับ${level.label()}" },
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.size(diameter)) {
            val segments = 22
            val start = 160f
            val sweep = 220f
            val c = center
            val rOut = size.minDimension / 2 - 4.dp.toPx()
            val rIn = rOut - 20.dp.toPx()
            val lit = (anim.value / 100f * segments)
            val w = 8.dp.toPx()
            for (i in 0 until segments) {
                val a = Math.toRadians((start + sweep * (i + 0.5f) / segments).toDouble())
                val o = Offset(c.x + (rOut * cos(a)).toFloat(), c.y + (rOut * sin(a)).toFloat())
                val n = Offset(c.x + (rIn * cos(a)).toFloat(), c.y + (rIn * sin(a)).toFloat())
                val on = i < lit
                val col = if (on) lerp(onColor.copy(alpha = 0.55f), accent, i / (segments - 1f)) else onColor.copy(alpha = 0.13f)
                drawLine(col, n, o, strokeWidth = w, cap = StrokeCap.Round)
            }
        }
        Column(Modifier.padding(top = diameter * 0.30f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(anim.value.toInt().toString(), style = MaterialTheme.typography.displayLarge, color = onColor, fontWeight = FontWeight.Bold)
            Text("คะแนนความเสี่ยง / 100", style = MaterialTheme.typography.labelMedium, color = onColor.copy(alpha = 0.8f))
        }
    }
}
