package com.example.gifkeyboard.ime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import com.example.gifkeyboard.R

/**
 * Manages three keyboard layers and switches between them:
 *   LETTERS  — standard QWERTY, shift / caps-lock
 *   SYMBOLS  — ?123 layer (punctuation, digits, common symbols)
 *   EXTENDED — =\< layer (maths, brackets, less-common symbols)
 *
 * Long-pressing any vowel key (A E I O U) raises a small popup with
 * accented variants so the keyboard is usable for most Western languages.
 */
class KeyboardLayoutBuilder(
    private val context: Context,
    private val listener: KeyEventListener
) {

    // ── Public event contract ─────────────────────────────────────────────
    interface KeyEventListener {
        fun onCharacterKey(char: String)
        fun onBackspace()
        fun onSpace()
        fun onEnter()
    }

    // ── Layout state ──────────────────────────────────────────────────────
    enum class Layer { LETTERS, SYMBOLS, EXTENDED }

    private var layer = Layer.LETTERS
    private var isShifted = false
    private var isCapsLock = false

    // Keep references so we can rebuild rows in-place when switching layers
    private var row1: LinearLayout? = null
    private var row2: LinearLayout? = null
    private var row3: LinearLayout? = null
    private var row4: LinearLayout? = null

    private val letterButtons = mutableListOf<Button>()

    // Long-press handler for accents
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var accentPopup: PopupWindow? = null

    // ── Key data ──────────────────────────────────────────────────────────
    private val row1Letters = "qwertyuiop".map { it.toString() }
    private val row2Letters = "asdfghjkl".map  { it.toString() }
    private val row3Letters = "zxcvbnm".map    { it.toString() }

    //  Symbols layer (?123)
    private val row1Sym = listOf("1","2","3","4","5","6","7","8","9","0")
    private val row2Sym = listOf("@","#","$","%","&","-","+","(",")")
    private val row3Sym = listOf("*",'"',"'",":",";","!","?")

    //  Extended layer (=\<)
    private val row1Ext = listOf("~","`","|","•","√","π","÷","×","¶","∆")
    private val row2Ext = listOf("£","¢","€","¥","^","°","=","\\","{","}")
    private val row3Ext = listOf("[","]","<",">","_","—","/")

    // Accent variants keyed by base letter (lowercase)
    private val accents = mapOf(
        "a" to listOf("à","á","â","ã","ä","å","æ"),
        "e" to listOf("è","é","ê","ë"),
        "i" to listOf("ì","í","î","ï"),
        "o" to listOf("ò","ó","ô","õ","ö","ø"),
        "u" to listOf("ù","ú","û","ü"),
        "n" to listOf("ñ"),
        "c" to listOf("ç"),
        "s" to listOf("ß")
    )

    // ── Public API ────────────────────────────────────────────────────────

    fun build(
        r1: LinearLayout,
        r2: LinearLayout,
        r3: LinearLayout,
        r4: LinearLayout
    ) {
        row1 = r1; row2 = r2; row3 = r3; row4 = r4
        rebuildRows()
    }

    // ── Layer switching ───────────────────────────────────────────────────

    private fun rebuildRows() {
        val r1 = row1 ?: return
        val r2 = row2 ?: return
        val r3 = row3 ?: return
        val r4 = row4 ?: return

        r1.removeAllViews(); r2.removeAllViews()
        r3.removeAllViews(); r4.removeAllViews()
        letterButtons.clear()

        when (layer) {
            Layer.LETTERS  -> buildLetterRows(r1, r2, r3, r4)
            Layer.SYMBOLS  -> buildSymbolRows(r1, r2, r3, r4, extended = false)
            Layer.EXTENDED -> buildSymbolRows(r1, r2, r3, r4, extended = true)
        }
    }

    // ── Letter layer ──────────────────────────────────────────────────────

    private fun buildLetterRows(
        r1: LinearLayout, r2: LinearLayout,
        r3: LinearLayout, r4: LinearLayout
    ) {
        row1Letters.forEach { r1.addView(makeLetterKey(it)) }
        row2Letters.forEach { r2.addView(makeLetterKey(it)) }

        r3.addView(makeShiftKey())
        row3Letters.forEach { r3.addView(makeLetterKey(it)) }
        r3.addView(makeBackspaceKey())

        r4.addView(makeLayerSwitchKey("?123", Layer.SYMBOLS, weight = 1.5f))
        r4.addView(makeCharKey(",", weight = 0.8f))
        r4.addView(makeSpaceKey())
        r4.addView(makeCharKey(".", weight = 0.8f))
        r4.addView(makeActionKey("⏎", weight = 1.5f) { listener.onEnter() })

        applyShiftState()
    }

    private fun makeLetterKey(letter: String): Button {
        val display = if (isShifted) letter.uppercase() else letter
        val btn = Button(context, null, 0).apply {
            text = display
            textSize = 18f
            isAllCaps = false
            setTextColor(ContextCompat.getColor(context, R.color.key_text))
            background = ContextCompat.getDrawable(context, R.drawable.key_bg_selector)
            layoutParams = rowLP(1f)
            setPadding(0, 0, 0, 0)

            setOnClickListener {
                val ch = if (isShifted) letter.uppercase() else letter
                listener.onCharacterKey(ch)
                if (isShifted && !isCapsLock) { isShifted = false; applyShiftState() }
            }

            // Long-press: show accent popup (if variants exist for this letter)
            setOnLongClickListener {
                val variants = accents[letter.lowercase()]
                if (!variants.isNullOrEmpty()) showAccentPopup(this, letter, variants)
                true
            }
        }
        letterButtons.add(btn)
        return btn
    }

    private fun makeShiftKey(): ImageButton {
        return ImageButton(context).apply {
            setImageResource(R.drawable.ic_shift)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            background = ContextCompat.getDrawable(context, R.drawable.key_special_bg_selector)
            layoutParams = rowLP(1.3f)
            setOnClickListener {
                when {
                    !isShifted            -> { isShifted = true;  isCapsLock = false }
                    isShifted && !isCapsLock -> { isCapsLock = true }
                    else                  -> { isShifted = false; isCapsLock = false }
                }
                applyShiftState()
            }
        }
    }

    private fun applyShiftState() {
        letterButtons.forEach { btn ->
            val t = btn.text.toString()
            if (t.length == 1 && t[0].isLetter())
                btn.text = if (isShifted) t.uppercase() else t.lowercase()
        }
    }

    // ── Symbol / Extended layers ──────────────────────────────────────────

    private fun buildSymbolRows(
        r1: LinearLayout, r2: LinearLayout,
        r3: LinearLayout, r4: LinearLayout,
        extended: Boolean
    ) {
        val keys1 = if (extended) row1Ext else row1Sym
        val keys2 = if (extended) row2Ext else row2Sym
        val keys3 = if (extended) row3Ext else row3Sym

        keys1.forEach { r1.addView(makeSymbolKey(it)) }
        keys2.forEach { r2.addView(makeSymbolKey(it)) }

        // Row 3: mini layer-toggle + symbol keys + backspace
        val toggleLabel = if (extended) "?123" else "=\\<"
        val toggleTarget = if (extended) Layer.SYMBOLS else Layer.EXTENDED
        r3.addView(makeLayerSwitchKey(toggleLabel, toggleTarget, weight = 1.3f))
        keys3.forEach { r3.addView(makeSymbolKey(it)) }
        r3.addView(makeBackspaceKey())

        // Row 4: back to ABC + space + enter
        r4.addView(makeLayerSwitchKey("ABC", Layer.LETTERS, weight = 1.5f))
        r4.addView(makeCharKey(",", weight = 0.8f))
        r4.addView(makeSpaceKey())
        r4.addView(makeCharKey(".", weight = 0.8f))
        r4.addView(makeActionKey("⏎", weight = 1.5f) { listener.onEnter() })
    }

    private fun makeSymbolKey(symbol: String): Button {
        return Button(context, null, 0).apply {
            text = symbol
            textSize = 17f
            isAllCaps = false
            setTextColor(ContextCompat.getColor(context, R.color.key_text))
            background = ContextCompat.getDrawable(context, R.drawable.key_bg_selector)
            layoutParams = rowLP(1f)
            setPadding(0, 0, 0, 0)
            setOnClickListener { listener.onCharacterKey(symbol) }
        }
    }

    private fun makeLayerSwitchKey(label: String, target: Layer, weight: Float): Button {
        return Button(context, null, 0).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(ContextCompat.getColor(context, R.color.key_text))
            background = ContextCompat.getDrawable(context, R.drawable.key_special_bg_selector)
            layoutParams = rowLP(weight)
            setPadding(0, 0, 0, 0)
            setOnClickListener { layer = target; rebuildRows() }
        }
    }

    // ── Shared key factories ──────────────────────────────────────────────

    private fun makeBackspaceKey(): ImageButton {
        return ImageButton(context).apply {
            setImageResource(R.drawable.ic_backspace)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            background = ContextCompat.getDrawable(context, R.drawable.key_special_bg_selector)
            layoutParams = rowLP(1.3f)
            setOnClickListener { listener.onBackspace() }
        }
    }

    private fun makeSpaceKey(): Button {
        return Button(context, null, 0).apply {
            text = "space"
            textSize = 13f
            isAllCaps = false
            setTextColor(ContextCompat.getColor(context, R.color.search_hint_text))
            background = ContextCompat.getDrawable(context, R.drawable.key_bg_selector)
            layoutParams = rowLP(4f)
            setPadding(0, 0, 0, 0)
            setOnClickListener { listener.onSpace() }
        }
    }

    private fun makeCharKey(ch: String, weight: Float): Button {
        return Button(context, null, 0).apply {
            text = ch
            textSize = 16f
            isAllCaps = false
            setTextColor(ContextCompat.getColor(context, R.color.key_text))
            background = ContextCompat.getDrawable(context, R.drawable.key_bg_selector)
            layoutParams = rowLP(weight)
            setPadding(0, 0, 0, 0)
            setOnClickListener { listener.onCharacterKey(ch) }
        }
    }

    private fun makeActionKey(label: String, weight: Float, action: () -> Unit): Button {
        return Button(context, null, 0).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            setTextColor(ContextCompat.getColor(context, R.color.key_text))
            background = ContextCompat.getDrawable(context, R.drawable.key_special_bg_selector)
            layoutParams = rowLP(weight)
            setPadding(0, 0, 0, 0)
            setOnClickListener { action() }
        }
    }

    private fun rowLP(weight: Float) =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
            marginStart = 2; marginEnd = 2
        }

    // ── Accent popup ──────────────────────────────────────────────────────

    private fun showAccentPopup(anchor: View, base: String, variants: List<String>) {
        accentPopup?.dismiss()

        // Build a horizontal row of small buttons, one per variant
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.key_special_bg))
            setPadding(8, 8, 8, 8)
        }

        // Optionally include the base key itself as the first option
        val all = listOf(if (isShifted) base.uppercase() else base) + variants.map {
            if (isShifted) it.uppercase() else it
        }

        all.forEach { ch ->
            val btn = Button(context, null, 0).apply {
                text = ch
                textSize = 16f
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                setTextColor(ContextCompat.getColor(context, R.color.key_text))
                background = ContextCompat.getDrawable(context, R.drawable.key_bg_selector)
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(42), dpToPx(48)
                ).apply { marginStart = 4; marginEnd = 4 }
                setOnClickListener {
                    listener.onCharacterKey(ch)
                    if (isShifted && !isCapsLock) { isShifted = false; applyShiftState() }
                    accentPopup?.dismiss()
                    accentPopup = null
                }
            }
            row.addView(btn)
        }

        val popup = PopupWindow(
            row,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dpToPx(64),
            true          // focusable=true so it dismisses on outside touch
        ).apply {
            elevation = 16f
            animationStyle = android.R.style.Animation_Dialog
        }
        accentPopup = popup

        // Show above the anchor key
        anchor.post {
            popup.showAsDropDown(anchor, 0, -dpToPx(120))
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
