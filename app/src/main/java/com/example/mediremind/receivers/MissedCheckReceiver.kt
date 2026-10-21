package com.example.mediremind.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.mediremind.data.Db
import com.example.mediremind.data.Status
import com.example.mediremind.util.LocaleHelper
import com.example.mediremind.util.Notifs
import com.example.mediremind.util.Scheduler
import com.example.mediremind.util.SmsHelper

/**
 * Grace period expired with no answer:
 * mark the dose missed, notify on screen and SMS the guardian (in the app's language).
 */
class MissedCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getLongExtra(Scheduler.EXTRA_MED_ID, -1)
        val at = intent.getLongExtra(Scheduler.EXTRA_AT, 0L)
        if (medId < 0 || at <= 0) return

        val db = Db(context)
        val med = db.getMedicine(medId) ?: return
        // The dose alarm may never have run (phone was off / app killed) –
        // create the log row here so a long-overdue dose still escalates
        // instead of silently doing nothing.
        val status = db.ensureLog(med, at)
        if (status != Status.PENDING) return // answered already

        db.updateLog(medId, at, Status.MISSED, guardianNotified = 1)

        val wrapped = LocaleHelper.wrap(context)
        Notifs.showMissed(wrapped, medId, med.name)

        // Keep the process alive until the SIM has delivered the SMS.
        val pending = goAsync()
        Thread {
            try {
                SmsHelper.sendMissedBlocking(context, med.name, med.dose, at)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
