package com.vow.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Dark ("ink") surfaces
val Ink = Color(0xFF0B0F14)
val InkSurface = Color(0xFF131922)
val InkSurfaceHigh = Color(0xFF1A2230)
val InkLine = Color(0xFF263142)
val InkText = Color(0xFFE8EEF6)
val InkTextMuted = Color(0xFF8FA0B5)

// Light surfaces
val Paper = Color(0xFFF5F7FB)
val PaperSurface = Color(0xFFFFFFFF)
val PaperSurfaceHigh = Color(0xFFEDF1F7)
val PaperLine = Color(0xFFD5DEEA)
val PaperText = Color(0xFF0F172A)
val PaperTextMuted = Color(0xFF5B6B80)

// Accents
// Vow accent: deep cyan (light) / soft cyan (dark)
val VowCyan = Color(0xFF0B6E8A)
val VowCyanDark = Color(0xFF6CCBE0)
val VowSky = Color(0xFF38BDF8)
val VowSeal = Color(0xFFA78BFA)
val VowSealDeep = Color(0xFF7C3AED)
val VowAmber = Color(0xFFFBBF24)
val VowGreen = Color(0xFF4ADE80)
val VowRose = Color(0xFFFB7185)

val AuroraColors = listOf(VowCyanDark, VowSky, VowSeal)
val AuroraBrush = Brush.linearGradient(AuroraColors)
val SealBrush = Brush.linearGradient(listOf(VowSealDeep, VowSeal))
