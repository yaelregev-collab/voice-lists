package app.voicelists

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import java.util.Locale

/** Which language the app speaks, listens and shows text in. */
object Lang {
    private const val PREFS = "settings"
    private const val KEY = "language"
    const val AUTO = "auto"
    const val HEBREW = "he"
    const val ENGLISH = "en"

    fun setting(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, AUTO) ?: AUTO

    fun setSetting(ctx: Context, value: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
    }

    /** True for Hebrew. "Auto" follows the keyboard's current language, then the phone's language. */
    fun isHebrew(ctx: Context): Boolean = when (setting(ctx)) {
        HEBREW -> true
        ENGLISH -> false
        else -> keyboardLanguage(ctx)?.let { isHebrewTag(it) } ?: isHebrewTag(Locale.getDefault().language)
    }

    fun strings(ctx: Context) = Str(isHebrew(ctx))

    private fun keyboardLanguage(ctx: Context): String? {
        val imm = ctx.getSystemService(InputMethodManager::class.java) ?: return null
        val tag = imm.currentInputMethodSubtype?.languageTag
        return if (tag.isNullOrEmpty()) null else tag
    }

    private fun isHebrewTag(tag: String) = tag.startsWith("he") || tag.startsWith("iw")

    fun applyDirection(view: View, hebrew: Boolean) {
        view.layoutDirection = if (hebrew) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    }
}

/** All user-facing text, in Hebrew and English. */
class Str(val he: Boolean) {
    val locale: Locale = if (he) Locale.forLanguageTag("he-IL") else Locale.US
    val speechTag: String = if (he) "he-IL" else "en-US"

    private fun t(en: String, hebrew: String) = if (he) hebrew else en

    val appName = t("Voice Lists", "רשימות קוליות")
    val newList = t("New list", "רשימה חדשה")
    val newCategory = t("New category", "קטגוריה חדשה")
    val categories = t("Categories", "קטגוריות")
    val newItem = t("New item", "פריט חדש")
    val add = t("Add", "הוסף")
    val cancel = t("Cancel", "ביטול")
    val save = t("Save", "שמור")
    val delete = t("Delete", "מחק")
    val rename = t("Rename", "שנה שם")
    val clearChecked = t("Remove checked items", "מחק פריטים מסומנים")
    val deleteList = t("Delete list", "מחק רשימה")
    val closeList = t("Close list", "סגור רשימה")
    val reopenList = t("Reopen list", "פתח רשימה מחדש")
    val closed = t("Closed", "סגורה")
    val closedLists = t("Closed lists", "רשימות סגורות")
    val listClosedNote = t("This list is closed. Reopen it to add items.", "הרשימה סגורה. פתחו אותה מחדש כדי להוסיף פריטים.")
    val language = t("Language", "שפה")
    val langAuto = t("Automatic (keyboard)", "אוטומטי (לפי המקלדת)")
    val langHebrew = "עברית"
    val langEnglish = "English"
    val voiceAdd = t("Add by voice", "הוספה בקול")
    val empty = t("No lists yet. Tap + to create one, or add an item by voice.",
        "אין עדיין רשימות. הקישו + כדי ליצור רשימה, או הוסיפו פריט בקול.")
    val emptyList = t("This list is empty.", "הרשימה ריקה.")
    val noCategories = t("No categories yet.", "אין עדיין קטגוריות.")
    val otherCategory = t("Other", "אחר")
    val firstListName = t("My list", "הרשימה שלי")
    fun defaultListName(date: String) = t("List $date", "רשימה $date")
    val widgetHint = t("Tip: long-press your home screen, choose Widgets, and add \"Voice Lists\" for one-tap adding.",
        "טיפ: לחיצה ארוכה על מסך הבית, בחרו ווידג'טים והוסיפו את \"רשימות קוליות\" להוספה בלחיצה אחת.")
    fun confirmDelete(name: String) = t("Delete \"$name\" and all its items?", "למחוק את \"$name\" ואת כל הפריטים שבה?")
    fun confirmDeleteCategory(name: String) = t("Delete the category \"$name\"?", "למחוק את הקטגוריה \"$name\"?")
    fun categoryInUse(name: String, count: Int) = t("\"$name\" is used by $count items. Move them to another category first.",
        "בקטגוריה \"$name\" יש $count פריטים. העבירו אותם קודם לקטגוריה אחרת.")
    fun itemCount(n: Int) = t("$n items", "$n פריטים")

    // Voice flow
    val sayItem = t("What would you like to add?", "מה להוסיף?")
    val sayItemHint = t("Say the item, then say \"add\".", "אמרו את הפריט, ואז \"הוסף\".")
    fun heardItem(item: String) = t("\"$item\". Say \"add\" to continue.", "\"$item\". אמרו \"הוסף\" כדי להמשיך.")
    fun alreadyExists(item: String, list: String) = t("$item is already in $list.", "$item כבר נמצא ב$list.")
    val askCategory = t("Which category?", "לאיזו קטגוריה?")
    val askList = t("Which list?", "לאיזו רשימה?")
    val listHint = t("Say the name of the list.", "אמרו את שם הרשימה.")
    fun added(item: String, cat: String, list: String) = t("Added $item to $cat in $list.", "$item נוסף ל$cat ב$list.")
    fun unknownCategory(cat: String, item: String) = t(
        "I didn't find the category $cat. Should I create it and add $item? Say yes or add, or say again.",
        "לא מצאתי את הקטגוריה $cat. ליצור אותה ולהוסיף את $item? אמרו כן או הוסף, או שוב.")
    val categoryHint = t("Say a category. A new name followed by \"add\" creates it.",
        "אמרו קטגוריה. שם חדש ואחריו \"הוסף\" ייצור אותה.")
    val sayNewCategory = t("Say the new category name.", "אמרו את שם הקטגוריה החדשה.")
    val newCategoryButton = t("+ New category", "+ קטגוריה חדשה")
    val type = t("Type", "הקלדה")
    val listening = t("Listening…", "מקשיב…")
    val notHeard = t("I didn't catch that. Tap the mic to try again.", "לא שמעתי. הקישו על המיקרופון כדי לנסות שוב.")
    val micPermission = t("Voice Lists needs the microphone to hear you.", "האפליקציה צריכה גישה למיקרופון כדי לשמוע אתכם.")
    val noRecognizer = t("Speech recognition isn't available on this phone.", "זיהוי דיבור לא זמין בטלפון הזה.")
    val yes = t("Yes, create", "כן, צור")
    val again = t("Say again", "שוב")
    val mic = t("Listen", "האזן")
    val category = t("Category", "קטגוריה")
    val widgetLabel = t("Add item", "הוסף פריט")

    // Screens
    val moveToCategory = t("Change category…", "העבר לקטגוריה…")
    val moveToList = t("Move to another list…", "העבר לרשימה אחרת…")
    fun movedTo(item: String, target: String) = t("Moved $item to $target", "$item הועבר ל$target")
    val newItemHint = t("New item (dictate, then say \"add\")", "פריט חדש (הכתיבו ואמרו \"הוסף\")")
}
