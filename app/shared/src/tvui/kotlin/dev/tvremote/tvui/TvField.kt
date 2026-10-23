package dev.tvremote.tvui

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * A text field the D-pad can always leave. The TV's keyboard took the arrows while it was up (Gboard on an FPT box), and
 * Done that ran the field's action without closing it looked like nothing happened: the person was stuck in the field.
 * - the keyboard does not rise on focus: a phone's remote app types into the field without one; OK on the field raises it
 * - Done / Search / Go on the keyboard, or Enter from a phone, closes the keyboard, runs [done] once (TextView calls the
 *   listener on Enter's down and up) and takes the focus out of the field, to [after] or whatever is below
 * The web page's fields do the same in js/ontv.js.
 */
object TvField {
    fun wire(f: EditText, after: () -> View? = { null }, done: () -> Unit) {
        f.showSoftInputOnFocus = false
        f.setOnKeyListener { v, k, e ->
            if (k != KeyEvent.KEYCODE_DPAD_CENTER) return@setOnKeyListener false
            if (e.action == KeyEvent.ACTION_UP) keys(v).showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
            true
        }
        f.setOnEditorActionListener { v, id, e ->
            if (e != null && e.action != KeyEvent.ACTION_DOWN) return@setOnEditorActionListener true
            if (e == null && id == EditorInfo.IME_ACTION_NONE) return@setOnEditorActionListener false
            keys(v).hideSoftInputFromWindow(v.windowToken, 0)
            done()
            (after() ?: v.focusSearch(View.FOCUS_DOWN))?.requestFocus()
            true
        }
    }

    private fun keys(v: View) = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
}
