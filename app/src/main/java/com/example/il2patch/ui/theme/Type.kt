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
 * No custom .ttf needed - uses system fonts so APK stays small and works on Android 8.0+ (minSdk 26).
 */
object Il2PatchType {
    fun titleTypeface(ctx: Context): Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    fun bodyTypeface(ctx: Context): Typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    fun bodyMediumTypeface(ctx: Context): Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    fun monoTypeface(ctx: Context): Typeface = Typeface.MONOSPACE
}
