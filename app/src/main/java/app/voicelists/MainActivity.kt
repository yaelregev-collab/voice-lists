package app.voicelists

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** The lists, open ones first. Items added here ask for a category, then a list. */
class MainActivity : Activity() {

    private sealed class Row {
        class Open(val list: ItemList) : Row()
        object ClosedHeader : Row()
        class Closed(val list: ItemList) : Row()
    }

    private lateinit var s: Str
    private var data = Data(mutableListOf(), mutableListOf())
    private var rows = listOf<Row>()
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        // The language may follow the keyboard, which can change while we're away.
        if (Lang.strings(this).he != s.he) buildUi()
        reload()
    }

    private fun reload() {
        data = Store.load(this)
        refresh()
    }

    private fun refresh() {
        val closed = data.lists.filter { it.closed }
        rows = data.openLists.map { Row.Open(it) } +
            (if (closed.isEmpty()) emptyList() else listOf(Row.ClosedHeader) + closed.map { Row.Closed(it) })
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (data.lists.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun save() {
        Store.save(this, data)
        refresh()
    }

    private fun buildUi() {
        s = Lang.strings(this)
        title = s.appName
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Lang.applyDirection(root, s.he)

        val input = EditText(this).apply {
            hint = s.newItemHint
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        fun submit() {
            val v = input.text.toString().trim()
            if (v.isEmpty()) return
            input.setText("")
            addItemFlow(data, v, null) { refresh() }
        }
        input.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) { submit(); true } else false
        }
        input.submitOnDictatedAdd { addItemFlow(data, it, null) { refresh() } }
        val voice = ImageButton(this).apply {
            setImageResource(R.drawable.ic_mic)
            setColorFilter(accent())
            background = null
            contentDescription = s.voiceAdd
            setOnClickListener { startActivity(Intent(this@MainActivity, VoiceActivity::class.java)) }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(8), dp(8))
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(pill(s.add) { submit() })
            addView(voice, LinearLayout.LayoutParams(dp(48), dp(48)))
        }

        val listView = ListView(this).apply {
            adapter = this@MainActivity.adapter
            divider = null
            setPadding(dp(8), 0, dp(8), dp(8))
            clipToPadding = false
            setOnItemClickListener { _, _, pos, _ ->
                val l = when (val r = rows[pos]) {
                    is Row.Open -> r.list
                    is Row.Closed -> r.list
                    else -> return@setOnItemClickListener
                }
                startActivity(Intent(this@MainActivity, ListActivity::class.java).putExtra(ListActivity.EXTRA_ID, l.id))
            }
            setOnItemLongClickListener { _, _, pos, _ ->
                when (val r = rows[pos]) {
                    is Row.Open -> listMenu(r.list)
                    is Row.Closed -> listMenu(r.list)
                    else -> {}
                }
                true
            }
        }
        emptyView = text(s.empty + "\n\n" + s.widgetHint, 16f).apply {
            setPadding(dp(32), dp(48), dp(32), dp(32))
            gravity = Gravity.CENTER
            alpha = 0.7f
        }
        val content = FrameLayout(this).apply {
            addView(listView)
            addView(emptyView)
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(20))
        }
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(6); marginEnd = dp(6)
        }
        bar.addView(pill("+ " + s.newList, filled = false) { newList() }, lp)
        bar.addView(pill(s.categories, filled = false) { manageCategories() }, lp)

        root.addView(top)
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bar)
        setContentView(root)
        invalidateOptionsMenu()
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun isEnabled(position: Int) = rows[position] !is Row.ClosedHeader
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
            when (val r = rows[position]) {
                is Row.Open -> listRow(r.list)
                is Row.Closed -> listRow(r.list).apply { alpha = 0.5f }
                is Row.ClosedHeader -> text(s.closedLists, 14f, bold = true).apply {
                    setPadding(dp(20), dp(24), dp(20), dp(6))
                    alpha = 0.6f
                }
            }
    }

    private fun listRow(l: ItemList): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(18))
        addView(text(l.name, 18f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(text(if (l.closed) s.closed else l.openCount.toString(), 16f).apply { alpha = 0.6f })
    }

    private fun newList() {
        prompt(s.newList, "") { name ->
            data.lists.add(ItemList(Store.newId(), name))
            save()
        }
    }

    private fun listMenu(l: ItemList) {
        AlertDialog.Builder(this)
            .setTitle(l.name)
            .setItems(arrayOf(if (l.closed) s.reopenList else s.closeList, s.rename, s.deleteList)) { _, which ->
                when (which) {
                    0 -> { l.closed = !l.closed; save() }
                    1 -> prompt(s.rename, l.name) { l.name = it; save() }
                    else -> confirmDeleteList(l)
                }
            }
            .show()
    }

    private fun confirmDeleteList(l: ItemList) {
        AlertDialog.Builder(this)
            .setMessage(s.confirmDelete(l.name))
            .setPositiveButton(s.delete) { _, _ -> data.lists.remove(l); save() }
            .setNegativeButton(s.cancel, null)
            .show()
    }

    /** The shared categories: add, rename, delete (only when no item uses it). */
    private fun manageCategories() {
        val labels = data.categories.map { it.name } + s.newCategoryButton
        AlertDialog.Builder(this)
            .setTitle(s.categories)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == data.categories.size) {
                    prompt(s.newCategory, "") { data.categoryNamed(it); save(); manageCategories() }
                    return@setItems
                }
                val c = data.categories[which]
                AlertDialog.Builder(this)
                    .setTitle(c.name)
                    .setItems(arrayOf(s.rename, s.delete)) { _, action ->
                        if (action == 0) prompt(s.rename, c.name) { c.name = it; save(); manageCategories() }
                        else deleteCategory(c)
                    }
                    .show()
            }
            .setNegativeButton(s.cancel, null)
            .show()
    }

    private fun deleteCategory(c: Category) {
        val used = data.lists.sumOf { l -> l.items.count { it.categoryId == c.id } }
        if (used > 0) {
            AlertDialog.Builder(this).setMessage(s.categoryInUse(c.name, used)).setPositiveButton(android.R.string.ok, null).show()
            return
        }
        AlertDialog.Builder(this)
            .setMessage(s.confirmDeleteCategory(c.name))
            .setPositiveButton(s.delete) { _, _ -> data.categories.remove(c); save(); manageCategories() }
            .setNegativeButton(s.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, s.language)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != 1) return super.onOptionsItemSelected(item)
        val values = arrayOf(Lang.AUTO, Lang.HEBREW, Lang.ENGLISH)
        val labels = arrayOf(s.langAuto, s.langHebrew, s.langEnglish)
        AlertDialog.Builder(this)
            .setTitle(s.language)
            .setSingleChoiceItems(labels, values.indexOf(Lang.setting(this))) { d, which ->
                Lang.setSetting(this, values[which])
                d.dismiss()
                AddItemWidget.refreshAll(this)
                buildUi()
                reload()
            }
            .show()
        return true
    }
}

/** A one-field text dialog. */
fun Activity.prompt(title: String, initial: String, onOk: (String) -> Unit) {
    val s = Lang.strings(this)
    val input = EditText(this).apply {
        setText(initial)
        setSelection(initial.length)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }
    val box = FrameLayout(this).apply {
        setPadding(dp(24), dp(8), dp(24), 0)
        addView(input)
    }
    AlertDialog.Builder(this)
        .setTitle(title)
        .setView(box)
        .setPositiveButton(s.save) { _, _ ->
            val v = input.text.toString().trim()
            if (v.isNotEmpty()) onOk(v)
        }
        .setNegativeButton(s.cancel, null)
        .show()
    input.requestFocus()
}
