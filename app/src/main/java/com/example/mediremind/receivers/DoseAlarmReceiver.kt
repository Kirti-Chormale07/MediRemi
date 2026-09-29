package com.example.mediremind.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.example.mediremind.R
import com.example.mediremind.data.Db
import com.example.mediremind.data.Prefs
import com.example.mediremind.data.Status
import com.example.mediremind.util.LocaleHelper
import com.example.mediremind.util.Notifs
import com.example.mediremind.util.Scheduler
import com.example.mediremind.util.VoiceAnnouncer
import java.util.concurrent.atomic.AtomicBoolean

/** Alarm time reached: ring the notification and arm the guardian escalation. */
class DoseAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getLongExtra(Scheduler.EXTRA_MED_ID, -1)
        val at = intent.getLongExtra(Scheduler.EXTRA_AT, 0L)
        if (medId < 0 || at <= 0) return

        val db = Db(context)
        val med = db.getMedicine(medId) ?: return
        val status = db.ensureLog(med, at)
        if (status != Status.PENDING) return // already answered (taken/snoozed/missed)

        val dose = med.doses.minByOrNull {
            kotlin.math.abs(Scheduler.instantOf(Prefs(context), at, it) - at)
        } ?: return

        Notifs.showDose(context, med, dose, at)

        // Nobody answers within the grace period → guardian gets an SMS.
        val grace = Prefs(context).graceMin * 60_000L
        var checkAt = at + grace
        val now = System.currentTimeMillis()
        if (checkAt < now + 2000) checkAt = now + 2000
        Scheduler.scheduleMissedCheck(context, medId, at, checkAt)

        // ---- VOICE REMINDER: speak it out loud in the elder's language ----
        // Works even when the full-screen reminder never opens (screen off,
        // phone in another app, app in pocket).
        val wrapped = LocaleHelper.wrap(context)
        val patient = Prefs(context).patientName
            .ifBlank { wrapped.getString(R.string.friend) }
        val prompt = wrapped.getString(
            R.string.tts_prompt, patient, med.name, med.dose.ifBlank { " " }
        )

        val pending = goAsync() // keep the process alive while TTS speaks
        val handler = Handler(Looper.getMainLooper())
        val finished = AtomicBoolean(false)
        fun finishOnce() {
            if (finished.compareAndSet(false, true)) {
                try {
                    pending.finish()
                } catch (_: Exception) {
                }
            }
        }
        handler.postDelayed({ finishOnce() }, 8000) // safety net
        handler.postDelayed({ // let the alert tone play first
            VoiceAnnouncer.speakOnce(
                context, VoiceAnnouncer.doseKey(medId, at), prompt
            ) { finishOnce() }
        }, 600)
    }
}
