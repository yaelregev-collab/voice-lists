package app.voicelists

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Item(val id: Long, var text: String, var categoryId: Long, var done: Boolean = false)

/** Categories are one shared set, used by every list. */
class Category(val id: Long, var name: String)

/** A list (shopping trip, packing list…). Only open lists take new items. */
class ItemList(val id: Long, var name: String, var closed: Boolean = false, val items: MutableList<Item> = mutableListOf()) {
    val openCount: Int get() = items.count { !it.done }
}

class Data(val categories: MutableList<Category>, val lists: MutableList<ItemList>) {
    val openLists: List<ItemList> get() = lists.filter { !it.closed }

    fun category(id: Long): Category? = categories.firstOrNull { it.id == id }
    fun list(id: Long): ItemList? = lists.firstOrNull { it.id == id }

    /** The category with this name, created if there isn't one yet. */
    fun categoryNamed(name: String): Category =
        categories.firstOrNull { Matcher.sameText(it.name, name) }
            ?: Category(Store.newId(), name.trim()).also { categories.add(it) }

    /** Open lists, or a new one when every list is closed. */
    fun openListsOrNew(s: Str): List<ItemList> = openLists.ifEmpty {
        listOf(ItemList(Store.newId(), s.defaultListName(SimpleDateFormat("d.M", Locale.ROOT).format(Date())))
            .also { lists.add(it) })
    }

    /** A list's items by category, in the shared category order; open items before checked ones. */
    fun grouped(list: ItemList): List<Pair<Category?, List<Item>>> {
        val sorted = list.items.filter { !it.done } + list.items.filter { it.done }
        val byCat = sorted.groupBy { it.categoryId }
        val known = categories.filter { it.id in byCat }.map { it to byCat.getValue(it.id) }
        val orphans = sorted.filter { category(it.categoryId) == null }
        return if (orphans.isEmpty()) known else known + (null to orphans)
    }
}

/** Everything lives in one JSON document in SharedPreferences. Small, simple, and survives reinstalls via backup. */
object Store {
    private const val PREFS = "lists"
    private const val KEY = "data_v2"
    private const val OLD_KEY = "categories"

    fun load(ctx: Context): Data {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return migrate(ctx, prefs.getString(OLD_KEY, null))
        return parse(raw)
    }

    /** Reads the saved document, which is also the backup file's format. Throws on anything else. */
    fun parse(raw: String): Data {
        val o = JSONObject(raw)
        val categories = mutableListOf<Category>()
        val cats = o.getJSONArray("categories")
        for (i in 0 until cats.length()) {
            val c = cats.getJSONObject(i)
            categories.add(Category(c.getLong("id"), c.getString("name")))
        }
        val lists = mutableListOf<ItemList>()
        val ls = o.getJSONArray("lists")
        for (i in 0 until ls.length()) {
            val l = ls.getJSONObject(i)
            val list = ItemList(l.getLong("id"), l.getString("name"), l.optBoolean("closed"))
            val items = l.getJSONArray("items")
            for (j in 0 until items.length()) {
                val it = items.getJSONObject(j)
                list.items.add(Item(it.getLong("id"), it.getString("text"), it.getLong("category"), it.optBoolean("done")))
            }
            lists.add(list)
        }
        return Data(categories, lists)
    }

    /** Version 1.3 and earlier had categories holding items directly: they become one list. */
    private fun migrate(ctx: Context, old: String?): Data {
        val data = Data(mutableListOf(), mutableListOf())
        if (old == null) return data
        val list = ItemList(newId(), Lang.strings(ctx).firstListName)
        val arr = JSONArray(old)
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val cat = Category(c.getLong("id"), c.getString("name"))
            data.categories.add(cat)
            val items = c.getJSONArray("items")
            for (j in 0 until items.length()) {
                val it = items.getJSONObject(j)
                list.items.add(Item(it.getLong("id"), it.getString("text"), cat.id, it.optBoolean("done")))
            }
        }
        data.lists.add(list)
        save(ctx, data)
        return data
    }

    fun save(ctx: Context, data: Data) {
        val json = toJson(data)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply()
        Backup.write(ctx, json)
        refreshWidgets(ctx)
    }

    fun toJson(data: Data): String {
        val cats = JSONArray()
        for (c in data.categories) cats.put(JSONObject().put("id", c.id).put("name", c.name))
        val lists = JSONArray()
        for (l in data.lists) {
            val items = JSONArray()
            for (it in l.items) {
                items.put(JSONObject().put("id", it.id).put("text", it.text).put("category", it.categoryId).put("done", it.done))
            }
            lists.put(JSONObject().put("id", l.id).put("name", l.name).put("closed", l.closed).put("items", items))
        }
        return JSONObject().put("app", "voicelists").put("format", 2)
            .put("categories", cats).put("lists", lists).toString(1)
    }

    private var lastId = 0L

    fun newId(): Long {
        lastId = maxOf(lastId + 1, System.currentTimeMillis())
        return lastId
    }

    /** An item in this list that is still open (not checked off) with the same name. */
    fun findOpenItem(list: ItemList, text: String): Item? =
        list.items.firstOrNull { !it.done && Matcher.sameText(it.text, text) }

    /** Adds the item, dropping an old checked-off copy of it from the same list. */
    fun addItem(list: ItemList, category: Category, text: String) {
        list.items.removeAll { it.done && Matcher.sameText(it.text, text) }
        list.items.add(Item(newId(), text.trim(), category.id))
    }

    /** Moves an item to another list (dropping a checked-off copy already there). */
    fun moveItem(item: Item, from: ItemList, to: ItemList) {
        if (from === to) return
        from.items.remove(item)
        to.items.removeAll { it.done && Matcher.sameText(it.text, item.text) }
        to.items.add(item)
    }
}
