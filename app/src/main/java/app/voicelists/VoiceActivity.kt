package app.voicelists

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The small floating window the widgets open. It listens for an item, moves on as soon as it hears "add",
 * asks for the category, then the list when more than one is open, and saves. Questions are read aloud with text-to-speech.
 *
 * With [EXTRA_BATCH] it adds several items in a row instead: it asks for the list and the category
 * (each confirmed with "select" / "בחר"; a new category is created), then adds every "<item> add" to them until "done".
 */
class VoiceActivity : Activity() {

    companion object {
        /** Optional: add into this list without asking which one. */
        const val EXTRA_LIST = "list_id"
        /** Add several items in a row to one category of one list. */
        const val EXTRA_BATCH = "batch"
        /** With [EXTRA_BATCH] and [EXTRA_LIST]: go straight to adding items to this category. */
        const val EXTRA_CATEGORY = "category_id"
        private const val REQ_MIC = 1
    }

    private enum class Step { ITEM, CATEGORY, CONFIRM, NEW_CATEGORY, LIST, B_LIST, B_NEW_LIST, B_CATEGORY, B_ITEMS, DONE }

    private lateinit var s: Str
    private lateinit var data: Data
    private var step = Step.ITEM
    private var item: String? = null
    private var category: Category? = null
    private var pendingCategory: String? = null
    private var presetList: ItemList? = null

    private var batch = false
    private var presetCategory: Category? = null
    private var batchList: ItemList? = null
    /** A list or category name heard without "select", waiting for it. */
    private var pendingName: String? = null
    private val added = mutableListOf<String>()

    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var misses = 0

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsInitDone = false
    private val afterSpeech = mutableMapOf<String, () -> Unit>()
    private var utteranceCounter = 0
    private var started = false

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var titleView: TextView
    private lateinit var heardView: TextView
    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var micView: ImageView
    private lateinit var chips: LinearLayout
    private lateinit var chipsScroll: HorizontalScrollView
    private lateinit var actions: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = Lang.strings(this)
        data = Store.load(this)
        presetList = data.list(intent.getLongExtra(EXTRA_LIST, -1L))?.takeIf { !it.closed }
        batch = intent.getBooleanExtra(EXTRA_BATCH, false)
        presetCategory = data.category(intent.getLongExtra(EXTRA_CATEGORY, -1L))
        buildUi()

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status(s.noRecognizer)
            return
        }

        tts = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS && tts?.let {
                val r = it.setLanguage(s.locale)
                r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            } == true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {}
                override fun onDone(utteranceId: String) = speechFinished(utteranceId)
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) = speechFinished(utteranceId)
                override fun onError(utteranceId: String, errorCode: Int) = speechFinished(utteranceId)
            })
            ttsInitDone = true
            maybeStart()
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_MIC) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) maybeStart() else status(s.micPermission)
    }

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun maybeStart() {
        if (started || !ttsInitDone || !hasMic()) return
        started = true
        if (batch) beginBatch() else beginItem()
    }

    // ---- The conversation ----

    private fun beginItem() {
        step = Step.ITEM
        titleView.text = s.sayItem
        heardView.text = ""
        status(s.sayItemHint)
        showActions()
        listen()
    }

    private fun onHeard(results: List<String>) {
        misses = 0
        val first = results.first().trim()
        if (results.any { Matcher.isCancel(it) || (batch && Matcher.isDone(it)) }) {
            if (step == Step.B_ITEMS) finishBatch() else finish()
            return
        }
        when (step) {
            Step.ITEM -> {
                if (results.any { Matcher.isAdd(it) }) {
                    if (item != null) itemConfirmed() else listen()
                    return
                }
                val withAdd = results.firstNotNullOfOrNull { Matcher.stripTrailingAdd(it) }
                    ?: results.firstNotNullOfOrNull { Matcher.stripLeadingAdd(it) }
                if (withAdd != null) {
                    item = withAdd
                    itemConfirmed()
                    return
                }
                item = first
                heardView.text = first
                status(s.heardItem(first))
                showActions()
                listen()
            }
            Step.CATEGORY, Step.CONFIRM -> {
                // "new category pharmacy" / "קטגוריה חדשה בית מרקחת"
                val newName = results.firstNotNullOfOrNull { Matcher.stripNewCategory(it) }
                if (newName != null) {
                    if (newName.isEmpty()) askNewCategory() else createAndAdd(newName)
                    return
                }
                if (results.any { Matcher.isAdd(it) || (step == Step.CONFIRM && Matcher.isYes(it)) }) {
                    // "add" / "yes" on its own confirms the category read back, or waits for one.
                    if (step == Step.CONFIRM) createAndAdd() else listen()
                    return
                }
                if (step == Step.CONFIRM && results.any { Matcher.isAgain(it) }) {
                    askCategory()
                    return
                }
                // "pharmacy add": an existing category is used, a new one is created right away.
                val stripped = results.firstNotNullOfOrNull { Matcher.stripAdd(it) }
                val match = results.firstNotNullOfOrNull { Matcher.findCategory(data.categories, Matcher.stripAdd(it) ?: it) }
                when {
                    match != null -> categoryChosen(match)
                    stripped != null -> createAndAdd(stripped)
                    else -> askCreate(first)
                }
            }
            Step.NEW_CATEGORY -> {
                if (results.all { Matcher.isAdd(it) }) {
                    listen()
                    return
                }
                val name = results.firstNotNullOfOrNull { Matcher.stripNewCategory(it)?.ifEmpty { null } }
                    ?: Matcher.stripAdd(first) ?: first
                createAndAdd(name)
            }
            Step.LIST -> {
                if (results.all { Matcher.isAdd(it) }) {
                    listen()
                    return
                }
                val match = results.firstNotNullOfOrNull { Matcher.findList(data.openLists, Matcher.stripAdd(it) ?: it) }
                if (match != null) addTo(match) else askList()
            }
            Step.B_LIST, Step.B_CATEGORY -> {
                // The name, then "select" / "בחר".
                val names = results.mapNotNull { Matcher.stripChoice(it) }
                when {
                    names.isNotEmpty() -> choose(names)
                    results.any { Matcher.isChoice(it) } -> pendingName?.let { choose(listOf(it)) } ?: listen()
                    else -> {
                        pendingName = first
                        heardView.text = first
                        status(s.heardChoice(first))
                        showActions()
                        listen()
                    }
                }
            }
            Step.B_NEW_LIST -> when {
                results.any { Matcher.isYes(it) || Matcher.isChoice(it) } -> createBatchList()
                results.any { Matcher.isAgain(it) } -> askBatchList()
                // Maybe the name of another list.
                else -> chooseList(results.map { Matcher.stripChoice(it) ?: it })
            }
            Step.B_ITEMS -> {
                if (results.any { Matcher.isAdd(it) }) {
                    item?.let { batchAdd(it) } ?: listen()
                    return
                }
                val withAdd = results.firstNotNullOfOrNull { Matcher.stripAdd(it) }
                if (withAdd != null) {
                    batchAdd(withAdd)
                    return
                }
                item = first
                heardView.text = first
                status(s.heardItem(first))
                showActions()
                listen()
            }
            Step.DONE -> {}
        }
    }

    private fun itemConfirmed() {
        val text = item?.trim()?.replaceFirstChar { it.uppercase(s.locale) } ?: return
        item = text
        heardView.text = text
        askCategory()
    }

    private fun askCategory() {
        step = Step.CATEGORY
        pendingCategory = null
        titleView.text = s.askCategory
        showActions()
        say(s.askCategory) {
            status(s.categoryHint)
            listen()
        }
    }

    private fun askNewCategory() {
        step = Step.NEW_CATEGORY
        pendingCategory = null
        titleView.text = s.sayNewCategory
        showActions()
        say(s.sayNewCategory) { listen() }
    }

    private fun askCreate(spoken: String) {
        val name = spoken.trim().replaceFirstChar { it.uppercase(s.locale) }
        step = Step.CONFIRM
        pendingCategory = name
        titleView.text = "${s.category}: $name"
        showActions()
        say(s.unknownCategory(name, item ?: "")) { listen() }
    }

    private fun createAndAdd(spoken: String? = pendingCategory) {
        val name = spoken?.trim()?.ifEmpty { null }?.replaceFirstChar { it.uppercase(s.locale) }
            ?: return askCategory()
        // Saying the name of a category that already exists just uses it.
        categoryChosen(data.categoryNamed(name))
    }

    /** Then the list: asked only when more than one list is open. */
    private fun categoryChosen(c: Category) {
        category = c
        val lists = presetList?.let { listOf(it) } ?: data.openListsOrNew(s)
        if (lists.size == 1) addTo(lists[0]) else askList()
    }

    private fun askList() {
        step = Step.LIST
        titleView.text = s.askList
        showActions()
        say(s.askList) {
            status(s.listHint)
            listen()
        }
    }

    private fun addTo(list: ItemList) {
        val text = item ?: return
        val cat = category ?: return askCategory()
        step = Step.DONE
        showActions()
        if (Store.findOpenItem(list, text) != null) {
            say(s.alreadyExists(text, list.name)) { finish() }
            return
        }
        Store.addItem(list, cat, text)
        Store.save(this, data)
        titleView.text = "✓ ${cat.name} · ${list.name}"
        say(s.added(text, cat.name, list.name)) { finish() }
    }

    // ---- Adding several items in a row ----

    private fun beginBatch() {
        val list = presetList ?: return askBatchList()
        batchList = list
        val c = presetCategory ?: return askBatchCategory()
        startBatchItems(c, created = false)
    }

    private fun askBatchList() {
        step = Step.B_LIST
        pendingName = null
        titleView.text = s.askList
        heardView.text = ""
        showActions()
        say(s.askList) {
            status(s.batchListHint)
            listen()
        }
    }

    private fun choose(names: List<String>) {
        if (step == Step.B_CATEGORY) chooseBatchCategory(names) else chooseList(names)
    }

    /** An open list by that name, or an offer to create it. */
    private fun chooseList(names: List<String>) {
        val match = names.firstNotNullOfOrNull { Matcher.findList(data.openLists, it) }
        if (match != null) {
            batchList = match
            askBatchCategory()
            return
        }
        val name = names.first().trim().replaceFirstChar { it.uppercase(s.locale) }
        step = Step.B_NEW_LIST
        pendingName = name
        titleView.text = "${s.newList}: $name"
        showActions()
        say(s.listNotFound(name)) { listen() }
    }

    private fun createBatchList(name: String? = pendingName) {
        val n = name?.trim()?.ifEmpty { null } ?: return askBatchList()
        val list = ItemList(Store.newId(), n)
        data.lists.add(list)
        Store.save(this, data)
        batchList = list
        askBatchCategory()
    }

    private fun askBatchCategory() {
        step = Step.B_CATEGORY
        pendingName = null
        titleView.text = "${batchList?.name ?: ""}: ${s.askCategory}"
        heardView.text = ""
        showActions()
        say(s.askCategory) {
            status(s.batchCategoryHint)
            listen()
        }
    }

    /** The category by that name, created when there isn't one. */
    private fun chooseBatchCategory(names: List<String>) {
        val match = names.firstNotNullOfOrNull { Matcher.findCategory(data.categories, it) }
        if (match != null) return startBatchItems(match, created = false)
        val name = names.first().trim().replaceFirstChar { it.uppercase(s.locale) }
        if (name.isEmpty()) return askBatchCategory()
        val c = data.categoryNamed(name)
        Store.save(this, data)
        startBatchItems(c, created = true)
    }

    private fun startBatchItems(c: Category, created: Boolean) {
        val list = batchList ?: return askBatchList()
        category = c
        item = null
        step = Step.B_ITEMS
        titleView.text = "${c.name} · ${list.name}"
        heardView.text = ""
        showActions()
        say(if (created) s.categoryCreated(c.name) + " " + s.batchStart else s.batchStart) {
            status(s.batchHint)
            listen()
        }
    }

    private fun batchAdd(raw: String) {
        val list = batchList ?: return
        val c = category ?: return
        val text = raw.trim().replaceFirstChar { it.uppercase(s.locale) }
        item = null
        heardView.text = text
        showActions()
        if (Store.findOpenItem(list, text) != null) {
            say(s.alreadyInList(text)) {
                status(s.batchHint)
                listen()
            }
            return
        }
        Store.addItem(list, c, text)
        Store.save(this, data)
        added.add(text)
        logView.text = added.takeLast(6).joinToString("\n") { "✓ $it" }
        logView.visibility = View.VISIBLE
        say(s.addedShort) {
            status(s.batchHint)
            listen()
        }
    }

    private fun finishBatch() {
        step = Step.DONE
        showActions()
        say(s.batchDone(added.size, category?.name ?: "", batchList?.name ?: "")) { finish() }
    }

    /** Lets a name be typed instead of said. */
    private fun typeName(title: String, then: (String) -> Unit) {
        stopListening()
        prompt(title, "") { then(it) }
    }

    // ---- Speech in and out ----

    private fun say(text: String, then: () -> Unit) {
        stopListening()
        status(text)
        val engine = tts
        if (!ttsReady || engine == null) {
            handler.postDelayed({ if (!isFinishing) then() }, 1200)
            return
        }
        val id = "u${utteranceCounter++}"
        afterSpeech[id] = then
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    private fun speechFinished(id: String) {
        handler.post { if (!isFinishing) afterSpeech.remove(id)?.invoke() }
    }

    private fun listen() {
        if (isFinishing || !hasMic()) return
        val rec = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, s.speechTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, s.speechTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        listening = true
        micView.alpha = 1f
        rec.startListening(intent)
    }

    private fun stopListening() {
        if (listening) recognizer?.cancel()
        listening = false
        micView.alpha = 0.4f
        micView.scaleX = 1f
        micView.scaleY = 1f
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (step == Step.ITEM && item == null) status(s.listening)
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            val scale = 1f + (rmsdB.coerceIn(0f, 10f) / 25f)
            micView.scaleX = scale
            micView.scaleY = scale
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {
            val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (p.isNullOrBlank()) return
            heardView.text = p
            // Act on "add" / "הוסף" the moment it's heard, without waiting for the pause at the end.
            if (!listening) return
            if (step == Step.CATEGORY || step == Step.CONFIRM || step == Step.NEW_CATEGORY || step == Step.LIST) {
                if (Matcher.stripTrailingAdd(p) != null || (step == Step.CONFIRM && Matcher.isAdd(p))) {
                    stopListening()
                    onHeard(listOf(p))
                }
                return
            }
            if (step == Step.B_LIST || step == Step.B_CATEGORY) {
                if (Matcher.stripTrailingChoice(p) != null) {
                    stopListening()
                    onHeard(listOf(p))
                }
                return
            }
            if (step == Step.B_ITEMS) {
                if (Matcher.stripTrailingAdd(p) != null) {
                    stopListening()
                    onHeard(listOf(p))
                }
                return
            }
            if (step != Step.ITEM) return
            val withAdd = Matcher.stripTrailingAdd(p)
            when {
                withAdd != null -> { stopListening(); item = withAdd; itemConfirmed() }
                item != null && Matcher.isAdd(p) -> { stopListening(); itemConfirmed() }
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) {
            if (!listening) return
            stopListening()
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.filter { it.isNotBlank() }
            if (list.isNullOrEmpty()) onError(SpeechRecognizer.ERROR_NO_MATCH) else onHeard(list)
        }

        override fun onError(error: Int) {
            if (!listening && error != SpeechRecognizer.ERROR_NO_MATCH) return
            stopListening()
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    misses++
                    // While adding items in a row, keep listening through longer pauses.
                    if (misses <= (if (step == Step.B_ITEMS) 8 else 2)) listen() else status(s.notHeard)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> status(s.micPermission)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    recognizer?.destroy()
                    recognizer = null
                    misses++
                    if (misses <= 2) handler.postDelayed({ listen() }, 400) else status(s.notHeard)
                }
                else -> status(s.notHeard)
            }
        }
    }

    // ---- Window ----

    private fun status(text: String) {
        statusView.text = text
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(16))
        }
        Lang.applyDirection(root, s.he)

        titleView = text(if (batch) s.batchTitle else s.sayItem, 20f, bold = true)
        heardView = text("", 26f).apply { setPadding(0, dp(12), 0, dp(4)); minHeight = dp(40) }
        statusView = text(if (batch) "" else s.sayItemHint, 15f).apply { alpha = 0.75f }
        logView = text("", 15f).apply {
            alpha = 0.8f
            setPadding(0, dp(8), 0, 0)
            visibility = View.GONE
        }

        micView = ImageView(this).apply {
            setImageResource(R.drawable.ic_mic)
            setColorFilter(getColor(android.R.color.white))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent())
            }
            setPadding(dp(16), dp(16), dp(16), dp(16))
            contentDescription = s.mic
            alpha = 0.4f
            setOnClickListener {
                misses = 0
                when (step) {
                    Step.DONE -> {}
                    else -> { stopListening(); listen() }
                }
            }
        }
        val micRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(16))
            addView(micView, LinearLayout.LayoutParams(dp(72), dp(72)))
        }

        chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chipsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
            visibility = View.GONE
        }
        actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(12), 0, 0)
        }

        root.addView(titleView)
        root.addView(heardView)
        root.addView(statusView)
        root.addView(logView)
        root.addView(micRow)
        root.addView(chipsScroll)
        root.addView(actions)
        setContentView(root)
        setFinishOnTouchOutside(true)
    }

    /** Buttons for the current step, so everything can also be done by touch. */
    private fun showActions() {
        chips.removeAllViews()
        actions.removeAllViews()
        chipsScroll.visibility = View.GONE
        val gap = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = dp(8) }

        if (step == Step.CATEGORY || step == Step.CONFIRM) {
            chipsScroll.visibility = View.VISIBLE
            chips.addView(pill(s.newCategoryButton) { askNewCategory() }, gap)
        }
        if (step == Step.CATEGORY && data.categories.isNotEmpty()) {
            chipsScroll.visibility = View.VISIBLE
            for (c in data.categories) {
                chips.addView(pill(c.name, filled = false) { categoryChosen(c) }, gap)
            }
        }
        if (step == Step.LIST) {
            chipsScroll.visibility = View.VISIBLE
            for (l in data.openLists) {
                chips.addView(pill(l.name, filled = false) { addTo(l) }, gap)
            }
        }
        if (step == Step.B_LIST) {
            chipsScroll.visibility = View.VISIBLE
            chips.addView(pill("+ " + s.newList) { typeName(s.newList) { createBatchList(it) } }, gap)
            for (l in data.openLists) {
                chips.addView(pill(l.name, filled = false) {
                    batchList = l
                    askBatchCategory()
                }, gap)
            }
        }
        if (step == Step.B_CATEGORY) {
            chipsScroll.visibility = View.VISIBLE
            chips.addView(pill(s.newCategoryButton) { typeName(s.newCategory) { chooseBatchCategory(listOf(it)) } }, gap)
            for (c in data.categories) {
                chips.addView(pill(c.name, filled = false) { startBatchItems(c, created = false) }, gap)
            }
        }
        if (step != Step.DONE && step != Step.B_ITEMS) actions.addView(pill(s.cancel, filled = false) { finish() }, gap)
        when (step) {
            Step.ITEM -> if (item != null) actions.addView(pill(s.add) { itemConfirmed() }, gap)
            Step.CONFIRM -> {
                actions.addView(pill(s.again, filled = false) { askCategory() }, gap)
                actions.addView(pill(s.yes) { createAndAdd() }, gap)
            }
            Step.NEW_CATEGORY -> actions.addView(pill(s.type, filled = false) {
                stopListening()
                prompt(s.newCategory, "") { createAndAdd(it) }
            }, gap)
            Step.B_LIST, Step.B_CATEGORY -> pendingName?.let { name ->
                actions.addView(pill(s.select) { choose(listOf(name)) }, gap)
            }
            Step.B_NEW_LIST -> {
                actions.addView(pill(s.again, filled = false) { askBatchList() }, gap)
                actions.addView(pill(s.yes) { createBatchList() }, gap)
            }
            Step.B_ITEMS -> {
                actions.addView(pill(s.doneButton, filled = false) { finishBatch() }, gap)
                item?.let { pending -> actions.addView(pill(s.add) { batchAdd(pending) }, gap) }
            }
            else -> {}
        }
    }

    override fun onStop() {
        super.onStop()
        // A voice window that's no longer on screen shouldn't keep the mic.
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
