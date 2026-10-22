package app.voicelists

import android.app.Activity
import android.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

/** Asks which category to use: the existing ones, plus a new one typed by hand. */
fun Activity.pickCategory(title: String, categories: MutableList<Category>, exclude: Category? = null, onPick: (Category) -> Unit) {
    val s = Lang.strings(this)
    val options = categories.filter { it !== exclude }
    val labels = options.map { it.name } + ("+ " + s.newCategory)
    AlertDialog.Builder(this)
        .setTitle(title)
        .setItems(labels.toTypedArray()) { _, which ->
            if (which < options.size) onPick(options[which])
            else prompt(s.newCategory, "") { name ->
                val c = Matcher.findCategory(categories, name)?.takeIf { Matcher.sameText(it.name, name) }
                    ?: Category(Store.newId(), name).also { categories.add(it) }
                onPick(c)
            }
        }
        .setNegativeButton(s.cancel, null)
        .show()
}

/**
 * Submits the field when keyboard dictation ends with "add" / "הוסף" ("milk add" -> "milk").
 * Only reacts to text inserted several letters at once, as dictation does, so typing a word
 * that starts with "add" isn't cut short.
 */
fun EditText.submitOnDictatedAdd(onItem: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        private var dictated = false
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            dictated = count - before >= 2
        }
        override fun afterTextChanged(e: Editable?) {
            if (!dictated) return
            dictated = false
            val item = Matcher.stripTrailingAdd(e?.toString() ?: return) ?: return
            post {
                setText("")
                onItem(item)
            }
        }
    })
}
