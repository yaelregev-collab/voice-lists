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
