# Voice Lists / רשימות קוליות

An Android app (Pixel 7a, Android 12+) for lists sorted into categories, with a home screen
widget for adding items by voice, in Hebrew or English.

## Voice flow
1. Tap the **Add item** widget. A small window opens and starts listening.
2. Say the item, then say **"add"** (or say it all at once: "milk add" / "חלב הוסף").
3. If the item is already on a list, the app says so and closes.
4. The app asks **"Which category?"** and listens again.
5. If the category exists (small mishearings are tolerated), the item is saved and the window closes.
   Saying a new name followed by **"add"** / **"הוסף"** creates that category straight away,
   and so does **"new category …"** / **"קטגוריה חדשה …"** or the **+ New category** button.
6. If not, it reads the category back and asks whether to create it. Say **"yes"** / **"כן"** or **"add"** / **"הוסף"** to create it,
   or **"again"** / **"שוב"** to say the category again. **"cancel"** / **"ביטול"** stops at any point.

Every step also has buttons, and existing categories show as tap-able chips.
The window acts on "add" / "הוסף" as soon as it's heard, and also accepts it first ("add milk").

## Adding many items in a row
The second widget (**Add many items** / **הוספה ברצף**, a list-with-plus icon) is for filling one category of one list:
1. It asks **which list**: say the name, then **"select"** / **"בחר"** ("Italy 2026 select"). Part of the name is enough
   when only one open list contains it. An unknown name offers to create that list.
2. It asks **which category**: say it, then **"select"** / **"בחר"**. A category that doesn't exist yet is created.
3. Then say items one after another, each followed by **"add"** / **"הוסף"** ("3 shirts add", "4 pants add").
   Every item goes into that category of that list. Say **"done"** / **"סיום"** to finish.

In the app the same thing starts from the list-with-plus button at the top of the main screen or of a list,
and from the button on each category heading inside a list (that one goes straight to step 3).

## In the app
- **Lists → categories → items.** The main screen shows your lists (open ones first, closed ones below).
  Categories are one shared set used by every list (the **Categories** button adds, renames and deletes them).
- A list can be closed and reopened (long-press it, or from its menu). Only open lists take new items.
- Adding an item asks for the category, then for the list, but only when more than one list is open.
  With no open list, a new one is started.
- Inside a list, items are grouped by category. Tap **⇄** to change an item's category; long-press for
  move to another list, rename, delete.
- Dictating with the keyboard mic and ending with "add" / "הוסף" adds the item straight away.
- **Categories** screen: every category with its item count. Renaming changes it for every item in every list;
  deleting asks where to move its items (or deletes them too, if chosen). Renaming to an existing name merges the two.

## Keeping your lists
- Installing a new version over the installed one keeps everything. Don't uninstall first: uninstalling erases the app's data.
  Android refuses to install an older version over a newer one, so always open the file with the highest version number.
- **Automatic backup** (banner on the main screen, or Menu → Automatic backup): pick a file once (Downloads by default)
  and every change is written to it. After a reinstall, the empty main screen offers **Restore from backup**.

## Language
Menu → Language: **Automatic** follows the keyboard's current language (then the phone's language),
or pick Hebrew or English. For spoken questions in Hebrew, make sure the Hebrew voice is installed:
Settings → System → Languages → Text-to-speech output → Speech Services by Google → Install voice data.

## Building
- GitHub: every push to `main` builds `VoiceLists-<version>.apk` and publishes it as release `v<version>`.
- Android Studio: open the folder and run on the phone.
- Command line: `./gradlew assembleRelease` (needs the Android SDK with platform 34).

The signing key in `keystore/` is a personal key so new builds install over old ones. Keep the repo private.

## Installing on the phone
Open the APK on the phone, allow "Install unknown apps" for the browser/Files when asked, and install.
Then long-press the home screen → Widgets → Voice Lists to add the widget.
