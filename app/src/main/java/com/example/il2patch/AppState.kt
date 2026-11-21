package com.example.il2patch

import android.net.Uri
import java.io.File

/** Process-wide state, so the loaded file survives activity recreation (e.g. theme switch). */
object AppState {
    var srcUri: Uri? = null
    var srcName: String = ""
    var apkFile: File? = null
    var apkEntry: String? = null
    var patcher: Patcher? = null
    var elf: ElfInfo? = null
    var info: String = ""
    var status: String = "No file loaded"
    var log: String = ""

    // Il2Cpp dump
    var metaFile: File? = null
    var metaName: String = ""
    var dump: Il2CppAnalyzer? = null
    var suggestions: List<Suggestion> = emptyList()
    var dumpReport: String = ""
}
