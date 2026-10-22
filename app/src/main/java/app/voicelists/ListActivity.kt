package app.voicelists

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
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

/** One list: its items grouped by the shared categories. Tick off, add, change category, move, close. */
class ListActivity : Activity() {

    companion object {
        const val EXTRA_ID = "id"
    }

    private sealed class Row {
        class Header(val name: String, val count: Int) : Row()
        class Entry(val item: Item) : Row()
    }

    private lateinit var s: Str
    private var data = Data(mutableListOf(), mutableListOf())
    private var list: ItemList? = null
    private var rows = listOf<Row>()
    private lateinit var top: View
    private lateinit var closedNote: TextView
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = Lang.strings(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Lang.applyDirection(root, s.he)

        val input = EditText(this).apply {
            hint = s.newItemHint
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        fun add(text: String) {
            val l = list ?: return
            addItemFlow(data, text, l) { refresh() }
        }
        fun submit() {
            val v = input.text.toString().trim()
            if (v.isEmpty()) return
            input.setText("")
            add(v)
        }
        input.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) { submit(); true } else false
        }
        input.submitOnDictatedAdd { add(it) }
        val voice = ImageButton(this).apply {
            setImageResource(R.drawable.ic_mic)
            setColorFilter(accent())
            background = null
            contentDescription = s.voiceAdd
            setOnClickListener {
                list?.let {
                    startActivity(Intent(this@ListActivity, VoiceActivity::class.java)
                        .putExtra(VoiceActivity.EXTRA_LIST, it.id))
                }
            }
        }
        top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(8), dp(8))
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(pill(s.add) { submit() })
            addView(voice, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        closedNote = text(s.listClosedNote, 15f).apply {
            setPadding(dp(20), dp(14), dp(20), dp(14))
            alpha = 0.7f
        }

        val listView = ListView(this).apply {
            adapter = this@ListActivity.adapter
            divider = null
            setPadding(dp(8), 0, dp(8), dp(8))
            clipToPadding = false
        }
        emptyView = text(s.emptyList, 16f).apply {
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(48), dp(32), dp(32))
            alpha = 0.6f
        }
        val content = FrameLayout(this).apply {
            addView(listView)
            addView(emptyView)
        }
        root.addView(top)
        root.addView(closedNote)
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        data = Store.load(this)
        refresh()
    }

    private fun refresh() {
        list = data.list(intent.getLongExtra(EXTRA_ID, -1L))
        val l = list ?: return finish()
        title = if (l.closed) "${l.name} (${s.closed})" else l.name
        top.visibility = if (l.closed) View.GONE else View.VISIBLE
        closedNote.visibility = if (l.closed) View.VISIBLE else View.GONE
        rows = data.grouped(l).flatMap { (cat, items) ->
            listOf(Row.Header(cat?.name ?: s.otherCategory, items.count { !it.done })) + items.map { Row.Entry(it) }
        }
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        invalidateOptionsMenu()
    }

    private fun save() {
        Store.save(this, data)
        refresh()
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
            when (val r = rows[position]) {
                is Row.Header -> LinearLayout(this@ListActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dp(12), dp(20), dp(12), dp(6))
                    addView(text(r.name, 19f, bold = true).apply { setTextColor(accent()) },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(text(r.count.toString(), 15f).apply { alpha = 0.6f })
                }
                is Row.Entry -> entry(r.item)
            }
    }

    private fun entry(item: Item): View {
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
            setOnLongClickListener { itemMenu(item); true }
        }
        // One tap to put the item under another category.
        val move = text("⇄", 22f).apply {
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setTextColor(accent())
            contentDescription = s.moveToCategory
            setOnClickListener { changeCategory(item) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
            addView(check, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(move)
        }
    }

    private fun changeCategory(item: Item) {
        pickCategory("${s.moveToCategory} (${item.text})", data, exclude = data.category(item.categoryId)) { c ->
            item.categoryId = c.id
            save()
            toast(s.movedTo(item.text, c.name))
        }
    }

    private fun moveToList(item: Item) {
        val from = list ?: return
        pickList(data, exclude = from) { to ->
            Store.moveItem(item, from, to)
            save()
            toast(s.movedTo(item.text, to.name))
        }
    }

    private fun itemMenu(item: Item) {
        val canMove = data.openLists.any { it !== list }
        val options = listOfNotNull(s.moveToCategory, if (canMove) s.moveToList else null, s.rename, s.delete)
        AlertDialog.Builder(this)
            .setTitle(item.text)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    s.moveToCategory -> changeCategory(item)
                    s.moveToList -> moveToList(item)
                    s.rename -> prompt(s.rename, item.text) { item.text = it; save() }
                    else -> { list?.items?.remove(item); save() }
                }
            }
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val l = list ?: return false
        menu.add(0, 1, 0, s.clearChecked)
        menu.add(0, 2, 1, if (l.closed) s.reopenList else s.closeList)
        menu.add(0, 3, 2, s.rename)
        menu.add(0, 4, 3, s.deleteList)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val l = list ?: return false
        when (item.itemId) {
            1 -> { l.items.removeAll { it.done }; save() }
            2 -> { l.closed = !l.closed; save() }
            3 -> prompt(s.rename, l.name) { l.name = it; save() }
            4 -> AlertDialog.Builder(this)
                .setMessage(s.confirmDelete(l.name))
                .setPositiveButton(s.delete) { _, _ ->
                    data.lists.remove(l)
                    Store.save(this, data)
                    finish()
                }
                .setNegativeButton(s.cancel, null)
                .show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
