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

/** One list: tick items off, add by typing or by voice, remove. */
class CategoryActivity : Activity() {

    companion object {
        const val EXTRA_ID = "id"
    }

    private lateinit var s: Str
    private var categories = mutableListOf<Category>()
    private var category: Category? = null
    private var shown = listOf<Item>()
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = Lang.strings(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Lang.applyDirection(root, s.he)

        val input = EditText(this).apply {
            hint = s.newItem
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        fun submit() {
            val v = input.text.toString().trim()
            val c = category ?: return
            if (v.isEmpty()) return
            if (Store.findOpenItem(categories, v) == null) {
                Store.addItem(c, v)
                Store.save(this, categories)
            }
            input.setText("")
            refresh()
        }
        input.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) { submit(); true } else false
        }
        val voice = ImageButton(this).apply {
            setImageResource(R.drawable.ic_mic)
            setColorFilter(accent())
            background = null
            contentDescription = s.voiceAdd
            setOnClickListener {
                category?.let {
                    startActivity(Intent(this@CategoryActivity, VoiceActivity::class.java)
                        .putExtra(VoiceActivity.EXTRA_CATEGORY, it.id))
                }
            }
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
            adapter = this@CategoryActivity.adapter
            divider = null
            setOnItemLongClickListener { _, _, pos, _ -> itemMenu(shown[pos]); true }
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
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        categories = Store.load(this)
        category = categories.firstOrNull { it.id == intent.getLongExtra(EXTRA_ID, -1L) }
        val c = category ?: return finish()
        title = c.name
        // Open items first, checked items at the bottom.
        shown = c.items.filter { !it.done } + c.items.filter { it.done }
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = shown[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = shown[position]
            return CheckBox(this@CategoryActivity).apply {
                text = item.text
                textSize = 18f
                isChecked = item.done
                setPadding(dp(12), dp(14), dp(12), dp(14))
                paintFlags = if (item.done) paintFlags or Paint.STRIKE_THRU_TEXT_FLAG else paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                alpha = if (item.done) 0.5f else 1f
                setOnClickListener {
                    item.done = !item.done
                    Store.save(this@CategoryActivity, categories)
                    refresh()
                }
                setOnLongClickListener { itemMenu(item); true }
            }
        }
    }

    private fun itemMenu(item: Item) {
        AlertDialog.Builder(this)
            .setTitle(item.text)
            .setItems(arrayOf(s.rename, s.delete)) { _, which ->
                if (which == 0) prompt(s.rename, item.text) {
                    item.text = it
                    Store.save(this, categories)
                    refresh()
                } else {
                    category?.items?.remove(item)
                    Store.save(this, categories)
                    refresh()
                }
            }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, s.clearChecked)
        menu.add(0, 2, 1, s.rename)
        menu.add(0, 3, 2, s.deleteList)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val c = category ?: return false
        when (item.itemId) {
            1 -> {
                c.items.removeAll { it.done }
                Store.save(this, categories)
                refresh()
            }
            2 -> prompt(s.rename, c.name) {
                c.name = it
                Store.save(this, categories)
                refresh()
            }
            3 -> AlertDialog.Builder(this)
                .setMessage(s.confirmDelete(c.name))
                .setPositiveButton(s.delete) { _, _ ->
                    categories.remove(c)
                    Store.save(this, categories)
                    finish()
                }
                .setNegativeButton(s.cancel, null)
                .show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
