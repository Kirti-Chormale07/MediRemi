package com.example.mediremind.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.mediremind.data.Prefs
import java.util.Locale

/**
 * Speaks the dose reminder out loud in the elder's chosen language.
 *
 * Used from two places:
 *  - DoseAlarmReceiver  → speaks the moment the alarm fires (even if the
 *    full-screen reminder never opens: screen off, phone in another app, …)
 *  - ReminderActivity   → speaks when the reminder screen appears / confirms
 *
 * [speakOnce] de-duplicates by key so the same dose is never announced twice,
 * and the locale is re-applied on every utterance, so changing the language in
 * Settings takes effect on the very next announcement.
 */
object VoiceAnnouncer {

    private const val TAG = "VoiceAnnouncer"
    private const val MAX_KEYS = 30
    private const val REDEDUP_WINDOW_MS = 60L * 60L * 1000L

    private var tts: TextToSpeech? = null
    private var initializing = false
    private val pendingWhenReady = ArrayList<() -> Unit>()

    /** key -> spokenAt, insertion ordered (oldest first). */
    private val spoken = LinkedHashMap<String, Long>()

    /** Stable key for one scheduled dose of one medicine. */
    fun doseKey(medId: Long, at: Long): String = "dose_${medId}_$at"

    /** Stable key for one doctor-appointment reminder. */
    fun apptKey(apptId: Long, at: Long): String = "appt_${apptId}_$at"

    /** Returns true the first time this key is spoken (within a 1-hour window). */
    @Synchronized
    fun isDuplicate(key: String, now: Long = System.currentTimeMillis()): Boolean {
        val it = spoken.entries.iterator()
        while (it.hasNext()) {
            if (now - it.next().value > REDEDUP_WINDOW_MS) it.remove()
        }
        if (spoken.containsKey(key)) return true
        spoken[key] = now
        while (spoken.size > MAX_KEYS) {
            val oldest = spoken.keys.first()
            spoken.remove(oldest)
        }
        return false
    }

    /**
     * Speaks [text] only if [key] has not been announced recently.
     * [onStarted] is invoked exactly once — when speech begins or when it is
     * skipped/disabled — so callers can release resources (e.g. goAsync()).
     */
    fun speakOnce(context: Context, key: String, text: String, onStarted: (() -> Unit)? = null) {
        if (isDuplicate(key)) {
            onStarted?.invoke()
            return
        }
        speak(context, text, onStarted)
    }

    /** Always speaks (e.g. the "very good!" confirmation), respecting the on/off switch. */
    fun speak(context: Context, text: String, onStarted: (() -> Unit)? = null) {
        val app = context.applicationContext
        if (!Prefs(app).voiceEnabled) {
            onStarted?.invoke()
            return
        }
        ensureEngine(app) {
            val engine = tts
            if (engine == null || !Prefs(app).voiceEnabled) {
                onStarted?.invoke()
                return@ensureEngine
            }
            applyLocale(engine, app)
            val id = "utter_${System.nanoTime()}"
            val queued = engine.speak(text, TextToSpeech.QUEUE_ADD, null, id)
            if (queued != TextToSpeech.SUCCESS) {
                Log.w(TAG, "speak() rejected: $queued")
            }
            onStarted?.invoke()
        }
    }

    /** Creates the engine once (async) and runs [onReady] when it can speak. */
    private fun ensureEngine(context: Context, onReady: () -> Unit) {
        synchronized(this) {
            if (tts != null && !initializing) {
                onReady()
                return
            }
            pendingWhenReady.add(onReady)
            if (initializing) return
            initializing = true
        }
        tts = TextToSpeech(context.applicationContext) { status ->
            val callbacks: List<() -> Unit>
            synchronized(this) {
                initializing = false
                if (status == TextToSpeech.SUCCESS) {
                    tts?.let { applyLocale(it, context.applicationContext) }
                } else {
                    Log.w(TAG, "TTS engine failed to start: $status")
                    tts = null
                }
                callbacks = ArrayList(pendingWhenReady)
                pendingWhenReady.clear()
            }
            callbacks.forEach { it() }
        }
    }

    private fun applyLocale(engine: TextToSpeech, context: Context) {
        val locale = Locale(Prefs(context).langCode())
        val res = engine.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            // No voice pack for this language → best available default voice.
            engine.setLanguage(Locale.getDefault())
        }
        engine.setSpeechRate(0.9f)
    }
}
