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
import com.example.il2patch.ui.theme.Il2PatchTheme

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
    private val navIds = listOf(R.id.nav_start, R.id.nav_find, R.id.nav_patch)
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
        "Resources", "Ads", "Premium", "Unlocks", "Timers", "Tutorial", "Achievements", "God Mode", "Damage",
        "Stats", "Weapons", "Movement", "Enemy", "Map", "Save", "Shop", "Daily", "Settings", "Vehicle", "Bars"
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

    // ---------- Material You helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    // MODERN COLORS — hardcoded, NOT from themes.xml.
    // Why: themes.xml in your repo still has the OLD purple/grey. If we read from
    // MaterialColors.getColor(), the old theme wins and the app still looks old.
    // These match Obtainium (light) / Morphe (dark) from your screenshots.
    private fun cPrimary(): Int = if (isNightNow()) 0xFFB8B2FF.toInt() else 0xFF4A44D6.toInt()
    private fun cOnPrimaryContainer(): Int = if (isNightNow()) 0xFFE8E0FF.toInt() else 0xFF1A1A1A.toInt()
    private fun cPrimaryContainer(): Int = if (isNightNow()) 0xFF3B2FC9.toInt() else 0xFFE8E0FF.toInt()
    private fun cSurfaceVariant(): Int = if (isNightNow()) 0xFF2A2A33.toInt() else 0xFFF2F0FA.toInt()
    private fun cOnSurface(): Int = if (isNightNow()) 0xFFE6E0E9.toInt() else 0xFF121216.toInt()
    private fun cOnVariant(): Int = if (isNightNow()) 0xFFCAC4D0.toInt() else 0xFF5A5A6A.toInt()
    private fun cError(): Int = if (isNightNow()) 0xFFF2B8B5.toInt() else 0xFFB3261E.toInt()
    private fun cSuccess(): Int = 0xFF1E8E5A.toInt()
    private fun cWarning(): Int = 0xFFB26A00.toInt()
    // ---- Modern design system (Obtainium / Morphe style) ----
    // Light: white bg, pastel cards. Dark: near-black bg, gradient cards.
    // All cards 28dp, all buttons pill 28dp, floating pill bottom nav.
    private fun isNight(): Boolean = isNightNow()
    private fun cCardBg(): Int = if (isNight()) 0xFF1E1E24.toInt() else 0xFFF2F0FA.toInt()
    private fun cCardBg2(): Int = if (isNight()) 0xFF252530.toInt() else 0xFFE8E0F5.toInt()
    private fun cNavBg(): Int = if (isNight()) 0xFF2A2A33.toInt() else 0xFFFFFFFF.toInt()
    private fun gradDrawable(start: Int, end: Int, radiusDp: Int = 28): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply { cornerRadius = dp(radiusDp).toFloat() }
    private fun pillDrawable(color: Int, radiusDp: Int = 28): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }
    // Morphe-style gradient pairs for hero / suggestion cards (dark mode pops, light mode stays soft)
    private fun heroGrad(): GradientDrawable = if (isNight())
        gradDrawable(0xFF1E3A8A.toInt(), 0xFF0D9488.toInt(), 28)
    else gradDrawable(0xFF4A44D6.toInt(), 0xFF00C2A8.toInt(), 28)
    private fun gradForCategory(cat: String): GradientDrawable {
        if (!isNight()) return pillDrawable(cCardBg())
        return when (cat) {
            "Currency", "Spend", "Coins" -> gradDrawable(0xFF7C3A00.toInt(), 0xFF0D9488.toInt(), 24)
            "Health", "Lives" -> gradDrawable(0xFF9F1239.toInt(), 0xFF0D9488.toInt(), 24)
            "Damage", "Attack" -> gradDrawable(0xFFB91C1C.toInt(), 0xFF0D9488.toInt(), 24)
            "Speed", "Movement" -> gradDrawable(0xFF1E40AF.toInt(), 0xFF0D9488.toInt(), 24)
            else -> gradDrawable(0xFF312E81.toInt(), 0xFF0D9488.toInt(), 24)
        }
    }

    private fun fontTitle(): Typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    private fun fontBody(): Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private fun fontBodyMedium(): Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private fun fontMono(): Typeface = Typeface.MONOSPACE

    // Clearer section header: emoji + title + "why it matters" line, Material You colors
    private fun sectionHeader(emoji: String, titleText: String, why: String): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(title("$emoji  $titleText"))
        col.addView(hint(why))
        return col
    }
    private fun tv(text: CharSequence, style: Int = MR.style.TextAppearance_Material3_BodyMedium) =
        TextView(this).apply { this.text = text; setTextAppearance(style); typeface = fontBody() }

    // Morphe style: NO emoji in titles. Titles are clean text, 22sp bold.
    // Emoji made every card look like a 2018 tutorial app.
    private fun cleanText(s: String): String {
        // Strip leading emoji / symbols / numbers like "📂  1 — ", "🔍  ", "1️⃣ "
        var r = s.trim()
        // Remove leading non-letter runs (emoji, digits, dashes)
        while (r.isNotEmpty() && !r[0].isLetter()) r = r.drop(1).trimStart()
        // Collapse "1 — Open" -> "Open"
        if (r.length > 2 && r[0].isDigit()) r = r.drop(1).trimStart('—','-',' ').trimStart()
        return r.ifEmpty { s }
    }
    private fun title(t: String) = tv(cleanText(t), MR.style.TextAppearance_Material3_TitleLarge).apply {
        typeface = fontTitle(); setTextColor(cOnSurface().toInt()); setPadding(0, 0, 0, dp(2)); textSize = 22f; letterSpacing = -0.01f
    }
    private fun subtitle(t: String) = tv(t, MR.style.TextAppearance_Material3_BodyMedium).apply {
        typeface = fontBody(); setTextColor(cOnVariant().toInt()); setPadding(0, 0, 0, dp(8)); setLineSpacing(0f, 1.15f); textSize = 14f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
    }
    // Section header: coloured by location, e.g. "💎  1  FILES" in Find, "✏️  VALUE" in Patch
    private fun label(t: String, color: Int = cPrimary()) = tv(cleanText(t).uppercase(), MR.style.TextAppearance_Material3_LabelLarge).apply {
        typeface = fontTitle(); letterSpacing = 0.12f; setTextColor(color.toInt()); setPadding(dp(4), dp(16), 0, dp(6)); textSize = 13f
    }
    private fun hint(t: String, color: Int = cOnVariant()) = tv(t, MR.style.TextAppearance_Material3_BodySmall).apply {
        typeface = fontBody(); setTextColor(color.toInt()); setLineSpacing(0f, 1.2f)
    }
    private fun hintSuccess(t: String) = hint(t, cSuccess())
    private fun hintWarn(t: String) = hint(t, cWarning())
    private fun hintError(t: String) = hint(t, cError())

    // Explainer card — Morphe style: title + ONE line. Full text expands on tap.
    // Long paragraphs on every card are what made it look old.
    private fun explainer(emoji: String, titleText: String, body: String, bg: Int = cPrimaryContainer(), fg: Int = cOnPrimaryContainer()): MaterialCardView {
        val t = tv(cleanText(titleText), MR.style.TextAppearance_Material3_TitleMedium).apply {
            typeface = fontTitle(); setTextColor(fg.toInt()); textSize = 20f
        }
        // Only first sentence shown by default — tap to read more
        val short = body.substringBefore("\n").substringBefore(". ").let { if (body.contains(". ")) it + "." else it }
        val b = tv(short, MR.style.TextAppearance_Material3_BodyMedium).apply {
            typeface = fontBody(); setTextColor(fg.toInt()); setPadding(0, dp(4), 0, 0); setLineSpacing(0f, 1.2f); maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val more = tv("Tap to read more  ▾", MR.style.TextAppearance_Material3_BodySmall).apply {
            setTextColor(0xFFFFFFFF.toInt()); alpha = 0.8f; setPadding(0, dp(6), 0, 0)
        }
        val c = card(t, b, more)
        // Modern hero: gradient in both modes, white text always readable
        c.background = heroGrad()
        c.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
        t.setTextColor(0xFFFFFFFF.toInt()); b.setTextColor(0xFFFFFFFF.toInt())
        c.cardElevation = dp(4).toFloat()
        var expanded = false
        c.setOnClickListener {
            expanded = !expanded
            if (expanded) { b.text = body; b.maxLines = Int.MAX_VALUE; more.text = "Tap to hide  ▴" }
            else { b.text = short; b.maxLines = 2; more.text = "Tap to read more  ▾" }
        }
        return c
    }

    private fun <T : View> T.top(v: Int): T {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(v) }; return this
    }
    private fun View.enter(delay: Long = 0) {
        alpha = 0f; translationY = dp(14).toFloat()
        animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(260)
            .setInterpolator(DecelerateInterpolator()).start()
    }
    private fun toggle(v: View) {
        if (v.visibility == View.VISIBLE) v.visibility = View.GONE
        else { v.visibility = View.VISIBLE; v.alpha = 0f; v.animate().alpha(1f).setDuration(200).start() }
    }
    private fun btn(text: String, kind: Int = 0, f: () -> Unit): MaterialButton {
        val b = when (kind) {
            1 -> MaterialButton(ContextThemeWrapper(this, R.style.ThemeOverlay_Il2Patch_Tonal))
            2 -> MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle)
            3 -> MaterialButton(this).apply { setTextColor(cError().toInt()) } // danger
            else -> MaterialButton(this)
        }
        b.text = text; b.isAllCaps = false
        b.typeface = fontBodyMedium(); b.cornerRadius = dp(28); b.minimumHeight = dp(56)
        b.setOnClickListener { v ->
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start(); f()
        }
        return b
    }
    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0)
        views.forEachIndexed { i, v -> addView(v, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = dp(8) }) }
    }
    private fun rowAsym(main: View, side: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0)
        addView(main, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(side, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(8) })
    }
    private fun uniformSeg(group: MaterialButtonToggleGroup, topMargin: Int = 4): View {
        group.orientation = LinearLayout.HORIZONTAL
        group.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { this.topMargin = dp(topMargin) }
        for (i in 0 until group.childCount) {
            val b = group.getChildAt(i)
            b.layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { if (i > 0) marginStart = dp(4) }
            if (b is MaterialButton) { b.cornerRadius = dp(24); b.textSize = 13f; b.isAllCaps = false }
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = pillDrawable(if (isNightNow()) 0xFF2A2A33.toInt() else 0xFFE8E0FF.toInt(), 28)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(group)
        }
        return wrap
    }
    private fun isUiClass(s: Suggestion): Boolean =
        s.cls.contains("Btn", ignoreCase = true) || s.cls.contains("View", ignoreCase = true)
    private val selectedCats = mutableSetOf<String>()
    private val chipButtons = mutableListOf<MaterialButton>()
    private fun segRow(group: MaterialButtonToggleGroup, topMargin: Int = 4): View =
        HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(group, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { this.topMargin = dp(topMargin) }
        }
    private fun card(vararg children: View): MaterialCardView {
        val c = MaterialCardView(this, null, MR.attr.materialCardViewFilledStyle)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        children.forEach { col.addView(it) }
        c.addView(col); c.radius = dp(28).toFloat()
        c.setCardBackgroundColor(cCardBg())
        c.cardElevation = dp(2).toFloat()
        c.strokeWidth = 0
        c.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) }
        return c
    }
    // ---- Morphe-style row: horizontal, icon circle + title + 1 subtitle + pill action ----
    // This is what makes Morphe look like Morphe: NO big card with a paragraph + button inside.
    // Each row is ONE line of action. Tap row = do it.
    private fun morpheRow(icon: String, titleText: String, subtitleText: String, actionText: String, gradStart: Int, gradEnd: Int, onClick: () -> Unit): MaterialCardView {
        val iconView = TextView(this).apply {
            text = icon; textSize = 24f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor((gradStart and 0x00FFFFFF) or 0xFF000000.toInt()); cornerRadius = dp(16).toFloat() }
            // icon circle: solid tinted bg
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        // Rebuild icon bg as tinted circle using gradStart at low alpha on card bg
        iconView.background = GradientDrawable().apply {
            setColor(if (isNightNow()) (gradStart and 0x00FFFFFF) or 0x33000000 else (gradStart and 0x00FFFFFF) or 0x22000000)
            cornerRadius = dp(16).toFloat()
        }
        val titleV = tv(cleanText(titleText), MR.style.TextAppearance_Material3_TitleMedium).apply {
            typeface = fontTitle(); setTextColor(cOnSurface().toInt()); textSize = 18f
        }
        val subV = tv(subtitleText, MR.style.TextAppearance_Material3_BodySmall).apply {
            setTextColor(cOnVariant().toInt()); textSize = 13f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        val mid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleV); addView(subV)
            setPadding(dp(12), 0, dp(8), 0)
        }
        val action = MaterialButton(this).apply {
            text = actionText; isAllCaps = false; typeface = fontBodyMedium()
            cornerRadius = dp(14); minimumHeight = dp(40)
            backgroundTintList = null
            // Same teal-blue gradient as every other tab button: indigo -> teal
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF5B2EE5.toInt(), 0xFF00C2A8.toInt())).apply { cornerRadius = dp(14).toFloat() }
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener { onClick() }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(iconView, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(mid, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(action, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        }
        val c = MaterialCardView(this, null, MR.attr.materialCardViewFilledStyle)
        c.addView(row.apply { setPadding(dp(16), dp(16), dp(16), dp(16)) })
        c.radius = dp(24).toFloat()
        // All Start rows: light-purple gradient, same in light + dark
        c.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFE9D5FF.toInt(), 0xFFC4B5FD.toInt())).apply { cornerRadius = dp(24).toFloat() }
        c.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
        titleV.setTextColor(0xFF1E1B4B.toInt()); subV.setTextColor(0xFF1E1B4B.toInt()); subV.alpha = 0.85f
        c.cardElevation = dp(2).toFloat(); c.strokeWidth = 0
        c.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
        c.setOnClickListener { onClick() }
        return c
    }

    private fun sw(text: String, checked: Boolean) = MaterialSwitch(this).apply { this.text = text; isChecked = checked; typeface = fontBody() }.top(4)
    private fun resultText() = tv("Nothing yet.", MR.style.TextAppearance_Material3_BodySmall).apply {
        typeface = fontMono(); textSize = 12f; setTextIsSelectable(true); setTextColor(cOnVariant().toInt())
    }
    private fun resultSuccess(t: String) = tv(t, MR.style.TextAppearance_Material3_BodySmall).apply {
        typeface = fontMono(); textSize = 12f; setTextIsSelectable(true); setTextColor(cSuccess().toInt())
    }
    private fun page(vararg views: View) = ScrollView(this).apply {
        setBackgroundColor(if (isNightNow()) 0xFF121216.toInt() else 0xFFFFFFFF.toInt())
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(32))
        }
        views.forEach { col.addView(it) }; addView(col); clipToPadding = false
    }
    private fun section(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; views.forEach { addView(it) }
    }
    // Field with helper text underneath — every field explains itself
    private inner class Field(hintText: String, lines: Int = 1, mono: Boolean = false, helper: String? = null) {
        val layout = TextInputLayout(this@MainActivity)
        val edit = TextInputEditText(layout.context)
        val helperView: TextView? = helper?.let { hint(it) }
        init {
            layout.hint = hintText
            if (mono) edit.typeface = fontMono() else edit.typeface = fontBody()
            if (lines > 1) {
                edit.minLines = lines; edit.gravity = Gravity.TOP
                edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            } else edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            layout.addView(edit)
            layout.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
        }
        var text: String
            get() = edit.text?.toString() ?: ""
            set(v) { edit.setText(v) }
        fun asView(): View {
            if (helperView == null) return layout
            val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            col.addView(layout)
            helperView.setPadding(dp(4), dp(2), 0, dp(4))
            col.addView(helperView)
            return col
        }
    }
    private fun toast(msg: String) {
        runOnUiThread {
            val s = Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG)
            if (::nav.isInitialized) s.setAnchorView(nav); s.show()
        }
    }

    // ---------- MERGED: Material You helpers (added, no old logic changed) ----------
    // Snackbar with Undo - shows after a patch, lets user undo 1 change instantly.
    private fun snackbarWithUndo(msg: String, undoAction: (() -> Unit)? = null) {
        runOnUiThread {
            val s = Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG)
            if (undoAction != null) {
                s.setAction("Undo") { undoAction() }
            }
            // Material You: use theme colors for snackbar
            s.setBackgroundTint(Il2PatchTheme.snackbarBackground(this))
            s.setTextColor(Il2PatchTheme.snackbarText(this))
            s.setActionTextColor(Il2PatchTheme.snackbarAction(this))
            s.show()
        }
    }
    // Show a patch result + offer Undo in one place. Call this after applyOne / doPatch / doReturnValue / flip.
    private fun showPatchedWithUndo(count: Int = 1) {
        snackbarWithUndo("Applied $count patch(es) - tap Undo to revert") { undo(false) }
    }
    private fun ui(block: () -> Unit) { runOnUiThread { block() } }
    private fun show(v: TextView, s: String) { runOnUiThread { v.text = s } }
    private fun setStatus(s: String) { st.status = s; runOnUiThread { statusText.text = s } }
    private fun log(msg: String) {
        st.log = (msg + "\n" + st.log).take(20000)
        runOnUiThread { if (::logText.isInitialized) logText.text = st.log }
    }
    private fun bg(block: () -> Unit) {
        runOnUiThread { busy++; progress.visibility = View.VISIBLE }
        Thread {
            try { block() } catch (e: Throwable) { toast("Error: ${e.message ?: e.javaClass.simpleName}"); log("Error: $e") }
            finally { runOnUiThread { busy--; if (busy <= 0) progress.visibility = View.INVISIBLE } }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("ui", MODE_PRIVATE)
        // THEME FIX: ONE source only — prefs "ui" key "night" (int). Do NOT call
        // Il2PatchTheme.applySavedTheme() here: it reads a different prefs file
        // (il2patch_settings / "theme") and overwrites this mode back to system.
        AppCompatDelegate.setDefaultNightMode(prefs.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        super.onCreate(savedInstanceState)
        work = File(filesDir, "work.bin")
        setContentView(buildUi())
        restore(savedInstanceState)
    }
    private fun isNightNow(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private fun header(): View {
        // MORPHE style: big plain title at top-left, NO logo+title row.
        // "Il" in blue-teal like Morphe's "M", rest white/dark. Subtitle small grey below.
        // Top-right: circular Search button -> Find (Settings tab removed).
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
            setBackgroundColor(if (isNightNow()) 0xFF121216.toInt() else 0xFFFFFFFF.toInt())
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        // Title: "Il" coloured + "2Patch" plain — built from 2 TextViews so the gradient colour is exact
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        // Il2 = light-purple gradient (Morphe font: same sans-serif-medium bold for both parts)
        val il2Part = tv("Il2", MR.style.TextAppearance_Material3_TitleLarge).apply {
            typeface = fontTitle(); textSize = 34f; letterSpacing = -0.02f
            // True gradient: light purple #E9D5FF -> #A78BFA, applied once width is known
            post {
                paint.shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), 0f,
                    intArrayOf(0xFFE9D5FF.toInt(), 0xFFC4B5FD.toInt(), 0xFFA78BFA.toInt()),
                    null, android.graphics.Shader.TileMode.CLAMP)
                invalidate()
            }
        }
        val patchPart = tv("Patch", MR.style.TextAppearance_Material3_TitleLarge).apply {
            typeface = fontTitle(); textSize = 34f; letterSpacing = -0.02f
            setTextColor(0xFFFFFFFF.toInt()) // Patch always white
        }
        titleRow.addView(il2Part); titleRow.addView(patchPart)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(titleRow)
        col.addView(hint("Patch your own Unity games", cOnVariant()).apply { textSize = 13f; setPadding(0, dp(2), 0, 0) })
        row.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        // Circular top-right buttons — Morphe: dark grey circle, white icon
        fun circleBtn(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
            this.text = text; textSize = 18f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(if (isNightNow()) 0xFF2A2A33.toInt() else 0xFFE8E0FF.toInt())
                cornerRadius = dp(24).toFloat()
            }
            setTextColor(if (isNightNow()) 0xFFFFFFFF.toInt() else 0xFF1E1B4B.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { onClick() }
        }
        row.addView(circleBtn("🔍") { go(1) }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) })
        outer.addView(row)
        statusText = hint(st.status, cOnSurface()).apply { visibility = View.GONE }
        outer.addView(statusText)
        return outer
    }


    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Force modern bg — do NOT rely on themes.xml windowBackground
            setBackgroundColor(if (isNightNow()) 0xFF121216.toInt() else 0xFFFFFFFF.toInt())
        }
        root.addView(header())
        progress = LinearProgressIndicator(this).apply { isIndeterminate = true; visibility = View.INVISIBLE }
        root.addView(progress, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        offsetField = Field("Function address (hex)", 1, true,
            "📌 Example: 1A2B3C — copy it from Find (RVA) or from Ghidra. Do NOT type a file offset here when RVA is ON.")
        rvaSwitch = sw("RVA from Il2CppDumper / Find  — ON = address from the game, OFF = raw file offset", prefs.getBoolean("def_rva", true))
        targetCard = card(
            title("🎯  Target address"),
            subtitle("This is the function every Patch button below will edit. Fill it from Find, or type it by hand."),
            offsetField.asView(), rvaSwitch
        )
        autoSwitch = sw("Write each change straight into the original file", prefs.getBoolean("def_auto", true))
        pages = listOf(startPage(), findPage(), patchPage())
        val frame = FrameLayout(this)
        pages.forEach { frame.addView(it, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)) }
        root.addView(frame, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        // Floating pill bottom nav (Obtainium / Morphe style): floats above content,
        // rounded 32dp, soft shadow. Selected tab gets indigo pill highlight via theme.
        nav = BottomNavigationView(this).apply {
            inflateMenu(R.menu.nav)
            menu.removeItem(R.id.nav_settings); menu.removeItem(R.id.nav_log)
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
            // MORPHE bottom bar: floating dark translucent pill, selected tab gets
            // a light-lavender pill behind its icon+label, unselected = grey.
            background = pillDrawable(if (isNightNow()) 0xE62A2A33.toInt() else 0xF2FFFFFF.toInt(), 32)
            // Selected pill (Material3 active indicator) — exactly Morphe's light lavender
            itemActiveIndicatorColor = android.content.res.ColorStateList.valueOf(0xFFB8B5D6.toInt())
            val sel = if (isNightNow()) 0xFF121216.toInt() else 0xFF121216.toInt()
            val unsel = if (isNightNow()) 0xFF9A93A8.toInt() else 0xFF6B6B7A.toInt()
            itemIconTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(sel, unsel)
            )
            itemTextColor = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(sel, unsel)
            )
            itemTextAppearanceActive = MR.style.TextAppearance_Material3_LabelMedium
            itemTextAppearanceInactive = MR.style.TextAppearance_Material3_LabelMedium
            elevation = dp(8).toFloat()
            setOnItemSelectedListener { item ->
                val i = navIds.indexOf(item.itemId)
                if (i >= 0 && i != curPage) go(i); true
            }
        }
        val navWrap = FrameLayout(this).apply {
            setPadding(dp(16), dp(4), dp(16), dp(12))
            addView(nav, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        root.addView(navWrap, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        go(0, false); refreshHint(); return root
    }
    private fun go(i: Int, animate: Boolean = true) {
        val old = curPage; curPage = i
        pages.forEachIndexed { j, p ->
            if (j == i) { p.visibility = View.VISIBLE; if (animate && j != old) p.enter() }
            else { p.animate().cancel(); p.visibility = View.GONE }
        }
        if (nav.selectedItemId != navIds[i]) nav.selectedItemId = navIds[i]
    }
    private fun goPatch(seg: Int) { go(2); segGroup.check(segIds[seg]) }
    private fun showSeg(i: Int) {
        curSeg = i
        sections.forEachIndexed { j, s ->
            if (j == i) { if (s.visibility != View.VISIBLE) { s.visibility = View.VISIBLE; s.enter() } }
            else s.visibility = View.GONE
        }
        targetCard.visibility = if (i == 0 || i == 1) View.VISIBLE else View.GONE
    }
    private fun refreshHint() {
        if (!::hintText.isInitialized) return
        hintText.text = when {
            st.patcher == null -> "Step 1: open libil2cpp.so or the game's APK."
            st.dump == null -> "Step 2: go to Find and tap Analyze. The app lists functions worth patching."
            else -> "Step 3: in Find, tap Apply fix on a function, then save from this page."
        }
    }

    // ---------- START TAB ----------
    private fun startPage(): ScrollView {
        hintText = tv("", MR.style.TextAppearance_Material3_BodyLarge).apply {
            typeface = fontBodyMedium(); setTextColor(cOnPrimaryContainer().toInt())
        }
        infoText = hint("No file open.", cOnVariant()).apply { setPadding(0, dp(4), 0, 0) }
        // ---- METADATA ROW — exactly mockup: icon square + title + subtitle + gradient Edit pill ----
        fun mockupRow(icon: String, titleText: String, subtitleText: String, actionText: String, onClick: () -> Unit): MaterialCardView {
            val iconV = TextView(this).apply {
                text = icon; textSize = 22f; gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(0xB3FFFFFF.toInt())
                    cornerRadius = dp(12).toFloat()
                }
                setPadding(dp(10), dp(10), dp(10), dp(10))
            }
            val tV = tv(titleText, MR.style.TextAppearance_Material3_TitleMedium).apply {
                typeface = fontTitle(); textSize = 18f
                setTextColor(0xFF1E1B4B.toInt())
            }
            val sV = tv(subtitleText, MR.style.TextAppearance_Material3_BodySmall).apply {
                textSize = 13f; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setTextColor(0xFF1E1B4B.toInt())
                alpha = 0.85f
            }
            val mid = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0)
                addView(tV); addView(sV)
            }
            // Gradient pill button: indigo -> teal, white text — exactly mockup
            val btn = MaterialButton(this).apply {
                text = actionText; isAllCaps = false; typeface = fontBodyMedium(); textSize = 14f
                cornerRadius = dp(14); minimumHeight = dp(40)
                backgroundTintList = null
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF5B2EE5.toInt(), 0xFF00C2A8.toInt())).apply { cornerRadius = dp(14).toFloat() }
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(dp(16), 0, dp(16), 0)
                setOnClickListener { onClick() }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                addView(iconV, LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(mid, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(btn, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            }
            return MaterialCardView(this, null, MR.attr.materialCardViewFilledStyle).apply {
                addView(row); radius = dp(20).toFloat()
                // All Start rows: light-purple gradient (same in light + dark)
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFE9D5FF.toInt(), 0xFFC4B5FD.toInt())).apply { cornerRadius = dp(20).toFloat() }
                setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                tV.setTextColor(0xFF1E1B4B.toInt()); sV.setTextColor(0xFF1E1B4B.toInt())
                cardElevation = dp(2).toFloat(); strokeWidth = 0
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) }
                setOnClickListener { onClick() }
            }
        }
        val metaSub = if (st.metaName.isEmpty()) "App name, version, package ID" else st.metaName
        // Metadata icon: old 🗎 glyph did not render on Samsung — 🗂️ always shows
        val metaRow = mockupRow("🗂️", "Metadata", metaSub, "Edit") { pickMeta() }
        // Import row — same style as Metadata (replaces the deleted hero)
        val importRow = mockupRow("⬇️", "Import libil2cpp.so", "Pick libil2cpp.so or the game APK", "Import") { pickFile() }
        val targetSub = if (st.srcName.isEmpty()) "output/patched_apk" else st.srcName
        val targetRow = mockupRow("📍", "Target address", targetSub, "Browse") { pickFile() }
        // Save row removed — patches auto-save via the Direct-mode switch below.
        val undoRow = morpheRow("↩️", "Undo last change", "Removes the last patch", "Undo", 0xFFB3261E.toInt(), 0xFF0D9488.toInt()) { undo(false) }
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), 0)
            addView(infoText)
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(16), 0, 0)
                addView(autoSwitch, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(16) })
                addView(btn("Undo all", 3) { undo(true) }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(16) })
            })
        }
        return page(importRow, metaRow, targetRow, undoRow, statusRow)
    }

    private fun findPage(): ScrollView {
        metaText = hint(if (st.metaName.isEmpty()) "APK: found automatically. For a plain .so, pick global-metadata.dat." else "Metadata: ${st.metaName}")
        includeAllSwitch = sw("Include 【entity-Unity¦canonical_name=Unity】 / System assemblies (bigger, slower)", false)
        val adv = section(
            hint("Normally we skip UnityEngine / System so the list stays clean. Turn this ON only if you are hunting a 【entity-Unity¦canonical_name=Unity】 or System function on purpose.").top(4),
            includeAllSwitch).apply { visibility = View.GONE }
        val files = card(
            title("🗂️  Metadata"),
            subtitle("global-metadata.dat = real names of every class & method. Without it, Find can't suggest."),
            metaText, rowAsym(btn("Pick metadata file", 1) { pickMeta() }, btn("Advanced", 2) { toggle(adv) }), adv)
        sumText = tv(summaryLine(), MR.style.TextAppearance_Material3_BodyLarge).apply { typeface = fontBodyMedium(); setTextColor(cOnSurface().toInt()) }
        dumpSummary = hint(if (st.dumpReport.isEmpty()) "" else st.dumpReport).apply {
            typeface = fontMono(); textSize = 11f; setTextIsSelectable(true); visibility = View.GONE
        }
        val run = card(
            title("🔍  Analyze"),
            subtitle("Reads every class and method and picks the ones that likely control gems / coins, ads, premium, stats, weapons, movement, enemies, map, saves, shop, daily rewards, settings, vehicles or bars. Names are hints — always test in-game."),
            btn("Analyze") { doAnalyze() }.top(12), sumText.top(12),
            btn("Show technical details", 2) { toggle(dumpSummary) }.top(8), dumpSummary.top(8))
        // Wrapping category chips: 3 per row, NO horizontal scroll — all categories visible
        selectedCats.clear(); chipButtons.clear()
        val chipFlow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var curRow: LinearLayout? = null
        cats.forEachIndexed { idx, c ->
            if (idx % 3 == 0) {
                curRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                chipFlow.addView(curRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { if (idx > 0) topMargin = dp(8) })
            }
            val b = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle)
            b.id = View.generateViewId(); b.text = c; b.isAllCaps = false; b.isCheckable = true; b.isChecked = true
            b.typeface = fontBodyMedium(); b.cornerRadius = dp(20); b.textSize = 12f
            b.setOnClickListener {
                // BUG FIX: MaterialButton with isCheckable already flipped isChecked
                // before this listener runs. Toggling again cancelled it out,
                // so nothing ever changed. Read the new value as-is.
                if (b.isChecked) selectedCats.add(c) else selectedCats.remove(c)
                // Same colour/style for all chips: indigo when on, outline when off
                b.backgroundTintList = android.content.res.ColorStateList.valueOf(if (b.isChecked) 0xFF4A44D6.toInt() else 0x00000000.toInt())
                b.setTextColor(if (b.isChecked) 0xFFFFFFFF.toInt() else if (isNightNow()) 0xFFE6E0E9.toInt() else 0xFF121216.toInt())
                sugLimit = 30; renderSuggestions()
            }
            selectedCats.add(c)
            b.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4A44D6.toInt())
            b.setTextColor(0xFFFFFFFF.toInt())
            chipButtons.add(b); catIds.add(b.id)
            curRow?.addView(b, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (idx % 3 > 0) marginStart = dp(8) })
        }
        filterField = Field("Search by name (optional)", 1, false,
            "🔎 Type part of a name, e.g. gem, coins, ad, health. Leave empty to see everything.")
        sugList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        emptyText = hint("Run Analyze to see suggestions.").apply { setPadding(dp(4), dp(8), 0, dp(8)) }
        moreBtn = btn("Show more", 2) { sugLimit += 30; renderSuggestions() }
        moreBtn.visibility = View.GONE
        val exp = card(
            title("📤  Export"),
            subtitle("Save results as JSON so you can share them, compare two versions of a game, or keep a backup of what you found."),
            row(btn("Suggestions only", 1) { exportSuggestions() }, btn("Everything", 1) { exportJson() }))
        val pg = page(
            explainer("🔎", "Find — what to patch",
                "Find functions by NAME. Green = what the patch does. Apply fix patches at once, Edit checks it in Patch first."),
            label("🗂️  1  FILES", cPrimary()), files,
            label("🔍  2  SCAN", cSuccess()), run,
            label("✅  3  FIX", cSuccess()),
            subtitle("Tap a category chip to filter. Un-tap it to hide that whole category."),
            chipFlow, filterField.asView(),
            emptyText, sugList.top(0), moreBtn.top(4),
            btn("Add shown to Batch list", 2) { sendToBatch() }.top(8),
            label("📤  EXPORT", cPrimary()), exp
        )
        filterField.edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { sugLimit = 30; renderSuggestions() }
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
        return "Found " + cats.joinToString(", ") { c -> "${sg.count { it.category == c }} ${c.lowercase()}" } + " functions."
    }
    private fun catColor(c: String): Int = Hints.colorFor(c)
    private fun catEmoji(c: String): String = Hints.emojiFor(c)
    private fun effectText(s: Suggestion): String = when (s.kind) {
        "noop" -> "Disabled: does nothing"
        "bool" -> "Always returns " + s.value
        else -> "Always returns " + s.value + " (" + s.kind + ")"
    }
    private fun suggestionCard(s: Suggestion): View {
        val col = catColor(s.category)
        val pill = tv("${catEmoji(s.category)}  ${s.category.uppercase()}" + if (isUiClass(s)) " • UI" else "", MR.style.TextAppearance_Material3_LabelSmall).apply {
            typeface = fontTitle(); letterSpacing = 0.08f; setTextColor(col.toInt())
            background = GradientDrawable().apply { setColor((col and 0x00FFFFFF) or 0x26000000); cornerRadius = dp(8).toFloat() }
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(pill); addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(hint("RVA 0x%X".format(s.rva)).apply { typeface = fontMono() })
        }
        val name = tv(s.cls + "." + s.method, MR.style.TextAppearance_Material3_TitleSmall).apply {
            typeface = fontBodyMedium(); setTextColor(cOnSurface().toInt()); setPadding(0, dp(8), 0, 0)
        }
        // Effect line is coloured: green = safe return-value patch, orange = check what it does first
        val effect = tv(effectText(s), MR.style.TextAppearance_Material3_BodyMedium).apply {
            typeface = fontBodyMedium(); setTextColor((if (s.kind == "noop") cWarning() else cSuccess()).toInt())
        }
        lateinit var ap: MaterialButton
        ap = btn("Apply fix") { quickApply(s) { ap.text = "Applied ✓" } }
        val edit = btn("Edit", 2) { applySuggestion(s) }
        return card(top, name, effect, hint("returns " + s.ret + "   " + s.note).top(2), row(ap, edit))
    }
    private fun renderSuggestions() {
        if (!::sugList.isInitialized) return
        sugList.removeAllViews()
        val list = filteredSuggestions()
        if (list.isEmpty()) {
            emptyText.text = when {
                st.dump == null -> "Run Analyze to see suggestions."
                st.suggestions.isEmpty() -> "No non-void functions with matching names were found."
                else -> "No matches. Change the filters."
            }
            emptyText.visibility = View.VISIBLE; moreBtn.visibility = View.GONE
            return
        }
        emptyText.visibility = View.GONE
        list.take(sugLimit).forEachIndexed { i, s ->
            val c = suggestionCard(s); sugList.addView(c); if (i < 10) c.enter(i * 30L)
        }
        if (list.size > sugLimit) { moreBtn.text = "Show more (" + (list.size - sugLimit) + " left)"; moreBtn.visibility = View.VISIBLE }
        else moreBtn.visibility = View.GONE
    }
    private fun pickMeta() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
        @Suppress("DEPRECATION") startActivityForResult(i, REQ_PICK_META)
    }
    private fun extractMetaFromApk(apk: File): File? {
        val out = File(filesDir, "meta_apk.dat")
        java.util.zip.ZipFile(apk).use { z ->
            val e = z.entries().toList().firstOrNull { it.name.endsWith("global-metadata.dat") } ?: return null
            z.getInputStream(e).use { i -> FileOutputStream(out).use { o -> copyStream(i, o, "Extracting metadata") } }
        }
        return out
    }
    private fun doAnalyze() {
        if (st.patcher == null) { toast("Open libil2cpp.so or the APK first"); go(0); return }
        val includeAll = includeAllSwitch.isChecked
        bg {
            st.dump?.close(); st.dump = null; st.suggestions = emptyList(); ui { renderSuggestions() }
            var mf = st.metaFile; val apk = st.apkFile
            if (mf == null && apk != null) {
                mf = extractMetaFromApk(apk)
                if (mf != null) { st.metaFile = mf; st.metaName = "global-metadata.dat (from the APK)"; ui { metaText.text = "Metadata: ${st.metaName}" } }
            }
            if (mf == null) { toast("Pick global-metadata.dat first"); return@bg }
            var an: Il2CppAnalyzer? = null
            try {
                an = Il2CppAnalyzer(mf, work)
                an.analyze(includeAll) { setStatus(it) }
                val sg = an.suggestions()
                st.dump = an; st.suggestions = sg
                st.dumpReport = "Done.\n" + an.diag.toString()
                setStatus("Analysis done: ${an.resolvedMethods} methods")
                log("Analysis done: ${an.resolvedMethods} methods, ${sg.size} suggestions")
                ui { sumText.text = summaryLine(); dumpSummary.text = st.dumpReport; sugLimit = 30; renderSuggestions(); refreshHint() }
            } catch (e: Exception) {
                val rep = "Analysis failed:\n" + (e.message ?: e.toString()) + "\n\n" + (an?.diag ?: "")
                st.dumpReport = rep; setStatus("Analysis failed"); log(rep); an?.close()
                ui { sumText.text = "Analysis failed. Details below."; dumpSummary.text = rep; dumpSummary.visibility = View.VISIBLE }
            }
        }
    }
    private fun filteredSuggestions(cat: String? = null): List<Suggestion> {
        val sel = HashSet<String>()
        if (chipButtons.isEmpty()) {
            for (id in catGroup.checkedButtonIds) { val i = catIds.indexOf(id); if (i >= 0) sel.add(cats[i]) }
        } else sel.addAll(selectedCats)
        val q = filterField.text.trim().lowercase()
        val sel2 = if (chipButtons.isNotEmpty()) selectedCats else sel
        return st.suggestions.filter {
            it.kind != "noop" &&
                (if (cat == null) it.category in sel2 else it.category == cat) &&
                (q.isEmpty() || (it.cls + "." + it.method).lowercase().contains(q))
        }.sortedWith(compareBy({ isUiClass(it) }, { it.category }, { it.cls + "." + it.method }))
    }
    private fun quickApply(s: Suggestion, done: () -> Unit) {
        val p = st.patcher ?: return toast("Open a file first")
        val off = toFileOffset(s.rva, true) ?: return toast("Address is not inside the file")
        val data = try { Asm.assemble(s.patch(), is64()) } catch (e: Exception) { return toast("Value: ${e.message}") }
        if (off < 0 || off + data.size > p.size()) return toast("Patch runs past the end of the file")
        val auto = autoSwitch.isChecked
        bg {
            val old = applyOne(p, off, data, auto)
            log("0x%X  %s.%s -> %s  (was %s)".format(off, s.cls, s.method, s.patch(), Hex.fmt(old)))
            setStatus("${p.journal.size} change(s) this session")
            ui {
                done()
                val sb = Snackbar.make(findViewById(android.R.id.content), "Patched " + s.method, Snackbar.LENGTH_LONG)
                sb.setAnchorView(nav); sb.setAction("Undo") { undo(false) }; sb.show()
            }
        }
    }
    private fun applySuggestion(s: Suggestion) {
        offsetField.text = "%X".format(s.rva); rvaSwitch.isChecked = true
        if (s.kind == "noop") { bytesField.text = "ret"; goPatch(1) }
        else {
            val i = types.indexOf(s.kind); if (i >= 0) typeGroup.check(typeIds[i])
            valueField.text = s.value; goPatch(0)
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
                sb.append("# ").append(s.cls).append('.').append(s.method).append(s.params).append("  returns ").append(s.ret).append('\n')
                sb.append(s.batchLine()).append('\n')
            }
        }
        if (sb.isEmpty()) return toast("No matches. Change the filters.")
        val old = batchField.text
        batchField.text = old + (if (old.isEmpty()) "" else "\n") + sb
        goPatch(2); toast("Added to the list. Delete lines you do not want and use Dry run first.")
    }
    private fun exportJson() {
        if (st.dump == null) return toast("Run Analyze first")
        createDoc(REQ_SAVE_JSON, "application/json", st.srcName.removeSuffix(".so").removeSuffix(".apk") + "_dump.json")
    }
    private fun exportSuggestions() {
        if (st.dump == null || st.suggestions.isEmpty()) return toast("No suggestions to export yet")
        createDoc(REQ_SAVE_SUGG, "application/json", st.srcName.removeSuffix(".so").removeSuffix(".apk") + "_suggestions.json")
    }

    // ---------- PATCH TAB ----------
    private fun patchPage(): ScrollView {
        typeGroup = MaterialButtonToggleGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        types.forEach { t ->
            val b = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle)
            b.id = View.generateViewId(); b.text = t; b.isAllCaps = false; b.typeface = fontBodyMedium()
            typeGroup.addView(b, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
            typeIds.add(b.id)
        }
        typeGroup.check(typeIds[0])
        valueField = Field("Value, e.g. 10000", 1, true,
            "💡 What should the function return? For gems = 999999, for bool = true / false, for price = 0.")
        returnResult = resultText()
        val value = section(
            card(
                title("🟢  Make the function return a value"),
                subtitle("Easiest patch. The function stops running its old code and just returns your value. Best for getters: GetGems, IsPremium, GetHealth."),
                hint("① Pick the type the function returns (check Find — it says “returns int / bool / float”).  ② Type the value.  ③ Tap Patch function."),
                uniformSeg(typeGroup, 12), valueField.asView(), btn("Patch function") { doReturnValue() }.top(12)),
            card(title("📋  Result"), subtitle("Green = patched. If it says “runs past end”, your address is wrong."), returnResult)
        )
        bytesField = Field("New bytes: hex or assembly", 3, true,
            "✍️ Two ways: HEX like 1F 20 03 D5  OR  assembly like mov w0, #1; ret. Tap Presets if you're not sure.")
        lenField = Field("Bytes to read", 1, true, "👀 How many bytes to show when you tap Read. 64 is enough to see the start of a function.")
        patchResult = resultText()
        val bytes = section(
            card(
                title("🟠  Write bytes"),
                subtitle("For when you know exactly what bytes / assembly you want. Advanced — use Value tab if you're unsure."),
                bytesField.asView(),
                row(btn("Presets", 2) { showPresets() }, btn("Apply") { doPatch() }),
                hint("Bytes are written exactly as typed. Use Value tab if you are unsure.").top(6)),
            card(title("👀  Read bytes"), subtitle("See what's really at the address before you change it."), lenField.asView(), btn("Read at address", 1) { doRead() }.top(8)),
            card(title("📋  Result"), patchResult)
        )
        batchField = Field("Patch list", 9, true,
            "📝 One patch per line. # = comment (ignored). See the hint above the box for the exact format.")
        dryCheck = sw("Dry run (check only, change nothing)", prefs.getBoolean("def_dry", false))
        batchResult = resultText()
        val list = section(
            card(
                title("🔵  Patch list"),
                subtitle("Patch many functions at once. Perfect for applying everything you picked in Find."),
                hint("Format — one per line, numbers in HEX:\n1A2B3C: mov w0, #1; ret\n1A2B50: 1F 20 03 D5\n1A2B60: 00 00 80 52 => 20 00 80 52\n1A2B70: return long 10000\nfind FD 7B ?? A9 +4: nop\n# comment").apply { typeface = fontMono() },
                batchField.asView(), dryCheck,
                hintWarn("⚠️ ALWAYS run with Dry run ON first. If it says 0 errors, turn Dry run OFF and run again to really patch.").top(4),
                btn("Run list") { runBatch() }.top(8),
                row(btn("Load", 2) { pickText(REQ_LOAD_PATCHES) }, btn("Save", 2) { saveText() })),
            card(title("📋  Result"), batchResult)
        )
        patternField = Field("Bytes with ?? wildcards", 1, true,
            "🔍 Example: FD 7B ?? A9 ?? ?? 00 91  —  ?? means “any byte”. Patterns survive game updates better than addresses.")
        searchResult = resultText()
        val search = section(
            card(
                title("🟣  Find a byte pattern"),
                subtitle("Find code by its bytes, not its address. Useful when Il2CppDumper addresses are old / the game updated."),
                patternField.asView(), btn("Search") { doFind() }.top(8),
                hint("Tap a match to use it as your target address.").top(6)),
            card(title("🎯  Matches"), searchResult)
        )
        sections = listOf(value, bytes, list, search)
        segGroup = MaterialButtonToggleGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        listOf("Value", "Bytes", "List", "Search").forEach { t ->
            val b = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle)
            b.id = View.generateViewId(); b.text = t; b.isAllCaps = false; b.typeface = fontBodyMedium()
            segGroup.addView(b, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) })
            segIds.add(b.id)
        }
        targetCard.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12); bottomMargin = dp(12) }
        val pg = page(
            explainer("✏️", "Patch — change one function",
                "Everything here edits the function at the 🎯 Target address at the top. Value = easiest, Bytes = full control, List = many at once, Search = find by bytes."),
            uniformSeg(segGroup, 4), targetCard, *sections.toTypedArray())
        segGroup.addOnButtonCheckedListener { _, id, checked -> if (checked) showSeg(segIds.indexOf(id)) }
        segGroup.check(segIds[0]); showSeg(0); return pg
    }

    // ---------- LOG (tab removed — log still kept in memory) ----------
    private fun clearCache() {
        bg {
            st.dump?.close(); st.dump = null; st.suggestions = emptyList(); st.dumpReport = ""; st.metaFile?.delete(); st.metaFile = null; st.metaName = ""
            filesDir.listFiles { f -> f.name.startsWith("meta_") }?.forEach { it.delete() }
            File(cacheDir, "input.apk").delete(); File(cacheDir, "signed.apk").delete()
            ui { metaText.text = "APK: found automatically. For a plain .so, pick global-metadata.dat."; sumText.text = summaryLine(); dumpSummary.text = ""; renderSuggestions() }
            setStatus("Cache cleared."); toast("Cache cleared")
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", curPage); outState.putInt("seg", curSeg)
        outState.putString("off", offsetField.text); outState.putString("bytes", bytesField.text)
        outState.putString("len", lenField.text); outState.putString("value", valueField.text)
        outState.putString("batch", batchField.text); outState.putString("pattern", patternField.text)
        outState.putBoolean("rva", rvaSwitch.isChecked); outState.putBoolean("auto", autoSwitch.isChecked)
        outState.putBoolean("dry", dryCheck.isChecked)
        outState.putInt("type", typeIds.indexOf(typeGroup.checkedButtonId))
        outState.putString("r1", patchResult.text.toString()); outState.putString("r2", returnResult.text.toString())
        outState.putString("r3", batchResult.text.toString()); outState.putString("r4", searchResult.text.toString())
    }
    private fun restore(b: Bundle?) {
        infoText.text = if (st.info.isEmpty()) "No file open." else st.info
        dumpSummary.text = st.dumpReport; refreshHint(); if (b == null) return
        offsetField.text = b.getString("off") ?: ""; bytesField.text = b.getString("bytes") ?: ""
        lenField.text = b.getString("len") ?: "64"; valueField.text = b.getString("value") ?: ""
        batchField.text = b.getString("batch") ?: ""; patternField.text = b.getString("pattern") ?: ""
        rvaSwitch.isChecked = b.getBoolean("rva", true); autoSwitch.isChecked = b.getBoolean("auto", true)
        dryCheck.isChecked = b.getBoolean("dry", false)
        val ti = b.getInt("type", 0); if (ti in typeIds.indices) typeGroup.check(typeIds[ti])
        patchResult.text = b.getString("r1") ?: "Nothing yet."; returnResult.text = b.getString("r2") ?: "Nothing yet."
        batchResult.text = b.getString("r3") ?: "Nothing yet."; searchResult.text = b.getString("r4") ?: "Nothing yet."
        val si = b.getInt("seg", 0); if (si in segIds.indices) segGroup.check(segIds[si])
        go(b.getInt("tab", 0).coerceIn(0, 2), false)
    }

    private fun pickFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        @Suppress("DEPRECATION") startActivityForResult(i, REQ_PICK)
    }
    private fun pickText(req: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
        @Suppress("DEPRECATION") startActivityForResult(i, req)
    }
    private fun createDoc(req: Int, mime: String, title: String) {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = mime; putExtra(Intent.EXTRA_TITLE, title)
        }
        @Suppress("DEPRECATION") startActivityForResult(i, req)
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_PICK -> {
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
                loadSource(uri)
            }
            REQ_SAVE_COPY -> bg {
                contentResolver.openOutputStream(uri, "wt")!!.use { o -> FileInputStream(work).use { i -> copyStream(i, o, "Saving") } }
                setStatus("Copy saved."); toast("Copy saved")
            }
            REQ_BUILD_APK -> {
                val inApk = st.apkFile; val entry = st.apkEntry; if (inApk == null || entry == null) return
                bg {
                    val signed = File(cacheDir, "signed.apk")
                    ApkTools.buildSignedApk(this, inApk, entry, work, signed) { setStatus(it) }
                    contentResolver.openOutputStream(uri, "wt")!!.use { o -> FileInputStream(signed).use { i -> copyStream(i, o, "Writing APK") } }
                    signed.delete(); setStatus("Patched APK saved."); log("Patched APK written. Uninstall the original app, then install this one."); toast("Patched APK saved. Uninstall the original first.")
                }
            }
            REQ_LOAD_PATCHES -> bg {
                val t = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                ui { batchField.text = t }; log("Loaded patch list (${t.lines().size} lines)")
            }
            REQ_SAVE_PATCHES -> { val t = batchField.text; bg { contentResolver.openOutputStream(uri, "wt")!!.use { it.write(t.toByteArray()) }; toast("Patch list saved") } }
            REQ_PICK_META -> bg {
                val name = queryName(uri); st.dump?.close(); st.dump = null
                val old = st.metaFile; val f = File(filesDir, "meta_" + System.currentTimeMillis() + ".dat")
                contentResolver.openInputStream(uri)!!.use { i -> FileOutputStream(f).use { o -> copyStream(i, o, "Loading metadata") } }
                if (old != null && old != f) old.delete()
                st.metaFile = f; st.metaName = name
                ui { metaText.text = "Metadata: $name (${f.length()} bytes)" }; setStatus("Metadata loaded: $name"); log("Metadata loaded: $name")
            }
            REQ_SAVE_SUGG -> {
                val an = st.dump ?: return; val sg = st.suggestions
                bg {
                    setStatus("Writing JSON...")
                    BufferedWriter(OutputStreamWriter(contentResolver.openOutputStream(uri, "wt")!!, Charsets.UTF_8), 1 shl 16).use { an.writeSuggestionsJson(it, sg) }
                    setStatus("Suggestions saved."); toast("Suggestions JSON saved")
                }
            }
            REQ_SAVE_JSON -> {
                val an = st.dump ?: return; val sg = st.suggestions
                bg {
                    setStatus("Writing JSON...")
                    BufferedWriter(OutputStreamWriter(contentResolver.openOutputStream(uri, "wt")!!, Charsets.UTF_8), 1 shl 16).use { an.writeJson(it, sg) }
                    setStatus("JSON saved."); toast("JSON saved.")
                }
            }
        }
    }
    private fun copyStream(input: InputStream, output: OutputStream, label: String) {
        val buf = ByteArray(1 shl 16); var total = 0L; var next = 8L shl 20
        while (true) {
            val n = input.read(buf); if (n < 0) break
            output.write(buf, 0, n); total += n
            if (total >= next) { next += 8L shl 20; setStatus("$label... ${total shr 20} MB") }
        }
    }
    private fun queryName(uri: Uri): String {
        var n = "file.bin"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) { val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) n = c.getString(i) }
        }
        return n
    }
    private fun loadSource(uri: Uri) {
        bg {
            st.dump?.close(); st.dump = null; st.suggestions = emptyList(); st.dumpReport = ""
            ui { dumpSummary.text = ""; sumText.text = summaryLine(); renderSuggestions(); refreshHint() }
            val name = queryName(uri); st.srcUri = uri; st.srcName = name; st.patcher = null
            if (name.lowercase().endsWith(".apk")) {
                val f = File(cacheDir, "input.apk")
                contentResolver.openInputStream(uri)!!.use { i -> FileOutputStream(f).use { o -> copyStream(i, o, "Copying APK") } }
                st.apkFile = f; st.apkEntry = null
                val names = ApkTools.listCandidates(f); ui { showEntryDialog(names) }
            } else {
                st.apkFile = null; st.apkEntry = null
                contentResolver.openInputStream(uri)!!.use { i -> FileOutputStream(work).use { o -> copyStream(i, o, "Loading") } }
                finishLoad(name)
            }
        }
    }
    private fun showEntryDialog(names: List<String>) {
        if (names.isEmpty()) { toast("No .so files found in this APK"); setStatus("No .so files found."); return }
        MaterialAlertDialogBuilder(this).setTitle("Pick the file to patch")
            .setItems(names.toTypedArray()) { _, i -> extractEntry(names[i]) }
            .setNegativeButton("Cancel", null).show()
    }
    private fun extractEntry(name: String) {
        val af = st.apkFile ?: return
        bg {
            java.util.zip.ZipFile(af).use { z ->
                val e = z.getEntry(name)
                z.getInputStream(e).use { i -> FileOutputStream(work).use { o -> copyStream(i, o, "Extracting") } }
            }
            st.apkEntry = name; finishLoad("${st.srcName} > $name")
        }
    }
    private fun finishLoad(label: String) {
        val p = Patcher(work); st.patcher = p
        val e = ElfInfo.parse(work); st.elf = e
        val desc = if (e == null) "Not an ELF file (raw hex editing only)" else "ELF ${e.archName()}, ${e.segments.size} loadable segments"
        val mode = if (st.apkFile != null) "APK mode: use 'Build patched APK' to save" else "Direct mode: changes go into the original file"
        st.info = "$label\n${p.size()} bytes\n$desc\n$mode"
        ui { infoText.text = st.info; patchResult.text = "Nothing yet."; refreshHint() }
        setStatus("Ready: $label"); log("Loaded $label")
    }

    private fun is64() = st.elf?.machine != 0x28
    private fun parseHexLong(s: String): Long? = s.trim().removePrefix("0x").removePrefix("0X").toLongOrNull(16)
    private fun toFileOffset(v: Long, rva: Boolean): Long? {
        val e = st.elf; return if (rva && e != null) e.toFile(v) else v
    }
    private fun resolveBytes(text: String): ByteArray = Hex.parse(text) ?: Asm.assemble(text, is64())
    private fun writeThrough(off: Long, data: ByteArray) {
        val uri = st.srcUri ?: return; if (st.apkFile != null) return
        val pfd = contentResolver.openFileDescriptor(uri, "rw") ?: throw IllegalStateException("cannot open original for writing")
        ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { os ->
            val ch = os.channel; ch.position(off); ch.write(ByteBuffer.wrap(data)); ch.force(true)
        }
    }
    private fun applyOne(p: Patcher, off: Long, data: ByteArray, auto: Boolean): ByteArray {
        val old = p.read(off, data.size); p.write(off, data); p.journal.add(Patcher.Rec(off, old, data))
        if (auto) writeThrough(off, data); return old
    }
    private fun selectedType(): String {
        val i = typeIds.indexOf(typeGroup.checkedButtonId); return types[if (i < 0) 0 else i]
    }
    private fun showPresets() {
        MaterialAlertDialogBuilder(this).setTitle("Presets")
            .setItems(presets.map { it.first }.toTypedArray()) { _, i -> bytesField.text = presets[i].second }
            .setNegativeButton("Close", null).show()
    }
    private fun doRead() {
        val p = st.patcher ?: return toast("Open a file first")
        val v = parseHexLong(offsetField.text) ?: return toast("Offset must be hex")
        val off = toFileOffset(v, rvaSwitch.isChecked) ?: return toast("Address is not inside the file")
        val len = (lenField.text.toIntOrNull() ?: 64).coerceIn(1, 4096)
        bg { val b = p.read(off, len); show(patchResult, "file offset 0x%X\n%s".format(off, Hex.dump(b, off))) }
    }
    private fun doPatch() {
        val p = st.patcher ?: return toast("Open a file first")
        val v = parseHexLong(offsetField.text) ?: return toast("Offset must be hex")
        val off = toFileOffset(v, rvaSwitch.isChecked) ?: return toast("Address is not inside the file")
        val data = try { resolveBytes(bytesField.text) } catch (e: Exception) { return toast("Bytes: ${e.message}") }
        if (off < 0 || off + data.size > p.size()) return toast("Patch runs past the end of the file")
        val auto = autoSwitch.isChecked
        bg {
            val old = applyOne(p, off, data, auto)
            show(patchResult, "Patched file offset 0x%X\nold: %s\nnew: %s".format(off, Hex.fmt(old), Hex.fmt(data)))
            log("0x%X  %s -> %s".format(off, Hex.fmt(old), Hex.fmt(data))); setStatus("${p.journal.size} change(s) this session")
        }
    }
    private fun doReturnValue() {
        val p = st.patcher ?: return toast("Open a file first")
        val v = parseHexLong(offsetField.text) ?: return toast("Offset must be hex")
        val off = toFileOffset(v, rvaSwitch.isChecked) ?: return toast("Address is not inside the file")
        val type = selectedType(); val valueTxt = valueField.text.trim()
        val data = try { Asm.returnValue(type, valueTxt, is64()) } catch (e: Exception) { return toast("Value: ${e.message}") }
        if (off < 0 || off + data.size > p.size()) return toast("Patch runs past the end of the file")
        val auto = autoSwitch.isChecked
        bg {
            val old = applyOne(p, off, data, auto)
            val msg = "Function now returns " + type + " " + valueTxt + "\n" + data.size + " bytes written at file offset 0x%X\nold: %s\nnew: %s"
            show(returnResult, msg.format(off, Hex.fmt(old), Hex.fmt(data)))
            log("0x%X  return %s %s".format(off, type, valueTxt)); setStatus("${p.journal.size} change(s) this session")
        }
    }
    private fun runBatch() {
        val p = st.patcher ?: return toast("Open a file first")
        val text = batchField.text; val rva = rvaSwitch.isChecked; val dry = dryCheck.isChecked
        val auto = autoSwitch.isChecked; val is64 = is64()
        bg {
            val out = ArrayList<String>(); var failed = 0
            val (ops, errs) = Batch.parse(text, is64)
            for (e in errs) { failed++; out.add("ERR $e") }
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
                        base = toFileOffset(v, rva) ?: throw IllegalStateException("address 0x%X is not in a file-backed segment".format(v))
                    }
                    val off = base + op.delta
                    if (off < 0 || off + op.new.size > p.size()) throw IllegalStateException("outside the file")
                    val cur = p.read(off, op.new.size)
                    if (op.old != null && !cur.contentEquals(op.old)) throw IllegalStateException("old bytes differ (found ${Hex.fmt(cur)})")
                    if (cur.contentEquals(op.new)) { out.add("line ${op.line}: already applied at 0x%X".format(off)); continue }
                    planned.add(Pair(op, off))
                    out.add("line ${op.line}: ${if (dry) "would patch" else "patch"} 0x%X  %s -> %s".format(off, Hex.fmt(cur), Hex.fmt(op.new)))
                } catch (e: Exception) { failed++; out.add("ERR line ${op.line}: ${e.message}") }
            }
            if (failed > 0) out.add("Nothing applied: $failed error(s).")
            else if (dry) out.add("Dry run OK: ${planned.size} patch(es) would be applied.")
            else { for ((op, off) in planned) applyOne(p, off, op.new, auto); out.add("Applied ${planned.size} patch(es)."); setStatus("${p.journal.size} change(s) this session") }
            show(batchResult, out.joinToString("\n")); log("Batch: ${out.last()}"); if (!dry) showPatchedWithUndo(planned.size)
        }
    }
    private fun doFind() {
        val p = st.patcher ?: return toast("Open a file first")
        val pat = BytePattern.parse(patternField.text) ?: return toast("Bad pattern")
        setStatus("Searching...")
        bg {
            val hits = p.find(pat, 200); val e = st.elf
            val labels = hits.map {
                val v = e?.toVaddr(it)
                if (v != null) "file 0x%X   RVA 0x%X".format(it, v) else "file 0x%X".format(it)
            }
            setStatus("${hits.size} match(es)${if (hits.size >= 200) " (first 200)" else ""}")
            ui {
                if (hits.isEmpty()) searchResult.text = "No matches."
                else {
                    searchResult.text = labels.joinToString("\n")
                    MaterialAlertDialogBuilder(this).setTitle("${hits.size} match(es), tap to use")
                        .setItems(labels.toTypedArray()) { _, i ->
                            val f = hits[i]; val v = e?.toVaddr(f)
                            val use = if (rvaSwitch.isChecked && v != null) v else f
                            offsetField.text = "%X".format(use); goPatch(0)
                        }.setNegativeButton("Close", null).show()
                }
            }
        }
    }
    private fun undo(all: Boolean) {
        val p = st.patcher ?: return toast("Open a file first")
        bg {
            var n = if (all) p.journal.size else minOf(1, p.journal.size)
            if (n == 0) { toast("Nothing to undo"); return@bg }
            val total = n
            while (n > 0) { val r = p.journal.removeAt(p.journal.size - 1); p.write(r.off, r.old); writeThrough(r.off, r.old); n-- }
            log("Undid $total change(s)"); setStatus("${p.journal.size} change(s) this session")
        }
    }
    private fun saveOriginal() {
        val uri = st.srcUri
        if (st.patcher == null || uri == null) return toast("Open a file first")
        if (st.apkFile != null) return toast("APK mode: use 'Build patched APK'")
        bg {
            contentResolver.openOutputStream(uri, "wt")!!.use { o -> FileInputStream(work).use { i -> copyStream(i, o, "Saving") } }
            setStatus("Saved to original."); toast("Saved to original file")
        }
    }
    private fun saveCopy() {
        if (st.patcher == null) return toast("Open a file first")
        val entry = st.apkEntry; val name = if (entry != null) entry.substringAfterLast('/') else st.srcName
        createDoc(REQ_SAVE_COPY, "application/octet-stream", name)
    }
    private fun buildApk() {
        if (st.patcher == null || st.apkFile == null || st.apkEntry == null) return toast("Open an .apk and pick a file inside it first")
        createDoc(REQ_BUILD_APK, "application/vnd.android.package-archive", st.srcName.removeSuffix(".apk") + "_patched.apk")
    }
    private fun saveText() { createDoc(REQ_SAVE_PATCHES, "text/plain", "patches.txt") }
}
