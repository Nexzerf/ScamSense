package com.scamsense.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.scamsense.app.R

/** IBM Plex Sans Thai (SIL OFL) — highly legible Thai + Latin, bundled so it looks the same on every device. */
val PlexThai = FontFamily(
    Font(R.font.ibm_plex_sans_thai_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_thai_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_thai_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_thai_bold, FontWeight.Bold),
)

// Thai stacks vowels and tone marks above/below letters, so line heights are ~1.5x
// (vs ~1.25x for Latin) to avoid clipping. Base body size is 18sp for older eyes.
private val lineStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(size: Int, weight: FontWeight, lh: Float = 1.5f) = TextStyle(
    fontFamily = PlexThai,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lh).sp,
    letterSpacing = 0.em,
    lineHeightStyle = lineStyle,
)

val AppTypography = Typography(
    displayLarge = style(48, FontWeight.Bold, 1.2f),
    displayMedium = style(40, FontWeight.Bold, 1.25f),
    displaySmall = style(34, FontWeight.Bold, 1.3f),
    headlineLarge = style(30, FontWeight.Bold, 1.35f),
    headlineMedium = style(26, FontWeight.Bold, 1.4f),
    headlineSmall = style(23, FontWeight.SemiBold, 1.4f),
    titleLarge = style(21, FontWeight.SemiBold),
    titleMedium = style(19, FontWeight.SemiBold),
    titleSmall = style(17, FontWeight.Medium),
    bodyLarge = style(18, FontWeight.Normal, 1.6f),
    bodyMedium = style(16, FontWeight.Normal, 1.55f),
    bodySmall = style(14, FontWeight.Normal),
    labelLarge = style(17, FontWeight.SemiBold, 1.4f),
    labelMedium = style(15, FontWeight.Medium, 1.4f),
    labelSmall = style(13, FontWeight.Medium, 1.4f),
)
