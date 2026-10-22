package app.voicelists

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Item(val id: Long, var text: String, var done: Boolean = false)

class Category(val id: Long, var name: String, val items: MutableList<Item> = mutableListOf()) {
    val openCount: Int get() = items.count { !it.done }
}

/** All lists live in one JSON document in SharedPreferences. Small, simple, and survives reinstalls via backup. */
object Store {
    private const val PREFS = "lists"
    private const val KEY = "categories"

    fun load(ctx: Context): MutableList<Category> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return mutableListOf()
        val arr = JSONArray(raw)
        val out = mutableListOf<Category>()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val items = c.getJSONArray("items")
            val cat = Category(c.getLong("id"), c.getString("name"))
            for (j in 0 until items.length()) {
                val it = items.getJSONObject(j)
                cat.items.add(Item(it.getLong("id"), it.getString("text"), it.optBoolean("done")))
            }
            out.add(cat)
        }
        return out
    }

    fun save(ctx: Context, categories: List<Category>) {
        val arr = JSONArray()
        for (c in categories) {
            val items = JSONArray()
            for (it in c.items) {
                items.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done))
            }
            arr.put(JSONObject().put("id", c.id).put("name", c.name).put("items", items))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
        AddItemWidget.refreshAll(ctx)
    }

    fun newId(): Long = System.nanoTime()

    /** An item that is still open (not checked off) with the same name, in any category. */
    fun findOpenItem(categories: List<Category>, text: String): Pair<Category, Item>? {
        for (c in categories) {
            val item = c.items.firstOrNull { !it.done && Matcher.sameText(it.text, text) }
            if (item != null) return c to item
        }
        return null
    }

    /** Adds the item, dropping an old checked-off copy of it from the same category. */
    fun addItem(category: Category, text: String) {
        category.items.removeAll { it.done && Matcher.sameText(it.text, text) }
        category.items.add(Item(newId(), text.trim()))
    }
}
