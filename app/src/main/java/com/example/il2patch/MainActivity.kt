package com.example.il2patch

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.InputType
import android.text.TextUtils
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.R as MR
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.ByteBuffer

/**
 * Il2Patch: hex / assembly patcher for ELF .so files (and .so files inside APKs).
 * Bottom navigation: Start, Find, Patch (Value / Bytes / List / Search), Log.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_PICK = 1
        private const val REQ_SAVE_COPY = 2
        private const val REQ_BUILD_APK = 3
        private const val REQ_LOAD_PATCHES = 4
        private const val REQ_SAVE_PATCHES = 5
        private const val REQ_PICK_META = 6
        private const val REQ_SAVE_JSON = 7
        private const val REQ_SAVE_SUGG = 8
    }

    private val st = AppState
    private lateinit var work: File
    private lateinit var prefs: SharedPreferences
    private var busy = 0

    private lateinit var progress: LinearProgressIndicator
    private lateinit var statusText: TextView
    private lateinit var nav: BottomNavigationView
    private lateinit var pages: List<View>
    private val navIds = listOf(R.id.nav_start, R.id.nav_find, R.id.nav_patch, R.id.nav_settings, R.id.nav_log)
    private var curPage = 0
    private lateinit var hintText: TextView

    private lateinit var targetCard: MaterialCardView
    private lateinit var offsetField: Field
    private lateinit var rvaSwitch: MaterialSwitch
    private lateinit var segGroup: MaterialButtonToggleGroup
    private val segIds = ArrayList<Int>()
    private lateinit var sections: List<View>
    private var curSeg = 0

    private lateinit var infoText: TextView
    private lateinit var autoSwitch: MaterialSwitch

    private lateinit var bytesField: Field
    private lateinit var lenField: Field
    private lateinit var patchResult: TextView

    private lateinit var typeGroup: MaterialButtonToggleGroup
    private val typeIds = ArrayList<Int>()
    private val types = listOf("long", "int", "bool", "float", "double")
    private lateinit var valueField: Field
    private lateinit var returnResult: TextView

    private lateinit var batchField: Field
    private lateinit var dryCheck: MaterialSwitch
    private lateinit var batchResult: TextView

    private lateinit var patternField: Field
    private lateinit var searchResult: TextView

    private lateinit var logText: TextView

    private lateinit var metaText: TextView
    private lateinit var sumText: TextView
    private lateinit var dumpSummary: TextView
    private lateinit var includeAllSwitch: MaterialSwitch
    private lateinit var catGroup: MaterialButtonToggleGroup
    private val catIds = ArrayList<Int>()
    private val cats = listOf(
        "Resources", "Ads", "Premium", "Unlocks", "Timers", "Tutorial", "Achievements", "God Mode", "Damage"
    )
    private lateinit var filterField: Field
    private lateinit var sugList: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var moreBtn: MaterialButton
    private var sugLimit = 30

    private val presets = listOf(
        "ARM64: return true" to "mov w0, #1; ret",
        "ARM64: return false / 0" to "mov w0, #0; ret",
        "ARM64: return int 999" to "reti 999",
        "ARM64: return float 1.0" to "retf 1.0",
        "ARM64: NOP" to "nop",
        "ARM64: RET only" to "ret",
        "ARM32: return true" to "mov r0, #1; bx lr",
        "ARM32: return false / 0" to "mov r0, #0; bx lr",
        "ARM32: NOP" to "nop",
        "Any arch: return long 10000" to "return long 10000"
    )

    // ------------------------------------------------------------ small UI helpers

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: CharSequence, style: Int = MR.style.TextAppearance_Material3_BodyMedium) =
        TextView(this).apply {
            this.text = text
            setTextAppearance(style)
        }

    private fun primaryColor() = MaterialColors.getColor(this, MR.attr.colorPrimary, 0xFF4A44D6.toInt())

    /** Card heading. */
    private fun title(t: String) = tv(t, MR.style.TextAppearance_Material3_TitleMedium).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, 0, 0, dp(6))
    }

    /** Small coloured section label above a group of cards. */
    private fun label(t: String) = tv(t.uppercase(), MR.style.TextAppearance_Material3_LabelSmall).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.1f
        setTextColor(primaryColor())
        setPadding(dp(4), dp(8), 0, dp(6))
    }

    private fun hint(t: String) = tv(t, MR.style.TextAppearance_Material3_BodySmall).apply {
        setTextColor(MaterialColors.getColor(this@MainActivity, MR.attr.colorOnSurfaceVariant, 0))
    }

    private fun <T : View> T.top(v: Int): T {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(v) }
        return this
    }

    /** Fade and slide in. */
    private fun View.enter(delay: Long = 0) {
        alpha = 0f
        translationY = dp(14).toFloat()
        animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(260)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun toggle(v: View) {
        if (v.visibility == View.VISIBLE) {
            v.visibility = View.GONE
        } else {
            v.visibility = View.VISIBLE
            v.alpha = 0f
            v.animate().alpha(1f).setDuration(200).start()
        }
    }

    /** kind: 0 = filled, 1 = tonal, 2 = outlined */
    private fun btn(text: String, kind: Int = 0, f: () -> Unit): MaterialButton {
        val b = when (kind) {
            1 -> MaterialButton(ContextThemeWrapper(this, R.style.ThemeOverlay_Il2Patch_Tonal))
            2 -> MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle)
            else -> MaterialButton(this)
        }
        b.text = text
        b.isAllCaps = false
        b.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        b.setOnClickListener { v ->
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
            f()
        }
        return b
    }

    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(8), 0, 0)
        views.forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = dp(8) })
        }
    }

    /** A wide view (weight 1) next to a compact one that keeps its own width, e.g. a long button + "Advanced". */
    private fun rowAsym(main: View, side: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(8), 0, 0)
        addView(main, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(side, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(8) })
    }

    /** Wraps a row of toggle/segmented buttons so it scrolls instead of truncating their text. */
    private fun segRow(group: MaterialButtonToggleGroup, topMargin: Int = 4): View =
        HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(group, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { this.topMargin = dp(topMargin) }
        }

    private fun card(vararg children: View): MaterialCardView {
        val c = MaterialCardView(this, null, MR.attr.materialCardViewFilledStyle)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        children.forEach { col.addView(it) }
        c.addView(col)
        c.radius = dp(20).toFloat()
        c.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
        return c
    }

    private fun sw(text: String, checked: Boolean) = MaterialSwitch(this).apply {
        this.text = text
        isChecked = checked
    }.top(4)

    private fun resultText() = tv("Nothing yet.", MR.style.TextAppearance_Material3_BodySmall).apply {
        typeface = Typeface.MONOSPACE
        textSize = 12f
        setTextIsSelectable(true)
    }

    private fun page(vararg views: View) = ScrollView(this).apply {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        views.forEach { col.addView(it) }
        addView(col)
        clipToPadding = false
    }

    private fun section(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        views.forEach { addView(it) }
    }

    private inner class Field(hint: String, lines: Int = 1, mono: Boolean = false) {
        val layout = TextInputLayout(this@MainActivity)
        val edit = TextInputEditText(layout.context)

        init {
            layout.hint = hint
            if (mono) edit.typeface = Typeface.MONOSPACE
            if (lines > 1) {
                edit.minLines = lines
                edit.gravity = Gravity.TOP
                edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            } else {
                edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            layout.addView(edit)
            layout.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
        }

        var text: String
            get() = edit.text?.toString() ?: ""
            set(v) {
                edit.setText(v)
            }
    }

    private fun toast(msg: String) {
        runOnUiThread {
            val s = Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG)
            if (::nav.isInitialized) s.setAnchorView(nav)
            s.show()
        }
    }

    private fun ui(block: () -> Unit) {
        runOnUiThread { block() }
    }

    private fun show(v: TextView, s: String) {
        runOnUiThread { v.text = s }
    }

    private fun setStatus(s: String) {
        st.status = s
        runOnUiThread { statusText.text = s }
    }

    private fun log(msg: String) {
        st.log = (msg + "\n" + st.log).take(20000)
        runOnUiThread { logText.text = st.log }
    }

    private fun bg(block: () -> Unit) {
        runOnUiThread {
            busy++
            progress.visibility = View.VISIBLE
        }
        Thread {
            try {
                block()
            } catch (e: Throwable) {
                toast("Error: ${e.message ?: e.javaClass.simpleName}")
                log("Error: $e")
            } finally {
                runOnUiThread {
                    busy--
                    if (busy <= 0) progress.visibility = View.INVISIBLE
                }
            }
        }.start()
    }

    // ------------------------------------------------------------ shell: header, bottom navigation

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("ui", MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            prefs.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        )
        super.onCreate(savedInstanceState)
        work = File(filesDir, "work.bin")
        setContentView(buildUi())
        restore(savedInstanceState)
    }

    private fun isNightNow(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun header(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(14), dp(10))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF3B2FC9.toInt(), 0xFF00C2A8.toInt())
            ).apply { cornerRadius = dp(14).toFloat() }
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
        }
        row.addView(logo, LinearLayout.LayoutParams(dp(46), dp(46)))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
        }
        col.addView(tv("Il2Patch", MR.style.TextAppearance_Material3_TitleLarge).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        statusText = hint(st.status).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        col.addView(statusText)
        row.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        return row
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(header())

        progress = LinearProgressIndicator(this).apply {
            isIndeterminate = true
            visibility = View.INVISIBLE
        }
        root.addView(progress, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // shared target (offset + RVA switch), lives on the Patch page
        offsetField = Field("Function address (hex)", 1, true)
        rvaSwitch = sw("RVA from Il2CppDumper / Find (turn off for a raw file offset)", prefs.getBoolean("def_rva", true))
        targetCard = card(offsetField.layout, rvaSwitch)

        // widgets that other pages reference must exist before the pages are built
        autoSwitch = sw("Write each change straight into the original file", prefs.getBoolean("def_auto", true))
        pages = listOf(startPage(), findPage(), patchPage(), settingsPage(), logPage())
        val frame = FrameLayout(this)
        pages.forEach { frame.addView(it, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)) }
        root.addView(frame, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        nav = BottomNavigationView(this).apply {
            inflateMenu(R.menu.nav)
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
            setOnItemSelectedListener { item ->
                val i = navIds.indexOf(item.itemId)
                if (i >= 0 && i != curPage) go(i)
                true
            }
        }
        root.addView(nav, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        go(0, false)
        refreshHint()
        return root
    }

    private fun go(i: Int, animate: Boolean = true) {
        val old = curPage
        curPage = i
        pages.forEachIndexed { j, p ->
            if (j == i) {
                p.visibility = View.VISIBLE
                if (animate && j != old) p.enter()
            } else {
                p.animate().cancel()
                p.visibility = View.GONE
            }
        }
        if (nav.selectedItemId != navIds[i]) nav.selectedItemId = navIds[i]
    }

    /** Opens the Patch page on one of its sections: 0 Value, 1 Bytes, 2 List, 3 Search. */
    private fun goPatch(seg: Int) {
        go(2)
        segGroup.check(segIds[seg])
    }

    private fun showSeg(i: Int) {
        curSeg = i
        sections.forEachIndexed { j, s ->
            if (j == i) {
                if (s.visibility != View.VISIBLE) {
                    s.visibility = View.VISIBLE
                    s.enter()
                }
            } else {
                s.visibility = View.GONE
            }
        }
        targetCard.visibility = if (i == 0 || i == 1) View.VISIBLE else View.GONE
    }

    /** The one-line "what next" guide on the Start page. */
    private fun refreshHint() {
        if (!::hintText.isInitialized) return
        hintText.text = when {
            st.patcher == null -> "Step 1: open libil2cpp.so or the game's APK."
            st.dump == null -> "Step 2: go to Find and tap Analyze. The app lists functions worth patching."
            else -> "Step 3: in Find, tap Apply fix on a function, then save from this page."
        }
    }

    // ------------------------------------------------------------ Start

    private fun startPage(): ScrollView {
        hintText = tv("", MR.style.TextAppearance_Material3_BodyLarge).apply {
            setTextColor(MaterialColors.getColor(this@MainActivity, MR.attr.colorOnPrimaryContainer, 0))
        }
        val hero = card(
            tv("Patch a game in 3 steps", MR.style.TextAppearance_Material3_HeadlineSmall).apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(MaterialColors.getColor(this@MainActivity, MR.attr.colorOnPrimaryContainer, 0))
                setPadding(0, 0, 0, dp(8))
            },
            hintText
        )
        hero.setCardBackgroundColor(MaterialColors.getColor(this, MR.attr.colorPrimaryContainer, 0))

        infoText = hint("No file open.").apply { setPadding(0, dp(12), 0, 0) }
        val src = card(
            title("1  Open your file"),
            hint("A libil2cpp.so, any ELF .so, or an APK (then pick the file inside it)."),
            btn("Open .so or .apk") { pickFile() }.top(12),
            infoText
        )
        val save = card(
            title("Save"),
            autoSwitch,
            row(btn("Save to original", 1) { saveOriginal() }, btn("Save copy as...", 1) { saveCopy() }),
            btn("Build patched APK (APK mode)") { buildApk() }.top(8)
        )
        val hist = card(
            title("Changes"),
            row(btn("Undo last", 2) { undo(false) }, btn("Undo all", 2) { undo(true) })
        )
        return page(hero, src, save, hist)
    }

    // ------------------------------------------------------------ Find (dump + suggestions)

    private fun findPage(): ScrollView {
        metaText = hint(
            if (st.metaName.isEmpty()) "APK: found automatically. For a plain .so, pick global-metadata.dat."
            else "Metadata: ${st.metaName}"
        )
        includeAllSwitch = sw("Include Unity / System assemblies (bigger, slower)", false)
        val adv = section(includeAllSwitch).apply { visibility = View.GONE }
        val files = card(
            title("Metadata"),
            metaText,
            rowAsym(
                btn("Pick metadata file", 1) { pickMeta() },
                btn("Advanced", 2) { toggle(adv) }
            ),
            adv
        )

        sumText = tv(summaryLine(), MR.style.TextAppearance_Material3_BodyLarge)
        dumpSummary = hint(if (st.dumpReport.isEmpty()) "" else st.dumpReport).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            visibility = View.GONE
        }
        val run = card(
            title("Analyze"),
            hint("Reads every class and method and picks the ones that likely control gems / coins, ads or premium."),
            btn("Analyze") { doAnalyze() }.top(12),
            sumText.top(12),
            btn("Show technical details", 2) { toggle(dumpSummary) }.top(8),
            dumpSummary.top(8)
        )

        catGroup = MaterialButtonToggleGroup(this)
        cats.forEach { c ->
            val b = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle)
            b.id = View.generateViewId()
            b.text = c
            b.isAllCaps = false
            b.isCheckable = true
            catGroup.addView(b, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
            catIds.add(b.id)
        }
        catIds.forEach { catGroup.check(it) }
        filterField = Field("Search by name (optional)")
        sugList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        emptyText = hint("Run Analyze to see suggestions.").apply { setPadding(dp(4), dp(8), 0, dp(8)) }
        moreBtn = btn("Show more", 2) {
            sugLimit += 30
            renderSuggestions()
        }
        moreBtn.visibility = View.GONE

        val exp = card(
            title("Export"),
            hint("Save the results as JSON files."),
            row(
                btn("Suggestions only", 1) { exportSuggestions() },
                btn("Everything", 1) { exportJson() }
            )
        )
        val pg = page(
            label("1  Files"), files,
            label("2  Scan"), run,
            label("3  Fix"),
            segRow(catGroup), filterField.layout,
            emptyText, sugList.top(0), moreBtn.top(4),
            btn("Add shown to Batch list", 2) { sendToBatch() }.top(8),
            label("Export"), exp
        )
        catGroup.addOnButtonCheckedListener { _, _, _ ->
            sugLimit = 30
            renderSuggestions()
        }
        filterField.edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                sugLimit = 30
                renderSuggestions()
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        renderSuggestions()
        return pg
    }

    private fun summaryLine(): String {
        if (st.dump == null) return "Nothing analysed yet."
        val sg = st.suggestions
        if (sg.isEmpty()) return "Analysis finished, but no matching functions were found."
        return "Found " + cats.joinToString(", ") { c -> "${sg.count { it.category == c }} ${c.lowercase()}" } +
            " functions."
    }

    private fun catColor(c: String): Int = when (c) {
        "Resources" -> 0xFF2E9E6B.toInt()
        "Ads" -> 0xFFE0702B.toInt()
        "Premium" -> 0xFF8E5BE0.toInt()
        "Unlocks" -> 0xFF2E7DD6.toInt()
        "Timers" -> 0xFF1C9FA6.toInt()
        "Tutorial" -> 0xFF8A6D3B.toInt()
        "Achievements" -> 0xFFC9A227.toInt()
        "God Mode" -> 0xFFC24A4A.toInt()
        "Damage" -> 0xFFB3461E.toInt()
        else -> 0xFF5D5C72.toInt()
    }

    private fun effectText(s: Suggestion): String = when (s.kind) {
        "noop" -> "Disabled: does nothing"
        "bool" -> "Always returns " + s.value
        else -> "Always returns " + s.value + " (" + s.kind + ")"
    }

    private fun suggestionCard(s: Suggestion): View {
        val col = catColor(s.category)
        val pill = tv(s.category.uppercase(), MR.style.TextAppearance_Material3_LabelSmall).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.08f
            setTextColor(col)
            background = GradientDrawable().apply {
                setColor((col and 0x00FFFFFF) or 0x26000000)
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(pill)
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(hint("RVA 0x%X".format(s.rva)).apply { typeface = Typeface.MONOSPACE })
        }
        val name = tv(s.cls + "." + s.method, MR.style.TextAppearance_Material3_TitleSmall).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(8), 0, 0)
        }
        val effect = tv(effectText(s), MR.style.TextAppearance_Material3_BodyMedium).apply {
            setTextColor(primaryColor())
        }
        lateinit var ap: MaterialButton
        ap = btn("Apply fix") { quickApply(s) { ap.text = "Applied ✓" } }
        val edit = btn("Edit", 2) { applySuggestion(s) }
        return card(
            top, name, effect,
            hint("returns " + s.ret + "   " + s.note).top(2),
            row(ap, edit)
        )
    }

    private fun renderSuggestions() {
        if (!::sugList.isInitialized) return
        sugList.removeAllViews()
        val list = filteredSuggestions()
        if (list.isEmpty()) {
            emptyText.text = when {
                st.dump == null -> "Run Analyze to see suggestions."
                st.suggestions.isEmpty() -> "No non-void functions with gem, ad or premium names were found."
                else -> "No matches. Change the filters."
            }
            emptyText.visibility = View.VISIBLE
            moreBtn.visibility = View.GONE
            return
        }
        emptyText.visibility = View.GONE
        list.take(sugLimit).forEachIndexed { i, s ->
            val c = suggestionCard(s)
            sugList.addView(c)
            if (i < 10) c.enter(i * 30L)
        }
        if (list.size > sugLimit) {
            moreBtn.text = "Show more (" + (list.size - sugLimit) + " left)"
            moreBtn.visibility = View.VISIBLE
        } else {
            moreBtn.visibility = View.GONE
        }
    }

    private fun pickMeta() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, REQ_PICK_META)
    }

    private fun extractMetaFromApk(apk: File): File? {
        val out = File(filesDir, "meta_apk.dat")
        java.util.zip.ZipFile(apk).use { z ->
            val e = z.entries().toList().firstOrNull { it.name.endsWith("global-metadata.dat") } ?: return null
            z.getInputStream(e).use { i ->
                FileOutputStream(out).use { o -> copyStream(i, o, "Extracting metadata") }
            }
        }
        return out
    }

    private fun doAnalyze() {
        if (st.patcher == null) {
            toast("Open libil2cpp.so or the APK first")
            go(0)
            return
        }
        val includeAll = includeAllSwitch.isChecked
        bg {
            st.dump?.close()
            st.dump = null
            st.suggestions = emptyList()
            ui { renderSuggestions() }
            var mf = st.metaFile
            val apk = st.apkFile
            if (mf == null && apk != null) {
                mf = extractMetaFromApk(apk)
                if (mf != null) {
                    st.metaFile = mf
                    st.metaName = "global-metadata.dat (from the APK)"
                    ui { metaText.text = "Metadata: ${st.metaName}" }
                }
            }
            if (mf == null) {
                toast("Pick global-metadata.dat first")
                return@bg
            }
            var an: Il2CppAnalyzer? = null
            try {
                an = Il2CppAnalyzer(mf, work)
                an.analyze(includeAll) { setStatus(it) }
                val sg = an.suggestions()
                st.dump = an
                st.suggestions = sg
                st.dumpReport = "Done.\n" + an.diag.toString()
                setStatus("Analysis done: ${an.resolvedMethods} methods")
                log("Analysis done: ${an.resolvedMethods} methods, ${sg.size} suggestions")
                ui {
                    sumText.text = summaryLine()
                    dumpSummary.text = st.dumpReport
                    sugLimit = 30
                    renderSuggestions()
                    refreshHint()
                }
            } catch (e: Exception) {
                val rep = "Analysis failed:\n" + (e.message ?: e.toString()) + "\n\n" + (an?.diag ?: "")
                st.dumpReport = rep
                setStatus("Analysis failed")
                log(rep)
                an?.close()
                ui {
                    sumText.text = "Analysis failed. Details below."
                    dumpSummary.text = rep
                    dumpSummary.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun filteredSuggestions(cat: String? = null): List<Suggestion> {
        val sel = HashSet<String>()
        for (id in catGroup.checkedButtonIds) {
            val i = catIds.indexOf(id)
            if (i >= 0) sel.add(cats[i])
        }
        val q = filterField.text.trim().lowercase()
        return st.suggestions.filter {
            // void functions ("noop": disable/do-nothing patches) aren't shown as suggestion cards --
            // there's no return value for "Apply fix" to set, so they're not a one-tap fix the way
            // the others are. Find one's exact address yourself (Find's search, or a disassembler)
            // and use Patch -> Bytes -> "Deep scan for subtract" to look for what to flip.
            it.kind != "noop" &&
                (if (cat == null) it.category in sel else it.category == cat) &&
                (q.isEmpty() || (it.cls + "." + it.method).lowercase().contains(q))
        }
    }

    /** One-tap patch of a suggestion; the Snackbar offers Undo. */
    private fun quickApply(s: Suggestion, done: () -> Unit) {
        val p = st.patcher ?: return toast("Open a file first")
        val off = toFileOffset(s.rva, true) ?: return toast("Address is not inside the file")
        val data = try {
            Asm.assemble(s.patch(), is64())
        } catch (e: Exception) {
            return toast("Value: ${e.message}")
        }
        if (off < 0 || off + data.size > p.size()) return toast("Patch runs past the end of the file")
        val auto = autoSwitch.isChecked
        bg {
            val old = applyOne(p, off, data, auto)
            log("0x%X  %s.%s -> %s  (was %s)".format(off, s.cls, s.method, s.patch(), Hex.fmt(old)))
            setStatus("${p.journal.size} change(s) this session")
            ui {
                done()
                val sb = Snackbar.make(
                    findViewById(android.R.id.content), "Patched " + s.method, Snackbar.LENGTH_LONG
                )
                sb.setAnchorView(nav)
                sb.setAction("Undo") { undo(false) }
                sb.show()
            }
        }
    }

    /** Fills the Patch page with a suggestion so it can be edited first. */
    private fun applySuggestion(s: Suggestion) {
        offsetField.text = "%X".format(s.rva)
        rvaSwitch.isChecked = true
        if (s.kind == "noop") {
            bytesField.text = "ret"
            goPatch(1)
        } else {
            val i = types.indexOf(s.kind)
            if (i >= 0) typeGroup.check(typeIds[i])
            valueField.text = s.value
            goPatch(0)
        }
        toast("Filled in ${s.cls}.${s.method}. Change it if you like, then apply.")
    }

    private fun sendToBatch() {
        if (st.suggestions.isEmpty()) return toast("Nothing yet. Run Analyze first.")
        val sb = StringBuilder()
        for (cat in cats) {
            val top = filteredSuggestions(cat).take(25)
            if (top.isEmpty()) continue
            sb.append("# --- ").append(cat).append(" (top ").append(top.size).append(", review every line) ---\n")
            for (s in top) {
                sb.append("# ").append(s.cls).append('.').append(s.method).append(s.params)
                    .append("  returns ").append(s.ret).append('\n')
                sb.append(s.batchLine()).append('\n')
            }
        }
        if (sb.isEmpty()) return toast("No matches. Change the filters.")
        val old = batchField.text
        batchField.text = old + (if (old.isEmpty()) "" else "\n") + sb
        goPatch(2)
        toast("Added to the list. Delete lines you do not want and use Dry run first.")
    }

    private fun exportJson() {
        if (st.dump == null) return toast("Run Analyze first")
        createDoc(REQ_SAVE_JSON, "application/json", st.srcName.removeSuffix(".so").removeSuffix(".apk") + "_dump.json")
    }

    private fun exportSuggestions() {
        if (st.dump == null || st.suggestions.isEmpty()) return toast("No suggestions to export yet")
        createDoc(
            REQ_SAVE_SUGG, "application/json",
            st.srcName.removeSuffix(".so").removeSuffix(".apk") + "_suggestions.json"
        )
    }

    // ------------------------------------------------------------ Patch (Value | Bytes | List | Search)

    private fun patchPage(): ScrollView {
        // Value
        typeGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        types.forEach { t ->
            val b = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle)
            b.id = View.generateViewId()
            b.text = t
            b.isAllCaps = false
            typeGroup.addView(b, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
            typeIds.add(b.id)
        }
        typeGroup.check(typeIds[0])
        valueField = Field("Value, e.g. 10000", 1, true)
        returnResult = resultText()
        val value = section(
            card(
                title("Make the function return a value"),
                hint("The app writes the code for you. The function returns the value at once and its old code no longer runs."),
                segRow(typeGroup, 12),
                valueField.layout,
                btn("Patch function") { doReturnValue() }.top(12)
            ),
            card(title("Result"), returnResult)
        )

        // Bytes
        bytesField = Field("New bytes: hex or assembly", 3, true)
        lenField = Field("Bytes to read", 1, true).apply { text = "64" }
        patchResult = resultText()
        val bytes = section(
            card(
                title("Write bytes"),
                hint("Hex like 1F 20 03 D5, or assembly like mov w0, #1; ret"),
                bytesField.layout,
                row(btn("Presets", 2) { showPresets() }, btn("Apply") { doPatch() }),
                btn("Flip + / − at this address (ARM64)", 2) { doFlipAddSub() }.top(8),
                btn("Deep scan for subtract (beta, follows branches/calls, ARM64)", 2) { doScanForSub() }.top(8)
            ),
            card(
                title("Read bytes"),
                lenField.layout,
                btn("Read at address", 1) { doRead() }.top(8)
            ),
            card(title("Result"), patchResult)
        )

        // List (batch)
        batchField = Field("Patch list", 9, true)
        dryCheck = sw("Dry run (check only, change nothing)", prefs.getBoolean("def_dry", false))
        batchResult = resultText()
        val list = section(
            card(
                title("Patch list"),
                hint(
                    "One patch per line, numbers in hex:\n" +
                        "1A2B3C: mov w0, #1; ret\n" +
                        "1A2B50: 1F 20 03 D5\n" +
                        "1A2B60: 00 00 80 52 => 20 00 80 52\n" +
                        "1A2B70: return long 10000\n" +
                        "find FD 7B ?? A9 +4: nop\n" +
                        "# comment"
                ).apply { typeface = Typeface.MONOSPACE },
                batchField.layout,
                dryCheck,
                btn("Run list") { runBatch() }.top(8),
                row(btn("Load", 2) { pickText(REQ_LOAD_PATCHES) }, btn("Save", 2) { saveText() })
            ),
            card(title("Result"), batchResult)
        )

        // Search
        patternField = Field("Bytes with ?? wildcards", 1, true)
        searchResult = resultText()
        val search = section(
            card(
                title("Find a byte pattern"),
                hint("Offsets change with every game update, patterns usually don't. Example: FD 7B ?? A9 ?? ?? 00 91"),
                patternField.layout,
                btn("Search") { doFind() }.top(8)
            ),
            card(title("Matches"), searchResult)
        )

        sections = listOf(value, bytes, list, search)
        segGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        listOf("Value", "Bytes", "List", "Search").forEach { t ->
            val b = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle)
            b.id = View.generateViewId()
            b.text = t
            b.isAllCaps = false
            segGroup.addView(b, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
            segIds.add(b.id)
        }
        targetCard.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        }
        val pg = page(segRow(segGroup, 4), targetCard, *sections.toTypedArray())
        segGroup.addOnButtonCheckedListener { _, id, checked ->
            if (checked) showSeg(segIds.indexOf(id))
        }
        segGroup.check(segIds[0])
        showSeg(0)
        return pg
    }

    // ------------------------------------------------------------ Settings

    private fun settingsPage(): ScrollView {
        val themeGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        val themeIds = ArrayList<Int>()
        val themeModes = listOf(
            "System" to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            "Light" to AppCompatDelegate.MODE_NIGHT_NO,
            "Dark" to AppCompatDelegate.MODE_NIGHT_YES
        )
        themeModes.forEach { (t, _) ->
            val b = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle)
            b.id = View.generateViewId()
            b.text = t
            b.isAllCaps = false
            themeGroup.addView(b, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
            themeIds.add(b.id)
        }
        val curMode = prefs.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val curIdx = themeModes.indexOfFirst { it.second == curMode }.let { if (it < 0) 0 else it }
        themeGroup.check(themeIds[curIdx])
        themeGroup.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            val i = themeIds.indexOf(id)
            if (i < 0) return@addOnButtonCheckedListener
            val mode = themeModes[i].second
            prefs.edit().putInt("night", mode).apply()
            AppCompatDelegate.setDefaultNightMode(mode)
        }
        val theme = card(title("Appearance"), hint("Theme"), segRow(themeGroup, 8))

        val defAuto = sw("Write each change straight into the original file", autoSwitch.isChecked)
        defAuto.setOnCheckedChangeListener { _, on ->
            autoSwitch.isChecked = on
            prefs.edit().putBoolean("def_auto", on).apply()
        }
        val defRva = sw("Use RVA (Il2CppDumper addresses) by default", rvaSwitch.isChecked)
        defRva.setOnCheckedChangeListener { _, on ->
            rvaSwitch.isChecked = on
            prefs.edit().putBoolean("def_rva", on).apply()
        }
        val defDry = sw("Dry run by default in the patch list", dryCheck.isChecked)
        defDry.setOnCheckedChangeListener { _, on ->
            dryCheck.isChecked = on
            prefs.edit().putBoolean("def_dry", on).apply()
        }
        val defaults = card(
            title("Defaults"),
            hint("Remembered for next time you open the app."),
            defAuto, defRva, defDry
        )

        val storage = card(
            title("Storage"),
            hint("Clears the metadata and APK copies this app keeps on the device. Your save files are not touched."),
            row(btn("Clear cache", 2) { clearCache() }, btn("Clear log", 2) {
                st.log = ""
                if (::logText.isInitialized) logText.text = ""
            })
        )

        val verName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        val about = card(
            title("About"),
            hint(
                "Il2Patch $verName\n" +
                    "For offline, single-player games you own. A repacked APK needs the original app " +
                    "uninstalled first, and in-game saves are usually lost unless you back them up."
            )
        )

        return page(theme, defaults, storage, about)
    }

    private fun clearCache() {
        bg {
            st.dump?.close()
            st.dump = null
            st.suggestions = emptyList()
            st.dumpReport = ""
            st.metaFile?.delete()
            st.metaFile = null
            st.metaName = ""
            filesDir.listFiles { f -> f.name.startsWith("meta_") }?.forEach { it.delete() }
            File(cacheDir, "input.apk").delete()
            File(cacheDir, "signed.apk").delete()
            ui {
                metaText.text = "APK: found automatically. For a plain .so, pick global-metadata.dat."
                sumText.text = summaryLine()
                dumpSummary.text = ""
                renderSuggestions()
            }
            setStatus("Cache cleared.")
            toast("Cache cleared")
        }
    }

    private fun logPage(): ScrollView {
        logText = tv(st.log, MR.style.TextAppearance_Material3_BodySmall).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        return page(
            card(title("Log"), logText),
            btn("Clear log", 2) {
                st.log = ""
                logText.text = ""
            }
        )
    }

    // ------------------------------------------------------------ state save / restore

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", curPage)
        outState.putInt("seg", curSeg)
        outState.putString("off", offsetField.text)
        outState.putString("bytes", bytesField.text)
        outState.putString("len", lenField.text)
        outState.putString("value", valueField.text)
        outState.putString("batch", batchField.text)
        outState.putString("pattern", patternField.text)
        outState.putBoolean("rva", rvaSwitch.isChecked)
        outState.putBoolean("auto", autoSwitch.isChecked)
        outState.putBoolean("dry", dryCheck.isChecked)
        outState.putInt("type", typeIds.indexOf(typeGroup.checkedButtonId))
        outState.putString("r1", patchResult.text.toString())
        outState.putString("r2", returnResult.text.toString())
        outState.putString("r3", batchResult.text.toString())
        outState.putString("r4", searchResult.text.toString())
    }

    private fun restore(b: Bundle?) {
        infoText.text = if (st.info.isEmpty()) "No file open." else st.info
        dumpSummary.text = st.dumpReport
        refreshHint()
        if (b == null) return
        offsetField.text = b.getString("off") ?: ""
        bytesField.text = b.getString("bytes") ?: ""
        lenField.text = b.getString("len") ?: "64"
        valueField.text = b.getString("value") ?: ""
        batchField.text = b.getString("batch") ?: ""
        patternField.text = b.getString("pattern") ?: ""
        rvaSwitch.isChecked = b.getBoolean("rva", true)
        autoSwitch.isChecked = b.getBoolean("auto", true)
        dryCheck.isChecked = b.getBoolean("dry", false)
        val ti = b.getInt("type", 0)
        if (ti in typeIds.indices) typeGroup.check(typeIds[ti])
        patchResult.text = b.getString("r1") ?: "Nothing yet."
        returnResult.text = b.getString("r2") ?: "Nothing yet."
        batchResult.text = b.getString("r3") ?: "Nothing yet."
        searchResult.text = b.getString("r4") ?: "Nothing yet."
        val si = b.getInt("seg", 0)
        if (si in segIds.indices) segGroup.check(segIds[si])
        go(b.getInt("tab", 0).coerceIn(0, 4), false)
    }

    // ------------------------------------------------------------ file picking / IO

    private fun pickFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, REQ_PICK)
    }

    private fun pickText(req: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, req)
    }

    private fun createDoc(req: Int, mime: String, title: String) {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, title)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, req)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_PICK -> {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                loadSource(uri)
            }
            REQ_SAVE_COPY -> bg {
                contentResolver.openOutputStream(uri, "wt")!!.use { o ->
                    FileInputStream(work).use { i -> copyStream(i, o, "Saving") }
                }
                setStatus("Copy saved.")
                toast("Copy saved")
            }
            REQ_BUILD_APK -> {
                val inApk = st.apkFile
                val entry = st.apkEntry
                if (inApk == null || entry == null) return
                bg {
                    val signed = File(cacheDir, "signed.apk")
                    ApkTools.buildSignedApk(this, inApk, entry, work, signed) { setStatus(it) }
                    contentResolver.openOutputStream(uri, "wt")!!.use { o ->
                        FileInputStream(signed).use { i -> copyStream(i, o, "Writing APK") }
                    }
                    signed.delete()
                    setStatus("Patched APK saved.")
                    log("Patched APK written. Uninstall the original app, then install this one.")
                    toast("Patched APK saved. Uninstall the original first.")
                }
            }
            REQ_LOAD_PATCHES -> bg {
                val t = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                ui { batchField.text = t }
                log("Loaded patch list (${t.lines().size} lines)")
            }
            REQ_SAVE_PATCHES -> {
                val t = batchField.text
                bg {
                    contentResolver.openOutputStream(uri, "wt")!!.use { it.write(t.toByteArray()) }
                    toast("Patch list saved")
                }
            }
            REQ_PICK_META -> bg {
                val name = queryName(uri)
                st.dump?.close()
                st.dump = null
                val old = st.metaFile
                val f = File(filesDir, "meta_" + System.currentTimeMillis() + ".dat")
                contentResolver.openInputStream(uri)!!.use { i ->
                    FileOutputStream(f).use { o -> copyStream(i, o, "Loading metadata") }
                }
                if (old != null && old != f) old.delete()
                st.metaFile = f
                st.metaName = name
                ui { metaText.text = "Metadata: $name (${f.length()} bytes)" }
                setStatus("Metadata loaded: $name")
                log("Metadata loaded: $name")
            }
            REQ_SAVE_SUGG -> {
                val an = st.dump ?: return
                val sg = st.suggestions
                bg {
                    setStatus("Writing JSON...")
                    BufferedWriter(OutputStreamWriter(contentResolver.openOutputStream(uri, "wt")!!, Charsets.UTF_8), 1 shl 16)
                        .use { an.writeSuggestionsJson(it, sg) }
                    setStatus("Suggestions saved.")
                    toast("Suggestions JSON saved")
                }
            }
            REQ_SAVE_JSON -> {
                val an = st.dump ?: return
                val sg = st.suggestions
                bg {
                    setStatus("Writing JSON...")
                    BufferedWriter(OutputStreamWriter(contentResolver.openOutputStream(uri, "wt")!!, Charsets.UTF_8), 1 shl 16)
                        .use { an.writeJson(it, sg) }
                    setStatus("JSON saved.")
                    toast("JSON saved")
                }
            }
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream, label: String) {
        val buf = ByteArray(1 shl 16)
        var total = 0L
        var next = 8L shl 20
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            total += n
            if (total >= next) {
                next += 8L shl 20
                setStatus("$label... ${total shr 20} MB")
            }
        }
    }

    private fun queryName(uri: Uri): String {
        var n = "file.bin"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) n = c.getString(i)
            }
        }
        return n
    }

    private fun loadSource(uri: Uri) {
        bg {
            st.dump?.close()
            st.dump = null
            st.suggestions = emptyList()
            st.dumpReport = ""
            ui {
                dumpSummary.text = ""
                sumText.text = summaryLine()
                renderSuggestions()
                refreshHint()
            }
            val name = queryName(uri)
            st.srcUri = uri
            st.srcName = name
            st.patcher = null
            if (name.lowercase().endsWith(".apk")) {
                val f = File(cacheDir, "input.apk")
                contentResolver.openInputStream(uri)!!.use { i ->
                    FileOutputStream(f).use { o -> copyStream(i, o, "Copying APK") }
                }
                st.apkFile = f
                st.apkEntry = null
                val names = ApkTools.listCandidates(f)
                ui { showEntryDialog(names) }
            } else {
                st.apkFile = null
                st.apkEntry = null
                contentResolver.openInputStream(uri)!!.use { i ->
                    FileOutputStream(work).use { o -> copyStream(i, o, "Loading") }
                }
                finishLoad(name)
            }
        }
    }

    private fun showEntryDialog(names: List<String>) {
        if (names.isEmpty()) {
            toast("No .so files found in this APK")
            setStatus("No .so files found.")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Pick the file to patch")
            .setItems(names.toTypedArray()) { _, i -> extractEntry(names[i]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun extractEntry(name: String) {
        val af = st.apkFile ?: return
        bg {
            java.util.zip.ZipFile(af).use { z ->
                val e = z.getEntry(name)
                z.getInputStream(e).use { i ->
                    FileOutputStream(work).use { o -> copyStream(i, o, "Extracting") }
                }
            }
            st.apkEntry = name
            finishLoad("${st.srcName} > $name")
        }
    }

    private fun finishLoad(label: String) {
        val p = Patcher(work)
        st.patcher = p
        val e = ElfInfo.parse(work)
        st.elf = e
        val desc = if (e == null) "Not an ELF file (raw hex editing only)"
        else "ELF ${e.archName()}, ${e.segments.size} loadable segments"
        val mode = if (st.apkFile != null) "APK mode: use 'Build patched APK' to save"
        else "Direct mode: changes go into the original file"
        st.info = "$label\n${p.size()} bytes\n$desc\n$mode"
        ui {
            infoText.text = st.info
            patchResult.text = "Nothing yet."
            refreshHint()
        }
        setStatus("Ready: $label")
        log("Loaded $label")
    }

    // ------------------------------------------------------------ helpers

    private fun is64() = st.elf?.machine != 0x28

    private fun parseHexLong(s: String): Long? =
        s.trim().removePrefix("0x").removePrefix("0X").toLongOrNull(16)

    private fun toFileOffset(v: Long, rva: Boolean): Long? {
        val e = st.elf
        return if (rva && e != null) e.toFile(v) else v
    }

    private fun resolveBytes(text: String): ByteArray = Hex.parse(text) ?: Asm.assemble(text, is64())

    private fun writeThrough(off: Long, data: ByteArray) {
        val uri = st.srcUri ?: return
        if (st.apkFile != null) return
        val pfd = contentResolver.openFileDescriptor(uri, "rw")
            ?: throw IllegalStateException("cannot open original for writing")
        ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { os ->
            val ch = os.channel
            ch.position(off)
            ch.write(ByteBuffer.wrap(data))
            ch.force(true)
        }
    }

    private fun applyOne(p: Patcher, off: Long, data: ByteArray, auto: Boolean): ByteArray {
        val old = p.read(off, data.size)
        p.write(off, data)
        p.journal.add(Patcher.Rec(off, old, data))
        if (auto) writeThrough(off, data)
        return old
    }

    private fun selectedType(): String {
        val i = typeIds.indexOf(typeGroup.checkedButtonId)
        return types[if (i < 0) 0 else i]
    }

    // ------------------------------------------------------------ actions

    private fun showPresets() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Presets")
            .setItems(presets.map { it.first }.toTypedArray()) { _, i -> bytesField.text = presets[i].second }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun doRead() {
        val p = st.patcher ?: return toast("Open a file first")
        val v = parseHexLong(offsetField.text) ?: return toast("Offset must be hex")
        val off = toFileOffset(v, rvaSwitch.isChecked) ?: return toast("Address is not inside the file")
        val len = (lenField.text.toIntOrNull() ?: 64).coerceIn(1, 4096)
        bg {
            val b = p.read(off, len)
            show(patchResult, "file offset 0x%X\n%s".format(off, Hex.dump(b, off)))
        }
    }

    /**
     * Reads the 4 bytes at file offset [off] and, if they're an ARM64 add/sub instruction (and not
     * one touching the stack pointer), flips add<->sub and writes it back. Shared by the manual
     * "Flip + / -" button and by tapping a candidate found by [doScanForSub].
     */
    private fun flipAt(off: Long) {
        val p = st.patcher ?: return toast("Open a file first")
        if (off < 0 || off + 4 > p.size()) return toast("Address is not inside the file")
        bg {
            val old = p.read(off, 4)
            val word = (old[0].toInt() and 0xFF) or ((old[1].toInt() and 0xFF) shl 8) or
                ((old[2].toInt() and 0xFF) shl 16) or ((old[3].toInt() and 0xFF) shl 24)
            val flip = Asm.flipAddSub(word)
            if (flip == null) {
                show(patchResult, "0x%X: %s\nNot a recognised add/sub instruction (or it touches the stack pointer).".format(off, Hex.fmt(old)))
                toast("Not a recognised add/sub instruction")
                return@bg
            }
            val (newWord, desc) = flip
            val data = byteArrayOf(
                (newWord and 0xFF).toByte(), ((newWord shr 8) and 0xFF).toByte(),
                ((newWord shr 16) and 0xFF).toByte(), ((newWord ushr 24) and 0xFF).toByte()
            )
            val auto = autoSwitch.isChecked
            applyOne(p, off, data, auto)
            log("0x%X  %s  (was %s, now %s)".format(off, desc, Hex.fmt(old), Hex.fmt(data)))
            setStatus("${p.journal.size} change(s) this session")
            show(patchResult, "0x%X: %s\n%s -> %s".format(off, desc, Hex.fmt(old), Hex.fmt(data)))
            ui {
                val sb = Snackbar.make(findViewById(android.R.id.content), "Flipped: $desc", Snackbar.LENGTH_LONG)
                sb.setAnchorView(nav)
                sb.setAction("Undo") { undo(false) }
                sb.show()
            }
        }
    }

    /**
     * Reads the 4 bytes at the current address and, if they're an ARM64 add/sub instruction (and
     * not one touching the stack pointer), flips add<->sub and writes it back. Meant for cases like
     * "Spend(int amount)" that do `resource -= amount` in place: there's no return value to patch,
     * but if this is the exact subtract instruction, flipping it makes spending increase the resource
     * instead of decreasing it. Point it at the instruction itself, found via Search, [doScanForSub],
     * or a disassembler (e.g. Ghidra) -- not at the function's entry point.
     */
    private fun doFlipAddSub() {
        if (!is64()) return toast("Flip +/- only recognises ARM64 instructions")
        val v = parseHexLong(offsetField.text) ?: return toast("Offset must be hex")
        val off = toFileOffset(v, rvaSwitch.isChecked) ?: return toast("Address is not inside the file")
        flipAt(off)
    }

    /**
     * Beta: scans from the current address -- meant to be a function's entry point, such as a
     * Resources "Spend" suggestion's address -- for a subtract instruction, entirely inside the
     * app, no external disassembler needed. Unlike a plain straight-line read, this follows
     * conditional branches (both ways), unconditional branches, and `bl` calls (see
     * [Asm.scanDeep]), since real compiled functions are full of guard checks and shared helpers.
     * It still can't tell *which* subtract is the right one if a function has several, or follow
     * an indirect (virtual/interface) call, so check the result -- candidates found only via a
     * call are marked, since they live in a function that may be shared by other code too.
     */
    private fun doScanForSub() {
        val p = st.patcher ?: return toast("Open a file first")
        if (!is64()) return toast("Scan only recognises ARM64 instructions")
        val v = parseHexLong(offsetField.text) ?: return toast("Offset must be hex")
        val off = toFileOffset(v, rvaSwitch.isChecked) ?: return toast("Address is not inside the file")
        if (off < 0 || off >= p.size()) return toast("Address is not inside the file")
        bg {
            val size = p.size()
            val reader: (Long) -> Int? = { o ->
                if (o < 0 || o + 4 > size) null
                else {
                    val b = p.read(o, 4)
                    (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8) or
                        ((b[2].toInt() and 0xFF) shl 16) or ((b[3].toInt() and 0xFF) shl 24)
                }
            }
            val hits = Asm.scanDeep(reader, off)
            if (hits.isEmpty()) {
                val sample = Hex.fmt(p.read(off, minOf(64L, size - off).toInt()))
                show(
                    patchResult,
                    ("No subtract instruction found, even following branches and calls.\n" +
                        "First bytes at 0x%X:\n%s\n\n" +
                        "This can still miss a case where the real work happens through an indirect " +
                        "(virtual/interface) call, which can't be followed automatically -- open the .so in " +
                        "a disassembler (e.g. Ghidra), follow the call chain from here by hand, and point the " +
                        "manual \"Flip + / -\" button at the subtract you find.")
                        .format(off, sample)
                )
                toast("No subtract instruction found nearby")
                return@bg
            }
            val e = st.elf
            val labels = hits.map { (fOff, desc, viaCall) ->
                val vaddr = e?.toVaddr(fOff)
                val addrTxt = if (vaddr != null) "RVA 0x%X".format(vaddr) else "file 0x%X".format(fOff)
                addrTxt + "  " + desc + (if (viaCall) "  (in a called function)" else "")
            }
            ui {
                MaterialAlertDialogBuilder(this)
                    .setTitle("${hits.size} subtract instruction(s) found - tap to flip (increase on spend)")
                    .setItems(labels.toTypedArray()) { _, i -> flipAt(hits[i].first) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun doPatch() {
        val p = st.patcher ?: return toast("Open a file first")
        val v = parseHexLong(offsetField.text) ?: return toast("Offset must be hex")
        val off = toFileOffset(v, rvaSwitch.isChecked) ?: return toast("Address is not inside the file")
        val data = try {
            resolveBytes(bytesField.text)
        } catch (e: Exception) {
            return toast("Bytes: ${e.message}")
        }
        if (off < 0 || off + data.size > p.size()) return toast("Patch runs past the end of the file")
        val auto = autoSwitch.isChecked
        bg {
            val old = applyOne(p, off, data, auto)
            show(patchResult, "Patched file offset 0x%X\nold: %s\nnew: %s".format(off, Hex.fmt(old), Hex.fmt(data)))
            log("0x%X  %s -> %s".format(off, Hex.fmt(old), Hex.fmt(data)))
            setStatus("${p.journal.size} change(s) this session")
        }
    }

    private fun doReturnValue() {
        val p = st.patcher ?: return toast("Open a file first")
        val v = parseHexLong(offsetField.text) ?: return toast("Offset must be hex")
        val off = toFileOffset(v, rvaSwitch.isChecked) ?: return toast("Address is not inside the file")
        val type = selectedType()
        val valueTxt = valueField.text.trim()
        val data = try {
            Asm.returnValue(type, valueTxt, is64())
        } catch (e: Exception) {
            return toast("Value: ${e.message}")
        }
        if (off < 0 || off + data.size > p.size()) return toast("Patch runs past the end of the file")
        val auto = autoSwitch.isChecked
        bg {
            val old = applyOne(p, off, data, auto)
            val msg = "Function now returns " + type + " " + valueTxt + "\n" +
                data.size + " bytes written at file offset 0x%X\nold: %s\nnew: %s"
            show(returnResult, msg.format(off, Hex.fmt(old), Hex.fmt(data)))
            log("0x%X  return %s %s".format(off, type, valueTxt))
            setStatus("${p.journal.size} change(s) this session")
        }
    }

    private fun runBatch() {
        val p = st.patcher ?: return toast("Open a file first")
        val text = batchField.text
        val rva = rvaSwitch.isChecked
        val dry = dryCheck.isChecked
        val auto = autoSwitch.isChecked
        val is64 = is64()
        bg {
            val out = ArrayList<String>()
            var failed = 0
            val (ops, errs) = Batch.parse(text, is64)
            for (e in errs) {
                failed++
                out.add("ERR $e")
            }
            val planned = ArrayList<Pair<Batch.Op, Long>>()
            for (op in ops) {
                try {
                    val base: Long
                    if (op.pattern != null) {
                        val m = p.find(op.pattern, 2)
                        if (m.isEmpty()) throw IllegalStateException("pattern not found")
                        if (m.size > 1) throw IllegalStateException("pattern matches more than once, make it longer")
                        base = m[0]
                    } else {
                        val v = op.offset!!
                        base = toFileOffset(v, rva)
                            ?: throw IllegalStateException("address 0x%X is not in a file-backed segment".format(v))
                    }
                    val off = base + op.delta
                    if (off < 0 || off + op.new.size > p.size()) throw IllegalStateException("outside the file")
                    val cur = p.read(off, op.new.size)
                    if (op.old != null && !cur.contentEquals(op.old)) {
                        throw IllegalStateException("old bytes differ (found ${Hex.fmt(cur)})")
                    }
                    if (cur.contentEquals(op.new)) {
                        out.add("line ${op.line}: already applied at 0x%X".format(off))
                        continue
                    }
                    planned.add(Pair(op, off))
                    out.add(
                        "line ${op.line}: ${if (dry) "would patch" else "patch"} 0x%X  %s -> %s"
                            .format(off, Hex.fmt(cur), Hex.fmt(op.new))
                    )
                } catch (e: Exception) {
                    failed++
                    out.add("ERR line ${op.line}: ${e.message}")
                }
            }
            if (failed > 0) {
                out.add("Nothing applied: $failed error(s).")
            } else if (dry) {
                out.add("Dry run OK: ${planned.size} patch(es) would be applied.")
            } else {
                for ((op, off) in planned) applyOne(p, off, op.new, auto)
                out.add("Applied ${planned.size} patch(es).")
                setStatus("${p.journal.size} change(s) this session")
            }
            show(batchResult, out.joinToString("\n"))
            log("Batch: ${out.last()}")
        }
    }

    private fun doFind() {
        val p = st.patcher ?: return toast("Open a file first")
        val pat = BytePattern.parse(patternField.text) ?: return toast("Bad pattern")
        setStatus("Searching...")
        bg {
            val hits = p.find(pat, 200)
            val e = st.elf
            val labels = hits.map {
                val v = e?.toVaddr(it)
                if (v != null) "file 0x%X   RVA 0x%X".format(it, v) else "file 0x%X".format(it)
            }
            setStatus("${hits.size} match(es)${if (hits.size >= 200) " (first 200)" else ""}")
            ui {
                if (hits.isEmpty()) {
                    searchResult.text = "No matches."
                } else {
                    searchResult.text = labels.joinToString("\n")
                    MaterialAlertDialogBuilder(this)
                        .setTitle("${hits.size} match(es), tap to use")
                        .setItems(labels.toTypedArray()) { _, i ->
                            val f = hits[i]
                            val v = e?.toVaddr(f)
                            val use = if (rvaSwitch.isChecked && v != null) v else f
                            offsetField.text = "%X".format(use)
                            goPatch(0)
                        }
                        .setNegativeButton("Close", null)
                        .show()
                }
            }
        }
    }

    private fun undo(all: Boolean) {
        val p = st.patcher ?: return toast("Open a file first")
        bg {
            var n = if (all) p.journal.size else minOf(1, p.journal.size)
            if (n == 0) {
                toast("Nothing to undo")
                return@bg
            }
            val total = n
            while (n > 0) {
                val r = p.journal.removeAt(p.journal.size - 1)
                p.write(r.off, r.old)
                writeThrough(r.off, r.old)
                n--
            }
            log("Undid $total change(s)")
            setStatus("${p.journal.size} change(s) this session")
        }
    }

    private fun saveOriginal() {
        val uri = st.srcUri
        if (st.patcher == null || uri == null) return toast("Open a file first")
        if (st.apkFile != null) return toast("APK mode: use 'Build patched APK'")
        bg {
            contentResolver.openOutputStream(uri, "wt")!!.use { o ->
                FileInputStream(work).use { i -> copyStream(i, o, "Saving") }
            }
            setStatus("Saved to original.")
            toast("Saved to original file")
        }
    }

    private fun saveCopy() {
        if (st.patcher == null) return toast("Open a file first")
        val entry = st.apkEntry
        val name = if (entry != null) entry.substringAfterLast('/') else st.srcName
        createDoc(REQ_SAVE_COPY, "application/octet-stream", name)
    }

    private fun buildApk() {
        if (st.patcher == null || st.apkFile == null || st.apkEntry == null) {
            return toast("Open an .apk and pick a file inside it first")
        }
        createDoc(
            REQ_BUILD_APK, "application/vnd.android.package-archive",
            st.srcName.removeSuffix(".apk") + "_patched.apk"
        )
    }

    private fun saveText() {
        createDoc(REQ_SAVE_PATCHES, "text/plain", "patches.txt")
    }
}
