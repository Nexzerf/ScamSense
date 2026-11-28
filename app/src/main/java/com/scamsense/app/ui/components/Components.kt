package com.scamsense.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scamsense.core.engine.RiskLevel
import com.scamsense.app.ui.theme.label
import com.scamsense.app.ui.theme.riskColors

/** Brand mark drawn in code (crisp at any size, no bitmap). */
@Composable
fun BrandShield(modifier: Modifier = Modifier, shieldColor: Color = Color.White, checkColor: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val s = minOf(w, h) / 48f
        val ox = (w - 48f * s) / 2f
        val oy = (h - 48f * s) / 2f
        fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
        val shield = Path().apply {
            moveTo(p(24f, 2f).x, p(24f, 2f).y)
            lineTo(p(42f, 8.8f).x, p(42f, 8.8f).y)
            lineTo(p(42f, 21.8f).x, p(42f, 21.8f).y)
            cubicTo(p(42f, 33f).x, p(42f, 33f).y, p(34.5f, 42.4f).x, p(34.5f, 42.4f).y, p(24f, 45.5f).x, p(24f, 45.5f).y)
            cubicTo(p(13.5f, 42.4f).x, p(13.5f, 42.4f).y, p(6f, 33f).x, p(6f, 33f).y, p(6f, 21.8f).x, p(6f, 21.8f).y)
            lineTo(p(6f, 8.8f).x, p(6f, 8.8f).y)
            close()
        }
        drawPath(shield, shieldColor)
        val check = Path().apply {
            moveTo(p(15.5f, 24f).x, p(15.5f, 24f).y)
            lineTo(p(21.8f, 30.3f).x, p(21.8f, 30.3f).y)
            lineTo(p(33f, 18.2f).x, p(33f, 18.2f).y)
        }
        drawPath(check, checkColor, style = Stroke(width = 4.5f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Score gauge (segmented arc); kept for existing call sites. */
@Composable
fun RiskGauge(score: Int, level: RiskLevel, modifier: Modifier = Modifier, diameter: Dp = 250.dp, onColor: Color = Color.White) {
    SegmentedGauge(score, level, com.scamsense.app.ui.theme.riskHero(level).accent, modifier, diameter, onColor)
}

@Composable
fun LevelBadge(level: RiskLevel, modifier: Modifier = Modifier) {
    val c = riskColors(level)
    Surface(modifier, shape = CircleShape, color = c.container, contentColor = c.onContainer) {
        Text(
            level.label(),
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Primary call-to-action: at least 64dp tall, icon + label (never icon-only). */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun BigOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.5.dp, contentColor.copy(alpha = 0.45f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
    }
}

/** Large tappable tile for the home grid: frosted card, gradient icon chip. */
@Composable
fun ActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 156.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.06f)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

/** Pulsing shield shown while a check runs. */
@Composable
fun AnalyzingIndicator(modifier: Modifier = Modifier, label: String = "กำลังตรวจสอบ…") {
    val t = rememberInfiniteTransition(label = "analyzing")
    val pulse by t.animateFloat(0.9f, 1.08f, infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "ring")
    val primary = MaterialTheme.colorScheme.primary
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = label }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawArc(primary, ring * 360f, 80f, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(4.dp.toPx(), 4.dp.toPx()), size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()))
                drawCircle(primary.copy(alpha = (1f - ring) * 0.25f), radius = r * (0.55f + 0.4f * ring))
            }
            Box(Modifier.size(132.dp).clip(CircleShape).glowBackdrop(), contentAlignment = Alignment.Center) {
                ShieldOrb(size = 124.dp, pulse = pulse)
            }
        }
        Spacer(Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "ตรวจคำอันตราย ลิงก์ เบอร์โทร และเทียบกับกลโกงที่รู้จัก",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** Info row with a leading round icon, used for reasons and advice. */
@Composable
fun IconRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (!body.isNullOrBlank()) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
    }
}
