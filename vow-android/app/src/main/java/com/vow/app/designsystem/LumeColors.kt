package com.vow.app.designsystem

import androidx.compose.ui.graphics.Color
import java.util.Calendar

/** Shared palette. Vow accents: Aurora Teal, Horizon Sky, Covenant Violet. */
object LumeColors {
    val AuroraTeal = Color(0xFF2DD4BF)
    val AuroraTealDeep = Color(0xFF0D9488)
    val HorizonSky = Color(0xFF38BDF8)
    val CovenantViolet = Color(0xFFA78BFA)
    val CovenantVioletDeep = Color(0xFF7C3AED)
    /** Ecosystem "sealed" colour (Seal Indigo). */
    val SealIndigo = Color(0xFF8E8BFF)
    val SealIndigoDeep = Color(0xFF5856D6)
}

/** Four-phase circadian surface engine, in step with Kasane's blocks. */
enum class CircadianPhase { MORNING, DAY, EVENING, SLEEP;

    companion object {
        /** MORNING 06-11:59, DAY 12-19:59, EVENING 20-21:59, SLEEP 22-05:59 (sanctuary until the morning window). */
        fun at(hour: Int): CircadianPhase = when (hour) {
            in 6..11 -> MORNING
            in 12..19 -> DAY
            in 20..21 -> EVENING
            else -> SLEEP
        }

        fun now(cal: Calendar = Calendar.getInstance()) = at(cal.get(Calendar.HOUR_OF_DAY))
    }
}

data class LumeSurface(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val line: Color,
    val text: Color,
    val textMuted: Color,
    val dark: Boolean
)

object LumeSurfaces {
    val Washi = LumeSurface(Color(0xFFFAF9F6), Color(0xFFFFFFFF), Color(0xFFF1EFE9), Color(0xFFE2DFD6), Color(0xFF1C1B18), Color(0xFF6B675D), false)
    val Paper = LumeSurface(Color(0xFFF5F7FB), Color(0xFFFFFFFF), Color(0xFFEDF1F7), Color(0xFFD5DEEA), Color(0xFF0F172A), Color(0xFF5B6B80), false)
    val DayDark = LumeSurface(Color(0xFF1C1C1E), Color(0xFF242426), Color(0xFF2C2C2E), Color(0xFF3A3A3C), Color(0xFFF2F2F7), Color(0xFF9A9AA0), true)
    val WarmGraphite = LumeSurface(Color(0xFF181715), Color(0xFF211F1C), Color(0xFF2A2724), Color(0xFF3A3631), Color(0xFFEDE8E0), Color(0xFF9C9588), true)
    /** Sleep Lock sanctuary: desaturated obsidian, unchanged Vow ink. */
    val Obsidian = LumeSurface(Color(0xFF0B0F14), Color(0xFF131922), Color(0xFF1A2230), Color(0xFF263142), Color(0xFFE8EEF6), Color(0xFF8FA0B5), true)

    fun forPhase(phase: CircadianPhase, systemDark: Boolean): LumeSurface = when (phase) {
        CircadianPhase.MORNING -> if (systemDark) DayDark else Washi
        CircadianPhase.DAY -> if (systemDark) DayDark else Paper
        CircadianPhase.EVENING -> WarmGraphite
        CircadianPhase.SLEEP -> Obsidian
    }
}
