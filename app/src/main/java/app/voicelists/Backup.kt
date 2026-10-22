package app.voicelists

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.concurrent.Executors

/**
 * Keeps a copy of everything in a file the user picked (for example in Downloads), rewritten on every change.
 * The file outlives the app, so the lists can be restored even after the app was uninstalled.
 */
object Backup {
    private const val PREFS = "backup"
    private const val KEY_URI = "uri"
    const val FILE_NAME = "VoiceLists-backup.json"
    private const val ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    private val writer = Executors.newSingleThreadExecutor()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun file(ctx: Context): Uri? = prefs(ctx).getString(KEY_URI, null)?.let(Uri::parse)

    fun isOn(ctx: Context) = file(ctx) != null

    /** Backs up to this file from now on, keeping access to it across restarts. False if the file can't be used. */
    fun use(ctx: Context, uri: Uri): Boolean {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, ACCESS)
        } catch (e: SecurityException) {
            return false
        }
        val old = file(ctx)
        if (old != null && old != uri) release(ctx, old)
        prefs(ctx).edit().putString(KEY_URI, uri.toString()).apply()
        return true
    }

    fun turnOff(ctx: Context) {
        file(ctx)?.let { release(ctx, it) }
        prefs(ctx).edit().remove(KEY_URI).apply()
    }

    private fun release(ctx: Context, uri: Uri) {
        try {
            ctx.contentResolver.releasePersistableUriPermission(uri, ACCESS)
        } catch (e: Exception) {
            // Already gone.
        }
    }

    fun write(ctx: Context, json: String) {
        val target = file(ctx) ?: return
        val app = ctx.applicationContext
        writer.execute {
            try {
                app.contentResolver.openOutputStream(target, "wt")!!.use { it.write(json.toByteArray()) }
            } catch (e: Exception) {
                // The file was deleted or access was lost: show the backup as off, so it can be set up again.
                if (file(app) == target) prefs(app).edit().remove(KEY_URI).apply()
            }
        }
    }

    fun read(ctx: Context, uri: Uri): String =
        ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
}
