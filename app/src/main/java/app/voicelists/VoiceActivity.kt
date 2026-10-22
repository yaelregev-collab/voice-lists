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
 * The small floating window the widget opens. It listens for an item, adds as soon as it hears "add",
 * asks for the category, and saves. Questions are read aloud with text-to-speech.
 */
class VoiceActivity : Activity() {

    companion object {
        /** Optional: add straight into this category without asking. */
        const val EXTRA_CATEGORY = "category_id"
        private const val REQ_MIC = 1
    }

    private enum class Step { ITEM, CATEGORY, CONFIRM, NEW_CATEGORY, DONE }

    private lateinit var s: Str
    private lateinit var categories: MutableList<Category>
    private var step = Step.ITEM
    private var item: String? = null
    private var pendingCategory: String? = null
    private var presetCategory: Category? = null

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
    private lateinit var micView: ImageView
    private lateinit var chips: LinearLayout
    private lateinit var chipsScroll: HorizontalScrollView
    private lateinit var actions: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = Lang.strings(this)
        categories = Store.load(this)
        val presetId = intent.getLongExtra(EXTRA_CATEGORY, -1L)
        presetCategory = categories.firstOrNull { it.id == presetId }
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
        beginItem()
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
        if (results.any { Matcher.isCancel(it) }) {
            finish()
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
                val match = results.firstNotNullOfOrNull { Matcher.findCategory(categories, Matcher.stripAdd(it) ?: it) }
                when {
                    match != null -> addTo(match)
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
            Step.DONE -> {}
        }
    }

    private fun itemConfirmed() {
        val text = item?.trim()?.replaceFirstChar { it.uppercase(s.locale) } ?: return
        item = text
        heardView.text = text
        val existing = Store.findOpenItem(categories, text)
        if (existing != null) {
            step = Step.DONE
            showActions()
            say(s.alreadyExists(text, existing.first.name)) { finish() }
            return
        }
        val preset = presetCategory
        if (preset != null) addTo(preset) else askCategory()
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
        categories.firstOrNull { Matcher.sameText(it.name, name) }?.let { return addTo(it) }
        val cat = Category(Store.newId(), name)
        categories.add(cat)
        addTo(cat, created = true)
    }

    private fun addTo(category: Category, created: Boolean = false) {
        val text = item ?: return
        Store.addItem(category, text)
        Store.save(this, categories)
        step = Step.DONE
        titleView.text = "✓ ${category.name}"
        showActions()
        say(if (created) s.created(category.name, text) else s.added(text, category.name)) { finish() }
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
            if (step == Step.CATEGORY || step == Step.CONFIRM || step == Step.NEW_CATEGORY) {
                if (Matcher.stripTrailingAdd(p) != null || (step == Step.CONFIRM && Matcher.isAdd(p))) {
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
                    if (misses <= 2) listen() else status(s.notHeard)
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

        titleView = text(s.sayItem, 20f, bold = true)
        heardView = text("", 26f).apply { setPadding(0, dp(12), 0, dp(4)); minHeight = dp(40) }
        statusView = text(s.sayItemHint, 15f).apply { alpha = 0.75f }

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
        if (step == Step.CATEGORY && categories.isNotEmpty()) {
            chipsScroll.visibility = View.VISIBLE
            for (c in categories) {
                chips.addView(pill(c.name, filled = false) { addTo(c) }, gap)
            }
        }
        if (step != Step.DONE) actions.addView(pill(s.cancel, filled = false) { finish() }, gap)
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
