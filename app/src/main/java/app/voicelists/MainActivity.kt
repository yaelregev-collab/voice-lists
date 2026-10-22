package app.voicelists

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** The lists (categories), with item counts. */
class MainActivity : Activity() {

    private lateinit var s: Str
    private var categories = mutableListOf<Category>()
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
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (categories.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildUi() {
        s = Lang.strings(this)
        title = s.appName
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Lang.applyDirection(root, s.he)

        listView = ListView(this).apply {
            adapter = this@MainActivity.adapter
            divider = null
            setPadding(dp(8), dp(8), dp(8), dp(8))
            clipToPadding = false
            setOnItemClickListener { _, _, pos, _ ->
                startActivity(Intent(this@MainActivity, CategoryActivity::class.java)
                    .putExtra(CategoryActivity.EXTRA_ID, categories[pos].id))
            }
            setOnItemLongClickListener { _, _, pos, _ -> editCategory(categories[pos]); true }
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

        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bar)
        setContentView(root)
        invalidateOptionsMenu()
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = categories.size
        override fun getItem(position: Int) = categories[position]
        override fun getItemId(position: Int) = categories[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val c = categories[position]
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(18), dp(20), dp(18))
            }
            row.addView(text(c.name, 18f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(text(c.openCount.toString(), 16f).apply { alpha = 0.6f })
            return row
        }
    }

    private fun newCategory() {
        prompt(s.newCategory, "") { name ->
            categories.add(Category(Store.newId(), name))
            Store.save(this, categories)
            reload()
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
