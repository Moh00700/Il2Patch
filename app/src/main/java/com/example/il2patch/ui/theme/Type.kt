package com.example.il2patch.ui.theme

import android.content.Context
import android.graphics.Typeface

/**
 * Type.kt - Il2Patch fonts, VIEWS-ONLY.
 * No Compose imports. Safe for GitHub Workflows build.
 *
 * - Title: bold, for tab headers / card titles
 * - Body: normal, for explanations / helper texts
 * - BodyMedium: semi-bold, for labels / buttons
 * - Mono: monospace, for hex / addresses / patch bytes - NEVER use body font for hex
 *
 * Demo Combo 3: Space Grotesk titles, Manrope body, JetBrains Mono code.
 * Put the .ttf files in res/font/ (space_grotesk_bold, manrope_regular,
 * manrope_semibold, jetbrains_mono_regular). If a file is missing, falls
 * back to the system font — never crashes.
 */
object Il2PatchType {
    private val cache = mutableMapOf<String, Typeface>()
    private fun load(ctx: Context, resName: String, fallback: Typeface): Typeface = cache.getOrPut(resName) {
        try {
            val id = ctx.resources.getIdentifier(resName, "font", ctx.packageName)
            if (id != 0) androidx.core.content.res.ResourcesCompat.getFont(ctx, id) ?: fallback else fallback
        } catch (e: Exception) { fallback }
    }
    fun titleTypeface(ctx: Context): Typeface = load(ctx, "space_grotesk_bold", Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
    fun bodyTypeface(ctx: Context): Typeface = load(ctx, "manrope_regular", Typeface.create(Typeface.DEFAULT, Typeface.NORMAL))
    fun bodyMediumTypeface(ctx: Context): Typeface = load(ctx, "manrope_semibold", Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
    fun monoTypeface(ctx: Context): Typeface = load(ctx, "jetbrains_mono_regular", Typeface.MONOSPACE)
}
