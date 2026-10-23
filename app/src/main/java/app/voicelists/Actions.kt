package app.voicelists

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Toast

/** Opens the voice window for adding several items in a row; the list and category are asked unless given. */
fun Activity.startAddMany(list: ItemList? = null, category: Category? = null) {
    val intent = Intent(this, VoiceActivity::class.java).putExtra(VoiceActivity.EXTRA_BATCH, true)
    list?.let { intent.putExtra(VoiceActivity.EXTRA_LIST, it.id) }
    category?.let { intent.putExtra(VoiceActivity.EXTRA_CATEGORY, it.id) }
    startActivity(intent)
}

/** Asks which category to use: the shared ones, plus a new one typed by hand. */
fun Activity.pickCategory(title: String, data: Data, exclude: Category? = null, onPick: (Category) -> Unit) {
    val s = Lang.strings(this)
    val options = data.categories.filter { it !== exclude }
    val labels = options.map { it.name } + s.newCategoryButton
    AlertDialog.Builder(this)
        .setTitle(title)
        .setItems(labels.toTypedArray()) { _, which ->
            if (which < options.size) onPick(options[which])
            else prompt(s.newCategory, "") { onPick(data.categoryNamed(it)) }
        }
        .setNegativeButton(s.cancel, null)
        .show()
}

/** Asks which open list to use, only when there's more than one. With none open, a new list is started. */
fun Activity.pickList(data: Data, exclude: ItemList? = null, onPick: (ItemList) -> Unit) {
    val s = Lang.strings(this)
    val options = if (exclude == null) data.openListsOrNew(s) else data.openLists.filter { it !== exclude }
    if (exclude == null && options.size == 1) return onPick(options[0])
    if (options.isEmpty()) return
    AlertDialog.Builder(this)
        .setTitle(s.askList)
        .setItems(options.map { it.name }.toTypedArray()) { _, which -> onPick(options[which]) }
        .setNegativeButton(s.cancel, null)
        .show()
}

/** Adds a typed or dictated item: asks the category, then the list (unless [list] is given). */
fun Activity.addItemFlow(data: Data, raw: String, list: ItemList?, done: () -> Unit) {
    val s = Lang.strings(this)
    val text = raw.trim().replaceFirstChar { it.uppercase(s.locale) }
    if (text.isEmpty()) return
    pickCategory("${s.askCategory} ($text)", data) { cat ->
        fun addTo(l: ItemList) {
            if (Store.findOpenItem(l, cat.id, text) != null) {
                Toast.makeText(this, s.alreadyExists(text, cat.name, l.name), Toast.LENGTH_SHORT).show()
            } else {
                Store.addItem(l, cat, text)
                Toast.makeText(this, s.added(text, cat.name, l.name), Toast.LENGTH_SHORT).show()
            }
            Store.save(this, data)
            done()
        }
        if (list != null) addTo(list) else pickList(data) { addTo(it) }
    }
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
