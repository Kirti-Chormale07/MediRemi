package com.example.mediremind.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mediremind.R
import com.example.mediremind.data.Db
import com.example.mediremind.data.Prefs
import com.example.mediremind.util.LocaleHelper
import com.example.mediremind.util.Notifs
import com.example.mediremind.util.Scheduler
import com.example.mediremind.util.TimeFmt
import com.example.mediremind.util.VoiceAnnouncer
import com.google.android.material.button.MaterialButton

/**
 * Full-screen "doctor appointment" reminder shown even when the phone is locked.
 * Speaks the announcement in the elder's chosen language.
 */
class AppointmentReminderActivity : AppCompatActivity() {

    private var apptId: Long = -1

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appointment_reminder)

        apptId = intent.getLongExtra(Scheduler.EXTRA_APPT_ID, -1)
        if (apptId < 0) {
            finish()
            return
        }

        val appt = Db(this).getAppointment(apptId)
        if (appt == null) {
            finish()
            return
        }

        findViewById<TextView>(R.id.txtApptDoctor).text = appt.doctor
        findViewById<TextView>(R.id.txtApptReason).text =
            if (appt.reason.isBlank()) appt.place else "${appt.reason} • ${appt.place}"
                .trim(' ', '•')
        findViewById<TextView>(R.id.txtApptWhen).text = TimeFmt.dateTime(this, appt.at)

        // Announcement (skipped if AppointmentReceiver already spoke this one).
        val patient = Prefs(this).patientName.ifBlank { getString(R.string.friend) }
        VoiceAnnouncer.speakOnce(
            this,
            VoiceAnnouncer.apptKey(appt.id, appt.at),
            getString(R.string.tts_appt, patient, appt.doctor, TimeFmt.time(this, appt.at))
        )

        findViewById<MaterialButton>(R.id.btnApptOk).setOnClickListener {
            Notifs.cancelAppointment(this, apptId)
            finish()
        }

        findViewById<MaterialButton>(R.id.btnApptCall).setOnClickListener {
            val phone = Prefs(this).doctorPhone
            if (phone.isBlank()) {
                Toast.makeText(this, R.string.doctor_no_number, Toast.LENGTH_LONG).show()
            } else {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            }
        }
    }
}
