package com.example.il2patch.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate

/**
 * Theme.kt - Il2Patch Material You theme, VIEWS-ONLY version.
 *
 * Use this one if you build with GitHub Workflows and DON'T use Compose.
 * Zero Compose imports -> cannot fail with "Unresolved reference: material3".
 *
 * For your Views MainActivity (the 1191-line one):
 *   Il2PatchTheme.applySavedTheme(this)  // in onCreate, BEFORE buildUi()
 *   Il2PatchTheme.successInt(this)       // green text
 *   Il2PatchTheme.warningInt(this)       // amber text
 *   Il2PatchTheme.errorInt(this)         // red text
 *   Il2PatchTheme.snackbarBackground(this)
 *
 * Theme values: "system" | "light" | "dark"
 * Stored in SharedPreferences "il2patch_settings" key "theme".
 * MUST match Il2PatchApp.kt KEY_THEME.
 *
 * Replace the previous Theme.kt (the one with Compose imports) with THIS file.
 * Keep Color.kt + Type.kt as-is - they already work for Views.
 * If your Color.kt / Type.kt also have Compose imports, use the Views-only versions below.
 */
object Il2PatchTheme {
    const val PREFS = "il2patch_settings"
    const val KEY_THEME = "theme"

    // ---- Apply saved theme BEFORE UI loads (call in MainActivity.onCreate) ----
    fun applySavedTheme(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = prefs.getString(KEY_THEME, "system") ?: "system"
        val nightMode = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun isNight(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // ---- Views: color helpers - use these INSTEAD of cPrimary() etc. in MainActivity ----
    @ColorInt fun primary(ctx: Context) = Il2PatchColors.primaryInt(ctx)
    @ColorInt fun onSurface(ctx: Context) = Il2PatchColors.onSurfaceInt(ctx)
    @ColorInt fun successInt(ctx: Context) = Il2PatchColors.successInt(ctx)
    @ColorInt fun warningInt(ctx: Context) = Il2PatchColors.warningInt(ctx)
    @ColorInt fun errorInt(ctx: Context) = Il2PatchColors.errorInt(ctx)
    @ColorInt fun surfaceVariant(ctx: Context) = Il2PatchColors.surfaceVariantInt(ctx)

    // Snackbar colors - dark bg in light mode, light bg in dark mode (readable in both)
    @ColorInt fun snackbarBackground(ctx: Context): Int =
        if (isNight(ctx)) android.graphics.Color.parseColor("#E6E0E9") else android.graphics.Color.parseColor("#1C1B1F")
    @ColorInt fun snackbarText(ctx: Context): Int =
        if (isNight(ctx)) android.graphics.Color.parseColor("#1C1B1F") else android.graphics.Color.parseColor("#FFFFFF")
    @ColorInt fun snackbarAction(ctx: Context): Int =
        if (isNight(ctx)) android.graphics.Color.parseColor("#6750A4") else android.graphics.Color.parseColor("#D0BCFF")
}
