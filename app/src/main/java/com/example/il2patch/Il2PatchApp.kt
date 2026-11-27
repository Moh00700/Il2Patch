package com.example.il2patch

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import java.io.File

/**
 * Il2PatchApp — Application class.
 *
 * This is the FIRST code that runs when Il2Patch opens,
 * before MainActivity / any tab shows.
 *
 * What it does:
 *  1. Applies the saved theme (System / Light / Dark) BEFORE the UI loads,
 *     so you don't get a white flash then dark mode.
 *  2. Holds global state shared by all 5 tabs:
 *     open file, work.bin, RVA on/off, dry-run on/off, undo journal.
 *  3. Creates Il2Patch folders on first launch.
 *  4. Cleans old temp files so you don't patch the wrong file.
 *
 * Manifest: AndroidManifest.xml -> android:name=".Il2PatchApp"
 * If you delete this file, you MUST also delete that line,
 * or the app will crash on launch with ClassNotFoundException.
 */
class Il2PatchApp : Application() {

    companion object {
        private const val TAG = "Il2PatchApp"
        private const val PREFS = "il2patch_settings"

        // Keys — MUST match MainActivity / Settings tab
        const val KEY_THEME = "theme"                 // "system" | "light" | "dark"
        const val KEY_RVA = "use_rva"                 // true = Il2CppDumper addresses
        const val KEY_DRY_RUN = "dry_run"             // true = Patch List only checks
        const val KEY_WRITE_THROUGH = "write_through" // true = save each patch instantly

        /** Global access — MainActivity uses Il2PatchApp.prefs / .state */
        lateinit var instance: Il2PatchApp
            private set

        val prefs: SharedPreferences
            get() = instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** Shared state for all 5 tabs. */
        val state = AppState
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Theme FIRST — before any Activity is created
        applySavedTheme()

        // 2. Folders
        createFolders()

        // 3. Clean old temp files from last session
        cleanupTempFiles()

        Log.i(TAG, "Il2Patch started — night mode=${instance.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)}")
    }

    // ------------------------------------------------------------
    // 1. Theme
    // ------------------------------------------------------------

    private fun applySavedTheme() {
        // ONE theme source: MainActivity reads prefs "ui" / int "night", and so do we.
        // Old code read "il2patch_settings" / string "theme" here — a SECOND source that
        // MainActivity then overwrote a moment later, so the app could flash the wrong
        // theme for one frame on launch. Now both read the same value.
        val mode = instance.getSharedPreferences("ui", Context.MODE_PRIVATE)
            .getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** Called from Settings tab when user changes theme. Same "ui"/"night" source.
        Named setAppTheme (NOT setTheme): Application already has setTheme(Int) —
        using that name hides the supertype member and the compiler rejects it. */
    fun setAppTheme(mode: Int) {
        instance.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putInt("night", mode).apply()
        applySavedTheme()
    }

    // ------------------------------------------------------------
    // 2. Folders
    // ------------------------------------------------------------

    /** App-private folders. Never in Downloads — patched files stay private. */
    val patchedDir: File get() = File(filesDir, "patched")
    val cacheDirIl2: File get() = File(cacheDir, "il2patch")
    val logsDir: File get() = File(filesDir, "logs")

    private fun createFolders() {
        try {
            patchedDir.mkdirs()
            cacheDirIl2.mkdirs()
            logsDir.mkdirs()
        } catch (e: Exception) {
            Log.w(TAG, "createFolders failed: ${e.message}")
        }
    }

    // ------------------------------------------------------------
    // 3. Cleanup — don't patch a stale file
    // ------------------------------------------------------------

    private fun cleanupTempFiles() {
        try {
            // Old work.bin from a crashed session — MainActivity will
            // re-create it when the user opens a file again.
            // We do NOT delete the user's open file, only our temp copy.
            // App just launched, so nothing is open yet — any stale work.bin is safe to delete.
            val staleWork = File(cacheDir, "work.bin")
            if (staleWork.exists()) {
                staleWork.delete()
            }
            // Old metadata copies older than 7 days
            val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            cacheDirIl2.listFiles()?.forEach { f ->
                if (f.lastModified() < cutoff) {
                    if (f.isDirectory) f.deleteRecursively() else f.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "cleanup failed: ${e.message}")
        }
    }
}
