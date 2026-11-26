package com.scamsense.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.scamsense.app.engine.RiskLevel

// Brand — a calm, trustworthy blue. All text/background pairs meet WCAG AA (most AAA).
val Brand = Color(0xFF1E4FD8)
val BrandDeep = Color(0xFF1638A8)
val BrandLight = Color(0xFF8FB0FF)

val InkLight = Color(0xFF0F172A)
val InkMutedLight = Color(0xFF475569)
val SurfaceLight = Color(0xFFFFFFFF)
val BackgroundLight = Color(0xFFF4F6FB)
val OutlineLight = Color(0xFFD5DBE7)

val InkDark = Color(0xFFE8EDF7)
val InkMutedDark = Color(0xFFA7B2C7)
val SurfaceDark = Color(0xFF131C31)
val BackgroundDark = Color(0xFF0B1220)
val OutlineDark = Color(0xFF2A3754)

@Immutable
data class RiskColors(val main: Color, val onMain: Color, val container: Color, val onContainer: Color)

private val SafeLight = RiskColors(Color(0xFF15803D), Color.White, Color(0xFFDCFCE7), Color(0xFF14532D))
private val WarnLight = RiskColors(Color(0xFFB45309), Color.White, Color(0xFFFEF3C7), Color(0xFF78350F))
private val DangerLight = RiskColors(Color(0xFFC81E1E), Color.White, Color(0xFFFEE2E2), Color(0xFF7F1D1D))

// Dark mode keeps the deep, white-on-colour headers (less glare at night) with dark containers.
private val SafeDark = RiskColors(Color(0xFF15803D), Color.White, Color(0xFF0F2E1C), Color(0xFFBBF7D0))
private val WarnDark = RiskColors(Color(0xFFB45309), Color.White, Color(0xFF33240A), Color(0xFFFDE68A))
private val DangerDark = RiskColors(Color(0xFFC81E1E), Color.White, Color(0xFF3A1212), Color(0xFFFECACA))

@Composable
fun riskColors(level: RiskLevel): RiskColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.3f
    return when (level) {
        RiskLevel.SAFE -> if (dark) SafeDark else SafeLight
        RiskLevel.SUSPICIOUS -> if (dark) WarnDark else WarnLight
        RiskLevel.DANGEROUS -> if (dark) DangerDark else DangerLight
    }
}

fun RiskLevel.label(): String = when (this) {
    RiskLevel.SAFE -> "ความเสี่ยงต่ำ"
    RiskLevel.SUSPICIOUS -> "น่าสงสัย"
    RiskLevel.DANGEROUS -> "อันตราย"
}
