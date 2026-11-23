package com.example.il2patch.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Theme.kt - Il2Patch Material You theme, centralised.
 *
 * THIS is what fixes the "theme in 3 places" problem.
 * Before: MainActivity had cPrimary() / cSuccess() etc. + Il2PatchApp had its own theme code.
 * Now: EVERYTHING calls Il2PatchTheme.* - one place to change colors.
 *
 * For your Views MainActivity (the 1191-line one):
 *   Il2PatchTheme.applySavedTheme(this)  // in onCreate, BEFORE buildUi()
 *   Il2PatchTheme.successInt(this)       // green text
 *   Il2PatchTheme.warningInt(this)       // amber text
 *   Il2PatchTheme.errorInt(this)         // red text
 *   Il2PatchTheme.snackbarBackground(this)
 *
 * For Compose (if you add a Compose screen later):
 *   Il2PatchTheme { ...your content... }
 *
 * Theme values: "system" | "light" | "dark" - stored in SharedPreferences "il2patch_settings" key "theme".
 * MUST match Il2PatchApp.kt KEY_THEME.
 */
object Il2PatchTheme {
    const val PREFS = "il2patch_settings"
    const val KEY_THEME = "theme"

    private val LightScheme = lightColorScheme(
        primary = Il2PatchColors.Primary,
        onPrimary = Il2PatchColors.OnPrimary,
        primaryContainer = Il2PatchColors.PrimaryContainer,
        onPrimaryContainer = Il2PatchColors.OnPrimaryContainer,
        surface = Il2PatchColors.Surface,
        onSurface = Il2PatchColors.OnSurface,
        surfaceVariant = Il2PatchColors.SurfaceVariant,
        onSurfaceVariant = Il2PatchColors.OnSurfaceVariant,
        error = Il2PatchColors.Error,
        errorContainer = Il2PatchColors.ErrorContainer
    )
    private val DarkScheme = darkColorScheme(
        primary = Il2PatchColors.PrimaryDark,
        onPrimary = Il2PatchColors.OnPrimaryDark,
        primaryContainer = Il2PatchColors.PrimaryContainerDark,
        onPrimaryContainer = Il2PatchColors.OnPrimaryContainerDark,
        surface = Il2PatchColors.SurfaceDark,
        onSurface = Il2PatchColors.OnSurfaceDark,
        surfaceVariant = Il2PatchColors.SurfaceVariantDark,
        onSurfaceVariant = Il2PatchColors.OnSurfaceVariantDark,
        error = Il2PatchColors.ErrorDark,
        errorContainer = Il2PatchColors.ErrorContainer
    )

    // ---- Views: apply saved theme BEFORE UI loads (call in MainActivity.onCreate) ----
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

    // ---- Compose: wrap any Compose screen with this ----
    @Composable
    fun Il2PatchTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = androidx.compose.material3.Typography(
                titleLarge = Il2PatchType.TitleLarge,
                titleMedium = Il2PatchType.TitleMedium,
                bodyLarge = Il2PatchType.BodyLarge,
                bodyMedium = Il2PatchType.BodyMedium,
                labelSmall = Il2PatchType.LabelSmall
            ),
            content = content
        )
    }
}

// Backwards-compat alias: old code used ThemeIl2Patch { } - keep it working
typealias ThemeIl2Patch = Il2PatchTheme
