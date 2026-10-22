package app.voicelists

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Paint
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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** Every category with its items underneath; add by typing, dictating or voice; move items between categories. */
class MainActivity : Activity() {

    /** One row of the screen: a category heading, or an item under it. */
    private sealed class Row {
        class Header(val category: Category) : Row()
        class Entry(val category: Category, val item: Item) : Row()
        class Empty(val category: Category) : Row()
    }

    private lateinit var s: Str
    private var categories = mutableListOf<Category>()
    private var rows = listOf<Row>()
    private lateinit var listView: ListView
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
        categories = Store.load(this)
        refresh()
    }

    private fun refresh() {
        rows = categories.flatMap { c ->
            // Open items first, checked items at the bottom.
            val items = c.items.filter { !it.done } + c.items.filter { it.done }
            listOf(Row.Header(c)) + if (items.isEmpty()) listOf(Row.Empty(c)) else items.map { Row.Entry(c, it) }
        }
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (categories.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun save() {
        Store.save(this, categories)
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
            addItem(v)
        }
        input.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) { submit(); true } else false
        }
        input.submitOnDictatedAdd { addItem(it) }
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

        listView = ListView(this).apply {
            adapter = this@MainActivity.adapter
            divider = null
            setPadding(dp(8), 0, dp(8), dp(8))
            clipToPadding = false
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
        bar.addView(pill("+ " + s.newCategory, filled = false) { newCategory() }, lp)
        bar.addView(pill("🎤 " + s.voiceAdd) {
            startActivity(Intent(this, VoiceActivity::class.java))
        }, lp)

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
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
            when (val r = rows[position]) {
                is Row.Header -> header(r.category)
                is Row.Entry -> entry(r.category, r.item)
                is Row.Empty -> text(s.noItems, 15f).apply {
                    alpha = 0.5f
                    setPadding(dp(20), dp(4), dp(20), dp(8))
                }
            }
    }

    private fun header(c: Category): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(20), dp(12), dp(6))
        addView(text(c.name, 19f, bold = true).apply { setTextColor(accent()) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(text(c.openCount.toString(), 15f).apply { alpha = 0.6f })
        setOnClickListener {
            startActivity(Intent(this@MainActivity, CategoryActivity::class.java)
                .putExtra(CategoryActivity.EXTRA_ID, c.id))
        }
        setOnLongClickListener { editCategory(c); true }
    }

    private fun entry(c: Category, item: Item): View {
        val check = CheckBox(this).apply {
            text = item.text
            textSize = 17f
            isChecked = item.done
            setPadding(dp(8), dp(10), dp(8), dp(10))
            paintFlags = if (item.done) paintFlags or Paint.STRIKE_THRU_TEXT_FLAG else paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            alpha = if (item.done) 0.5f else 1f
            setOnClickListener {
                item.done = !item.done
                save()
            }
            setOnLongClickListener { itemMenu(c, item); true }
        }
        // One tap to move the item to another category.
        val move = text("⇄", 22f).apply {
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setTextColor(accent())
            contentDescription = s.moveTo
            setOnClickListener { moveItem(c, item) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
            addView(check, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(move)
        }
    }

    private fun addItem(raw: String) {
        val text = raw.trim().replaceFirstChar { it.uppercase(s.locale) }
        val existing = Store.findOpenItem(categories, text)
        if (existing != null) {
            toast(s.alreadyExists(text, existing.first.name))
            return
        }
        pickCategory("${s.askCategory} ($text)", categories) { c ->
            Store.addItem(c, text)
            save()
            toast(s.addedTo(text, c.name))
        }
    }

    private fun moveItem(from: Category, item: Item) {
        pickCategory("${s.moveTo} (${item.text})", categories, exclude = from) { to ->
            Store.moveItem(item, from, to)
            save()
            toast(s.movedTo(item.text, to.name))
        }
    }

    private fun itemMenu(c: Category, item: Item) {
        AlertDialog.Builder(this)
            .setTitle(item.text)
            .setItems(arrayOf(s.moveTo, s.rename, s.delete)) { _, which ->
                when (which) {
                    0 -> moveItem(c, item)
                    1 -> prompt(s.rename, item.text) {
                        item.text = it
                        save()
                    }
                    else -> {
                        c.items.remove(item)
                        save()
                    }
                }
            }
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun newCategory() {
        prompt(s.newCategory, "") { name ->
            categories.add(Category(Store.newId(), name))
            save()
        }
    }

    private fun editCategory(c: Category) {
        AlertDialog.Builder(this)
            .setTitle(c.name)
            .setItems(arrayOf(s.rename, s.deleteList)) { _, which ->
                if (which == 0) prompt(s.rename, c.name) { name ->
                    c.name = name
                    Store.save(this, categories)
                    reload()
                } else AlertDialog.Builder(this)
                    .setMessage(s.confirmDelete(c.name))
                    .setPositiveButton(s.delete) { _, _ ->
                        categories.remove(c)
                        Store.save(this, categories)
                        reload()
                    }
                    .setNegativeButton(s.cancel, null)
                    .show()
            }
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
