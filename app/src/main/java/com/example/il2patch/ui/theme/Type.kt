package com.example.il2patch.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type.kt - Il2Patch fonts / typography.
 *
 * Your MainActivity is Views, so it uses fontTitle() / fontBody() / fontMono().
 * This file is the SINGLE source of truth for those - so Views + Compose use the same fonts.
 *
 * - Title: bold, for tab headers / card titles
 * - Body: normal, for explanations / helper texts
 * - BodyMedium: semi-bold, for labels / buttons
 * - Mono: monospace, for hex / addresses / patch bytes - NEVER use body font for hex
 *
 * No custom .ttf needed - uses system fonts so APK stays small and works on Android 8.0+ (minSdk 26).
 */
object Il2PatchType {
    // ---- Compose Typography (for any future Compose screen) ----
    val TitleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
    val TitleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp)
    val BodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val BodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
    val LabelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
    val MonoSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val MonoMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)

    // ---- Views helpers (used by MainActivity) ----
    fun titleTypeface(ctx: Context): Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    fun bodyTypeface(ctx: Context): Typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    fun bodyMediumTypeface(ctx: Context): Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    fun monoTypeface(ctx: Context): Typeface = Typeface.MONOSPACE
}
