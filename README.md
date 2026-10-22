# Voice Lists / רשימות קוליות

An Android app (Pixel 7a, Android 12+) for lists sorted into categories, with a home screen
widget for adding items by voice, in Hebrew or English.

## Voice flow
1. Tap the **Add item** widget. A small window opens and starts listening.
2. Say the item, then say **"add"** (or say it all at once: "milk add" / "חלב הוסף").
3. If the item is already on a list, the app says so and closes.
4. The app asks **"Which category?"** and listens again.
5. If the category exists (small mishearings are tolerated), the item is saved and the window closes.
6. If not, it reads the category back and asks whether to create it. Say **"yes"** / **"כן"** to create it,
   or **"again"** / **"שוב"** to say the category again. **"cancel"** / **"ביטול"** stops at any point.

Every step also has buttons, and existing categories show as tap-able chips.
The window acts on "add" / "הוסף" as soon as it's heard, and also accepts it first ("add milk").

## In the app
- The main screen shows every category with its items underneath. Tap a heading to open that category,
  long-press it to rename or delete it, and use **+ New list** to create a category by hand.
- Type or dictate in the box at the top. Dictating with the keyboard mic and ending with "add" / "הוסף"
  adds the item straight away and asks for the category.
- Tap **⇄** next to an item to move it to another category (or long-press the item for move, rename, delete).

A widget can't switch on the keyboard's dictation, so the window uses Android's speech recognizer
(the same Google engine as Gboard's mic) and reads questions aloud with text-to-speech.

## Language
Menu → Language: **Automatic** follows the keyboard's current language (then the phone's language),
or pick Hebrew or English. For spoken questions in Hebrew, make sure the Hebrew voice is installed:
Settings → System → Languages → Text-to-speech output → Speech Services by Google → Install voice data.

## Building
- GitHub: every push to `main` builds `VoiceLists.apk` and attaches it to the **latest** release.
- Android Studio: open the folder and run on the phone.
- Command line: `./gradlew assembleRelease` (needs the Android SDK with platform 34).

The signing key in `keystore/` is a personal key so new builds install over old ones. Keep the repo private.

## Installing on the phone
Open the APK on the phone, allow "Install unknown apps" for the browser/Files when asked, and install.
Then long-press the home screen → Widgets → Voice Lists to add the widget.
