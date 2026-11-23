package com.example.il2patch.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt

/**
 * Color.kt - Il2Patch Material You colors, VIEWS-ONLY.
 * No Compose imports. Safe for GitHub Workflows build.
 *
 * Do NOT hard-code hex in MainActivity anymore - call Il2PatchTheme.* or these helpers.
 * Light + Dark are both here. MainActivity already picks via isNightNow() / prefs.
 */
object Il2PatchColors {
    // ---- Brand / Primary ----
    @ColorInt const val PRIMARY_LIGHT: Int = 0xFF6750A4.toInt()
    @ColorInt const val PRIMARY_DARK: Int = 0xFFD0BCFF.toInt()
    @ColorInt const val PRIMARY_CONTAINER_LIGHT: Int = 0xFFEADDFF.toInt()
    @ColorInt const val PRIMARY_CONTAINER_DARK: Int = 0xFF4F378B.toInt()
    @ColorInt const val ON_PRIMARY_LIGHT: Int = 0xFFFFFFFF.toInt()
    @ColorInt const val ON_PRIMARY_DARK: Int = 0xFF381E72.toInt()

    // ---- Surface ----
    @ColorInt const val SURFACE_LIGHT: Int = 0xFFFFFBFE.toInt()
    @ColorInt const val SURFACE_DARK: Int = 0xFF1C1B1F.toInt()
    @ColorInt const val SURFACE_VARIANT_LIGHT: Int = 0xFFE7E0EC.toInt()
    @ColorInt const val SURFACE_VARIANT_DARK: Int = 0xFF49454F.toInt()
    @ColorInt const val ON_SURFACE_LIGHT: Int = 0xFF1C1B1F.toInt()
    @ColorInt const val ON_SURFACE_DARK: Int = 0xFFE6E0E9.toInt()

    // ---- Status: Green = patched / success, Amber = warning, Red = error ----
    @ColorInt const val SUCCESS_LIGHT: Int = 0xFF1E8E3E.toInt()
    @ColorInt const val SUCCESS_DARK: Int = 0xFF81C995.toInt()
    @ColorInt const val WARNING_LIGHT: Int = 0xFFB06000.toInt()
    @ColorInt const val WARNING_DARK: Int = 0xFFFFC46B.toInt()
    @ColorInt const val ERROR_LIGHT: Int = 0xFFB3261E.toInt()
    @ColorInt const val ERROR_DARK: Int = 0xFFF2B8B5.toInt()

    fun isNight(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    @ColorInt fun primaryInt(ctx: Context): Int = if (isNight(ctx)) PRIMARY_DARK else PRIMARY_LIGHT
    @ColorInt fun onSurfaceInt(ctx: Context): Int = if (isNight(ctx)) ON_SURFACE_DARK else ON_SURFACE_LIGHT
    @ColorInt fun successInt(ctx: Context): Int = if (isNight(ctx)) SUCCESS_DARK else SUCCESS_LIGHT
    @ColorInt fun warningInt(ctx: Context): Int = if (isNight(ctx)) WARNING_DARK else WARNING_LIGHT
    @ColorInt fun errorInt(ctx: Context): Int = if (isNight(ctx)) ERROR_DARK else ERROR_LIGHT
    @ColorInt fun surfaceVariantInt(ctx: Context): Int = if (isNight(ctx)) SURFACE_VARIANT_DARK else SURFACE_VARIANT_LIGHT
}
