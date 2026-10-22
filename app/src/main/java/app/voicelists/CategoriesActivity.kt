package app.voicelists

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/**
 * The shared categories. Items point at their category, so a rename shows in every list at once;
 * deleting a category moves its items to another one (or deletes them, if asked).
 */
class CategoriesActivity : Activity() {

    private lateinit var s: Str
    private var data = Data(mutableListOf(), mutableListOf())
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = Lang.strings(this)
        title = s.categories
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        Lang.applyDirection(root, s.he)

        val listView = ListView(this).apply {
            adapter = this@CategoriesActivity.adapter
            divider = null
            setPadding(dp(8), dp(8), dp(8), dp(8))
            clipToPadding = false
        }
        emptyView = text(s.noCategories, 16f).apply {
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(48), dp(32), dp(32))
            alpha = 0.6f
        }
        val content = FrameLayout(this).apply {
            addView(listView)
            addView(emptyView)
        }
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(20))
            addView(pill(s.newCategoryButton) { newCategory() })
        }
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bar)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        data = Store.load(this)
        refresh()
    }

    private fun refresh() {
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (data.categories.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun save() {
        Store.save(this, data)
        refresh()
    }

    /** Items in this category, in all lists (open or closed). */
    private fun itemsIn(c: Category): List<Item> = data.lists.flatMap { l -> l.items.filter { it.categoryId == c.id } }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = data.categories.size
        override fun getItem(position: Int) = data.categories[position]
        override fun getItemId(position: Int) = data.categories[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val c = data.categories[position]
            return LinearLayout(this@CategoriesActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(6), dp(4), dp(6))
                val names = LinearLayout(this@CategoriesActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text(c.name, 18f))
                    addView(text(s.itemCount(itemsIn(c).size), 14f).apply { alpha = 0.6f })
                }
                addView(names, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(icon("✏️", s.rename) { rename(c) })
                addView(icon("🗑️", s.delete) { delete(c) })
                setOnClickListener { rename(c) }
            }
        }
    }

    private fun icon(glyph: String, label: String, onClick: () -> Unit) = text(glyph, 20f).apply {
        setPadding(dp(14), dp(12), dp(14), dp(12))
        contentDescription = label
        setOnClickListener { onClick() }
    }

    private fun newCategory() {
        prompt(s.newCategory, "") { name ->
            if (data.categories.any { Matcher.sameText(it.name, name) }) toast(s.categoryExists(name))
            else {
                data.categories.add(Category(Store.newId(), name))
                save()
            }
        }
    }

    private fun rename(c: Category) {
        prompt(s.rename, c.name) { name ->
            val other = data.categories.firstOrNull { it !== c && Matcher.sameText(it.name, name) }
            if (other == null) {
                c.name = name
                save()
            } else AlertDialog.Builder(this)
                .setMessage(s.mergeConfirm(other.name))
                .setPositiveButton(s.merge) { _, _ -> moveAndDelete(c, other) }
                .setNegativeButton(s.cancel, null)
                .show()
        }
    }

    private fun delete(c: Category) {
        val items = itemsIn(c)
        if (items.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage(s.confirmDeleteCategory(c.name))
                .setPositiveButton(s.delete) { _, _ ->
                    data.categories.remove(c)
                    save()
                }
                .setNegativeButton(s.cancel, null)
                .show()
            return
        }
        val others = data.categories.filter { it !== c }
        val labels = others.map { it.name } + s.newCategoryButton
        AlertDialog.Builder(this)
            .setTitle(s.deleteMoveTitle(c.name, items.size))
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < others.size) moveAndDelete(c, others[which])
                else prompt(s.newCategory, "") { name ->
                    val target = data.categoryNamed(name)
                    if (target !== c) moveAndDelete(c, target)
                }
            }
            .setNeutralButton(s.deleteItemsToo) { _, _ -> deleteWithItems(c, items.size) }
            .setNegativeButton(s.cancel, null)
            .show()
    }

    private fun moveAndDelete(from: Category, to: Category) {
        val items = itemsIn(from)
        items.forEach { it.categoryId = to.id }
        data.categories.remove(from)
        save()
        if (items.isNotEmpty()) toast(s.movedItems(items.size, to.name))
    }

    private fun deleteWithItems(c: Category, count: Int) {
        AlertDialog.Builder(this)
            .setMessage(s.confirmDeleteWithItems(c.name, count))
            .setPositiveButton(s.delete) { _, _ ->
                data.lists.forEach { l -> l.items.removeAll { it.categoryId == c.id } }
                data.categories.remove(c)
                save()
            }
            .setNegativeButton(s.cancel, null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
