package com.example.il2patch.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.example.il2patch.R

/**
 * Color.kt - Il2Patch Material You colors.
 *
 * Works for BOTH:
 *  1. Views / AppCompatActivity (your MainActivity.kt) -> use the @ColorInt helpers / R.color.*
 *  2. Compose (if you ever add a Compose screen) -> use the Compose Color vals.
 *
 * Do NOT hard-code hex in MainActivity anymore - call Il2PatchTheme.* or these vals.
 * Light + Dark are both here. MainActivity already picks via isNightNow() / prefs.
 *
 * Palette: Material You - deep purple primary, teal success, amber warning, red error.
 * Matches your existing cPrimary() / cSuccess() / cWarning() / cError() in MainActivity,
 * but centralised so every tab uses the SAME colors.
 */
object Il2PatchColors {
    // ---- Brand / Primary (Material You) ----
    val Primary = Color(0xFF6750A4)
    val PrimaryDark = Color(0xFFD0BCFF)
    val OnPrimary = Color(0xFFFFFFFF)
    val OnPrimaryDark = Color(0xFF381E72)
    val PrimaryContainer = Color(0xFFEADDFF)
    val PrimaryContainerDark = Color(0xFF4F378B)
    val OnPrimaryContainer = Color(0xFF21005D)
    val OnPrimaryContainerDark = Color(0xFFEADDFF)

    // ---- Surface / Background ----
    val Surface = Color(0xFFFFFBFE)
    val SurfaceDark = Color(0xFF1C1B1F)
    val SurfaceVariant = Color(0xFFE7E0EC)
    val SurfaceVariantDark = Color(0xFF49454F)
    val OnSurface = Color(0xFF1C1B1F)
    val OnSurfaceDark = Color(0xFFE6E0E9)
    val OnSurfaceVariant = Color(0xFF49454F)
    val OnSurfaceVariantDark = Color(0xFFCAC4D0)

    // ---- Status colors - THESE are what MainActivity uses for text coloring ----
    // Green = patched / success / ready, Amber = warning / careful, Red = error / danger
    val Success = Color(0xFF1E8E3E)
    val SuccessDark = Color(0xFF81C995)
    val SuccessContainer = Color(0xFFCEEBD5)
    val Warning = Color(0xFFB06000)
    val WarningDark = Color(0xFFFFC46B)
    val WarningContainer = Color(0xFFFFE7B8)
    val Error = Color(0xFFB3261E)
    val ErrorDark = Color(0xFFF2B8B5)
    val ErrorContainer = Color(0xFFF9DEDC)

    // ---- Category colors (match MainActivity catColor()) ----
    val CatResources = Color(0xFF0B57D0)
    val CatAds = Color(0xFFB3261E)
    val CatPremium = Color(0xFFAA00A2)
    val CatGodMode = Color(0xFF1E8E3E)
    val CatDamage = Color(0xFFD93025)
    val CatDefault = Color(0xFF6750A4)

    // ---- Android @ColorInt helpers for Views (MainActivity) ----
    // Usage in MainActivity: Il2PatchColors.primaryInt(context)
    @ColorInt fun primaryInt(ctx: Context): Int = resolve(ctx, isNight(ctx), Primary, PrimaryDark)
    @ColorInt fun onSurfaceInt(ctx: Context): Int = resolve(ctx, isNight(ctx), OnSurface, OnSurfaceDark)
    @ColorInt fun successInt(ctx: Context): Int = resolve(ctx, isNight(ctx), Success, SuccessDark)
    @ColorInt fun warningInt(ctx: Context): Int = resolve(ctx, isNight(ctx), Warning, WarningDark)
    @ColorInt fun errorInt(ctx: Context): Int = resolve(ctx, isNight(ctx), Error, ErrorDark)
    @ColorInt fun surfaceVariantInt(ctx: Context): Int = resolve(ctx, isNight(ctx), SurfaceVariant, SurfaceVariantDark)

    fun isNight(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    @ColorInt private fun resolve(ctx: Context, night: Boolean, light: Color, dark: Color): Int {
        val c = if (night) dark else light
        return android.graphics.Color.argb(
            (c.alpha * 255).toInt(),
            (c.red * 255).toInt(),
            (c.green * 255).toInt(),
            (c.blue * 255).toInt()
        )
    }
}
