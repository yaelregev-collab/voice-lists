package app.voicelists

import android.app.Activity
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Prints a list on A4 in three columns: each category with its items underneath, in the categories' order,
 * flowing from one column to the next and on to as many pages as needed.
 */
object ListPrinter {
    // The page is laid out by a WebView, which has to stay referenced until its print job is handed over.
    private var pending: WebView? = null

    fun print(activity: Activity, data: Data, list: ItemList) {
        val s = Lang.strings(activity)
        val jobName = "${s.appName} - ${list.name}"
        val web = WebView(activity)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (pending !== view) return
                pending = null
                val manager = activity.getSystemService(PrintManager::class.java) ?: return
                manager.print(jobName, view.createPrintDocumentAdapter(jobName),
                    PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
            }
        }
        pending = web
        web.loadDataWithBaseURL(null, html(data, list, s), "text/html", "UTF-8", null)
    }

    // Columns fill one after another (column-fill: auto) and continue on the next page.
    private const val CSS = """
@page { size: A4; margin: 12mm 10mm; }
html { font-family: sans-serif; color: #000; }
body { margin: 0; }
h1 { font-size: 17pt; margin: 0 0 5mm; padding-bottom: 2mm; border-bottom: 0.6mm solid #000; }
.cols { column-count: 3; column-gap: 7mm; column-fill: auto; column-rule: 0.2mm solid #bbb; }
h2 { font-size: 12.5pt; margin: 0 0 1.5mm; padding: 3mm 0 0.8mm; border-bottom: 0.3mm solid #666; break-after: avoid; }
p { font-size: 10.5pt; line-height: 1.3; margin: 0; padding: 0.7mm 0; break-inside: avoid; }
p::before { content: ""; display: inline-block; width: 2.8mm; height: 2.8mm; border: 0.3mm solid #333;
    margin-inline-end: 1.8mm; vertical-align: -0.3mm; }
p.done { color: #777; text-decoration: line-through; }
p.done::before { background: #777; border-color: #777; }
"""

    fun html(data: Data, list: ItemList, s: Str): String {
        val groups = data.grouped(list)
        val texts = listOf(list.name) + groups.flatMap { (cat, items) -> listOfNotNull(cat?.name) + items.map { it.text } }
        val rtl = s.he || texts.count { t -> t.any { it in '֐'..'׿' } } * 2 > texts.size
        val body = StringBuilder()
        for ((cat, items) in groups) {
            body.append("<h2>").append(escape(cat?.name ?: s.otherCategory)).append("</h2>")
            for (item in items) {
                body.append(if (item.done) "<p class=\"done\">" else "<p>").append(escape(item.text)).append("</p>")
            }
        }
        return "<!doctype html><html dir=\"${if (rtl) "rtl" else "ltr"}\"><head><meta charset=\"utf-8\">" +
            "<style>$CSS</style></head><body><h1>${escape(list.name)}</h1><div class=\"cols\">$body</div></body></html>"
    }

    private fun escape(t: String) =
        t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
