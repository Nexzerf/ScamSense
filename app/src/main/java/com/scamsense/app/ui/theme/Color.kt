package com.scamsense.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.scamsense.core.engine.RiskLevel

// "Night shield" palette: deep navy heroes with a soft electric-blue / mint / lime glow,
// on airy blue-white pages. All text/background pairs meet WCAG AA (most AAA).
val Navy950 = Color(0xFF060D22)
val Navy900 = Color(0xFF0A1838)
val Navy800 = Color(0xFF12275A)
val Electric = Color(0xFF3D6BFF)
val Sky = Color(0xFF9CC8FF)
val Mint = Color(0xFF72E6C9)
val Lime = Color(0xFFD5FF4D)

val Brand = Color(0xFF1F5EFF)
val BrandDeep = Navy900
val BrandLight = Color(0xFF9DB6FF)

val InkLight = Color(0xFF0B1530)
val InkMutedLight = Color(0xFF5B6475)
val SurfaceLight = Color(0xFFFFFFFF)
val BackgroundLight = Color(0xFFF4F5F7)
val OutlineLight = Color(0xFFD6DAE1)

val InkDark = Color(0xFFE9EEF9)
val InkMutedDark = Color(0xFFA9B5CF)
val SurfaceDark = Color(0xFF101D3E)
val BackgroundDark = Navy950
val OutlineDark = Color(0xFF26375F)

/** Deep two-tone background + glow accent for a verdict hero (text on it is white). */
@Immutable
data class RiskHero(val top: Color, val bottom: Color, val accent: Color)

fun riskHero(level: RiskLevel): RiskHero = when (level) {
    RiskLevel.SAFE -> RiskHero(Color(0xFF04201E), Color(0xFF0C5A4C), Mint)
    RiskLevel.SUSPICIOUS -> RiskHero(Color(0xFF231404), Color(0xFF7A430A), Color(0xFFFFC651))
    RiskLevel.DANGEROUS -> RiskHero(Color(0xFF2A0610), Color(0xFF8A1830), Color(0xFFFF7A7A))
}

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
