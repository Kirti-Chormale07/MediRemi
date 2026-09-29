package com.example.mediremind.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.example.mediremind.R
import com.example.mediremind.data.Db
import com.example.mediremind.data.Prefs
import com.example.mediremind.util.LocaleHelper
import com.example.mediremind.util.Notifs
import com.example.mediremind.util.Scheduler
import com.example.mediremind.util.TimeFmt
import com.example.mediremind.util.VoiceAnnouncer
import java.util.concurrent.atomic.AtomicBoolean

/** Appointment lead time reached: ring + speak the reminder in the elder's language. */
class AppointmentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Scheduler.ACTION_APPT) return
        val apptId = intent.getLongExtra(Scheduler.EXTRA_APPT_ID, -1)
        if (apptId < 0) return

        val appt = Db(context).getAppointment(apptId) ?: return // deleted meanwhile
        val now = System.currentTimeMillis()
        if (appt.at < now - 10 * 60_000L) return // appointment already over (stale alarm)

        Notifs.showAppointment(context, apptId)

        // Voice: "Ramesh, your appointment with Dr. Sharma is at 10:30 AM."
        val wrapped = LocaleHelper.wrap(context)
        val patient = Prefs(context).patientName
            .ifBlank { wrapped.getString(R.string.friend) }
        val prompt = wrapped.getString(
            R.string.tts_appt, patient, appt.doctor, TimeFmt.time(wrapped, appt.at)
        )

        val pending = goAsync()
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
                context, VoiceAnnouncer.apptKey(appt.id, appt.at), prompt
            ) { finishOnce() }
        }, 600)
    }
}
