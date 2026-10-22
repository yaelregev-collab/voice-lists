package app.voicelists

import java.util.Locale

/** Text comparison for spoken input in Hebrew and English. */
object Matcher {
    private val ADD = setOf("add", "add it", "ok add", "okay add", "please add", "add that", "אד",
        "הוסף", "הוסיפי", "הוסיפו", "תוסיף", "תוסיפי", "תוסיפו", "להוסיף", "הוספה", "תוסיף את זה", "תוסיפי את זה",
        // How the recognizer often mishears "הוסף"
        "יוסף", "josef", "joseph", "yosef", "hosef", "הוסיף")
    private val YES = setOf("yes", "yeah", "yep", "sure", "ok", "okay", "create", "create it", "yes please",
        "כן", "בטח", "אישור", "צור", "תיצור", "תיצרי", "כן תיצור", "כן תיצרי", "בסדר", "יס")
    private val AGAIN = setOf("again", "try again", "retry", "no", "repeat", "nope",
        "שוב", "עוד פעם", "נסה שוב", "נסי שוב", "לא", "מחדש")
    private val CANCEL = setOf("cancel", "stop", "never mind", "nevermind", "quit", "exit",
        "ביטול", "בטל", "בטלי", "עצור", "עצרי", "לא משנה", "צא")
    // Picks the list or category just said. Written the way normalize() leaves them.
    private val SELECT = setOf("select", "choose", "pick", "select it", "choose it", "select this", "choose this",
        "בחר", "בחרי", "בחרו", "תבחר", "תבחרי", "תבחרו", "בחירה", "בחר את זה",
        // How the recognizer sometimes hears "בחר"
        "בכר")
    // Ends adding several items in a row.
    private val DONE = setOf("done", "finish", "finished", "i m done", "im done", "that s it", "thats it", "that s all",
        "thats all", "end", "סיום", "סיימתי", "סיימנו", "זהו", "זה הכל", "זהו זה", "די", "מספיק", "לסיים", "סיים",
        "תסיים", "תסיימי", "גמרתי", "גמרנו")

    private val NEW_CATEGORY = listOf("new category", "new list", "create category", "create a category",
        "add category", "add a category", "make category",
        "קטגוריה חדשה", "רשימה חדשה", "צור קטגוריה", "תיצור קטגוריה", "תיצרי קטגוריה", "הוסף קטגוריה",
        "צור רשימה", "קטגוריה חדש")

    fun normalize(s: String): String =
        s.lowercase(Locale.ROOT)
            .replace(Regex("[\\p{Punct}״׳]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun isAdd(s: String) = normalize(s) in ADD
    fun isYes(s: String) = normalize(s) in YES
    fun isAgain(s: String) = normalize(s) in AGAIN
    fun isCancel(s: String) = normalize(s) in CANCEL
    fun isDone(s: String) = normalize(s) in DONE

    /** "select" / "בחר" on its own; "add" is accepted too. */
    fun isChoice(s: String) = normalize(s) in SELECT || isAdd(s)

    /** "milk add" -> "milk". Returns null when the phrase doesn't end with an add word. */
    fun stripTrailingAdd(s: String): String? = stripTrailing(s, ADD)

    /** "add milk" -> "milk". Returns null when the phrase doesn't start with an add word. */
    fun stripLeadingAdd(s: String): String? = stripLeading(s, ADD)

    /** "Italy 2026 select" -> "Italy 2026" ("add" works too). Returns null without such a word. */
    fun stripTrailingChoice(s: String): String? = stripTrailing(s, SELECT) ?: stripTrailingAdd(s)

    fun stripChoice(s: String): String? = stripTrailingChoice(s) ?: stripLeading(s, SELECT)

    private fun stripTrailing(s: String, words: Set<String>): String? {
        val parts = normalize(s).split(" ")
        for (n in 3 downTo 1) {
            if (parts.size > n && parts.takeLast(n).joinToString(" ") in words) {
                return s.trim().split(Regex("\\s+")).dropLast(n).joinToString(" ").trim().trimEnd('.', ',', '!', '?').ifEmpty { null }
            }
        }
        return null
    }

    private fun stripLeading(s: String, words: Set<String>): String? {
        val parts = normalize(s).split(" ")
        for (n in 3 downTo 1) {
            if (parts.size > n && parts.take(n).joinToString(" ") in words) {
                return s.trim().split(Regex("\\s+")).drop(n).joinToString(" ").trim().trimEnd('.', ',', '!', '?').ifEmpty { null }
            }
        }
        return null
    }

    /**
     * "new category pharmacy" -> "pharmacy", "קטגוריה חדשה" -> "".
     * Returns null when the phrase isn't a request for a new category.
     */
    fun stripNewCategory(s: String): String? {
        val n = normalize(s)
        for (p in NEW_CATEGORY) {
            if (n == p) return ""
            if (n.startsWith("$p ")) {
                val rest = s.trim().split(Regex("\\s+")).drop(p.split(" ").size).joinToString(" ")
                return (stripTrailingAdd(rest) ?: rest).trim().trimEnd('.', ',', '!', '?')
            }
        }
        return null
    }

    /** The phrase without a leading or trailing add word, or null when it has none. */
    fun stripAdd(s: String): String? = stripTrailingAdd(s) ?: stripLeadingAdd(s)

    /** Forms of a phrase to compare: as is, and without the Hebrew prefixes ה / ו / ל. */
    private fun forms(s: String): Set<String> {
        val n = normalize(s)
        val out = mutableSetOf(n)
        val stripped = n.split(" ").joinToString(" ") { w ->
            if (w.length > 2 && (w[0] == 'ה' || w[0] == 'ו' || w[0] == 'ל')) w.substring(1) else w
        }
        out.add(stripped)
        // English plural "s"
        if (n.length > 3 && n.endsWith("s")) out.add(n.dropLast(1))
        return out
    }

    fun sameText(a: String, b: String): Boolean = forms(a).intersect(forms(b)).isNotEmpty()

    fun findCategory(categories: List<Category>, spoken: String): Category? = findNamed(categories, spoken) { it.name }

    fun findList(lists: List<ItemList>, spoken: String): ItemList? = findNamed(lists, spoken) { it.name } ?: run {
        // "Italy" for "Italy 2026": the one list whose name contains what was said.
        val n = normalize(spoken)
        if (n.length < 3) null else lists.singleOrNull { normalize(it.name).contains(n) }
    }

    /** Best match for a spoken name, allowing small recognition mistakes. */
    private fun <T> findNamed(options: List<T>, spoken: String, name: (T) -> String): T? {
        options.firstOrNull { sameText(name(it), spoken) }?.let { return it }
        var best: T? = null
        var bestDist = Int.MAX_VALUE
        for (c in options) {
            for (a in forms(name(c))) for (b in forms(spoken)) {
                val d = distance(a, b)
                val allowed = when {
                    maxOf(a.length, b.length) <= 3 -> 0
                    maxOf(a.length, b.length) <= 6 -> 1
                    else -> 2
                }
                if (d <= allowed && d < bestDist) {
                    best = c
                    bestDist = d
                }
            }
        }
        return best
    }

    private fun distance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
