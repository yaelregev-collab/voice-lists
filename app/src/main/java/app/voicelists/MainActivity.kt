package app.voicelists

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import android.widget.Toast

/** The lists, open ones first. Items added here ask for a category, then a list. */
class MainActivity : Activity() {

    companion object {
        private const val REQ_BACKUP_FILE = 10
        private const val REQ_RESTORE_FILE = 11
        private const val MENU_BACKUP = 1
        private const val MENU_RESTORE = 2
        private const val MENU_LANGUAGE = 3
        private const val MENU_VERSION = 4
    }

    private sealed class Row {
        class Open(val list: ItemList) : Row()
        object ClosedHeader : Row()
        class Closed(val list: ItemList) : Row()
    }

    private lateinit var s: Str
    private var data = Data(mutableListOf(), mutableListOf())
    private var rows = listOf<Row>()
    private lateinit var emptyView: View
    private lateinit var backupBanner: View

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
        backupBanner.visibility = if (Backup.isOn(this)) View.GONE else View.VISIBLE
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
            addView(iconButton(R.drawable.ic_batch, s.batchTitle) { startAddMany() }, LinearLayout.LayoutParams(dp(48), dp(48)))
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
        // A fresh install starts here: offer to bring the lists back from a backup file.
        emptyView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(32), dp(48), dp(32), dp(32))
            addView(text(s.empty + "\n\n" + s.widgetHint, 16f).apply {
                gravity = Gravity.CENTER
                alpha = 0.7f
            })
            addView(pill(s.restoreButton, filled = false) { chooseRestoreFile() },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(24) })
        }
        backupBanner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(4), dp(16), dp(8))
            addView(text(s.backupOff, 14f).apply { alpha = 0.8f },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(12) })
            addView(pill(s.turnOnBackup) { chooseBackupFile() })
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
        bar.addView(pill(s.categories, filled = false) {
            startActivity(Intent(this, CategoriesActivity::class.java))
        }, lp)

        root.addView(top)
        root.addView(backupBanner)
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
            .setItems(arrayOf(s.print, if (l.closed) s.reopenList else s.closeList, s.rename, s.deleteList)) { _, which ->
                when (which) {
                    0 -> ListPrinter.print(this, data, l)
                    1 -> { l.closed = !l.closed; save() }
                    2 -> prompt(s.rename, l.name) { l.name = it; save() }
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_BACKUP, 0, s.backupMenu)
        menu.add(0, MENU_RESTORE, 1, s.restoreMenu)
        menu.add(0, MENU_LANGUAGE, 2, s.language)
        menu.add(0, MENU_VERSION, 3, s.version(versionName())).isEnabled = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_BACKUP -> backupSettings()
            MENU_RESTORE -> chooseRestoreFile()
            MENU_LANGUAGE -> chooseLanguage()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun versionName(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }

    private fun chooseLanguage() {
        val values = arrayOf(Lang.AUTO, Lang.HEBREW, Lang.ENGLISH)
        val labels = arrayOf(s.langAuto, s.langHebrew, s.langEnglish)
        AlertDialog.Builder(this)
            .setTitle(s.language)
            .setSingleChoiceItems(labels, values.indexOf(Lang.setting(this))) { d, which ->
                Lang.setSetting(this, values[which])
                d.dismiss()
                refreshWidgets(this)
                buildUi()
                reload()
            }
            .show()
    }

    // ---- Backup ----

    /** Lets the user pick where the backup file goes (Downloads by default). */
    private fun chooseBackupFile() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, Backup.FILE_NAME), REQ_BACKUP_FILE)
    }

    private fun chooseRestoreFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*"), REQ_RESTORE_FILE)
    }

    private fun backupSettings() {
        val uri = Backup.file(this) ?: return chooseBackupFile()
        AlertDialog.Builder(this)
            .setTitle(s.backupMenu)
            .setMessage(s.backupOn(displayName(uri)))
            .setPositiveButton(s.close, null)
            .setNeutralButton(s.changeFile) { _, _ -> chooseBackupFile() }
            .setNegativeButton(s.turnOff) { _, _ ->
                Backup.turnOff(this)
                refresh()
            }
            .show()
    }

    private fun displayName(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    } ?: Backup.FILE_NAME

    override fun onActivityResult(requestCode: Int, resultCode: Int, result: Intent?) {
        super.onActivityResult(requestCode, resultCode, result)
        val uri = result?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQ_BACKUP_FILE -> {
                if (Backup.use(this, uri)) {
                    Backup.write(this, Store.toJson(Store.load(this)))
                    toast(s.backupStarted)
                } else toast(s.backupFailed)
                refresh()
            }
            REQ_RESTORE_FILE -> restore(uri)
        }
    }

    private fun restore(uri: Uri) {
        val restored = try {
            Store.parse(Backup.read(this, uri))
        } catch (e: Exception) {
            null
        }
        if (restored == null) {
            toast(s.notBackup)
            return
        }
        fun replaceWithBackup() {
            data = restored
            // Keep backing up to the same file from now on.
            Backup.use(this, uri)
            Store.save(this, data)
            refresh()
            toast(s.restored(data.lists.size))
        }
        if (Store.load(this).lists.isEmpty()) replaceWithBackup()
        else AlertDialog.Builder(this)
            .setMessage(s.restoreConfirm(restored.lists.size))
            .setPositiveButton(s.restore) { _, _ -> replaceWithBackup() }
            .setNegativeButton(s.cancel, null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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
