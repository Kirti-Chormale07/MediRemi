package com.example.mediremind.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.mediremind.R
import com.example.mediremind.data.Appointment
import com.example.mediremind.data.Db
import com.example.mediremind.util.LocaleHelper
import com.example.mediremind.util.Scheduler
import com.example.mediremind.util.TimeFmt
import com.google.android.material.button.MaterialButton
import java.util.Calendar

/** Add or edit one doctor appointment (name, reason, place, date, time, lead time). */
class AddAppointmentActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_APPT_ID = "appt_id"

        /** Reminder lead options: minutes before the appointment. */
        private val LEAD_MINUTES = intArrayOf(0, 15, 60, 1440)
        private val LEAD_LABELS = intArrayOf(
            R.string.lead_at_time, R.string.lead_15, R.string.lead_60, R.string.lead_1day
        )
    }

    private lateinit var edtDoctor: EditText
    private lateinit var edtReason: EditText
    private lateinit var edtPlace: EditText
    private lateinit var btnDate: TextView
    private lateinit var btnTime: TextView
    private lateinit var spnLead: Spinner

    private var apptId: Long = -1
    private var year = 0
    private var month = 0
    private var day = 0
    private var hour = 10
    private var minute = 0

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_appointment)

        apptId = intent.getLongExtra(EXTRA_APPT_ID, -1)

        edtDoctor = findViewById(R.id.edtApptDoctor)
        edtReason = findViewById(R.id.edtApptReason)
        edtPlace = findViewById(R.id.edtApptPlace)
        btnDate = findViewById(R.id.btnApptDate)
        btnTime = findViewById(R.id.btnApptTime)
        spnLead = findViewById(R.id.spnApptLead)

        spnLead.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            LEAD_LABELS.map { getString(it) }
        )
        spnLead.setSelection(2) // default: 1 hour before

        val cal = Calendar.getInstance()
        if (apptId > 0) {
            Db(this).getAppointment(apptId)?.let { a ->
                edtDoctor.setText(a.doctor)
                edtReason.setText(a.reason)
                edtPlace.setText(a.place)
                spnLead.setSelection(LEAD_MINUTES.indexOf(a.leadMin).coerceAtLeast(0))
                cal.timeInMillis = a.at
            }
        } else {
            // New appointment: tomorrow at 10:00 AM (a comfortable default).
            cal.add(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 10)
            cal.set(Calendar.MINUTE, 0)
        }
        year = cal.get(Calendar.YEAR)
        month = cal.get(Calendar.MONTH)
        day = cal.get(Calendar.DAY_OF_MONTH)
        hour = cal.get(Calendar.HOUR_OF_DAY)
        minute = cal.get(Calendar.MINUTE)
        refreshDateButton()
        refreshTimeButton()

        btnDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d -> year = y; month = m; day = d; refreshDateButton() },
                year, month, day
            ).show()
        }
        btnTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, h, m -> hour = h; minute = m; refreshTimeButton() },
                hour, minute,
                android.text.format.DateFormat.is24HourFormat(this)
            ).show()
        }

        findViewById<MaterialButton>(R.id.btnSaveAppt).setOnClickListener { save() }

        if (apptId > 0) {
            findViewById<MaterialButton>(R.id.btnDeleteAppt).setOnClickListener { confirmDelete() }
        } else {
            findViewById<MaterialButton>(R.id.btnDeleteAppt).visibility =
                android.view.View.GONE
        }
    }

    private fun refreshDateButton() {
        val cal = Calendar.getInstance().apply {
            set(year, month, day, hour, minute)
        }
        btnDate.text = getString(R.string.appt_date_fmt, TimeFmt.date(this, cal.timeInMillis))
    }

    private fun refreshTimeButton() {
        val cal = Calendar.getInstance().apply {
            set(year, month, day, hour, minute)
        }
        btnTime.text = getString(R.string.appt_time_fmt, TimeFmt.time(this, cal.timeInMillis))
    }

    private fun appointmentMillis(): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun save() {
        val doctor = edtDoctor.text.toString().trim()
        if (doctor.isEmpty()) {
            Toast.makeText(this, R.string.err_appt_doctor, Toast.LENGTH_LONG).show()
            return
        }
        val at = appointmentMillis()
        if (at <= System.currentTimeMillis()) {
            Toast.makeText(this, R.string.err_appt_future, Toast.LENGTH_LONG).show()
            return
        }

        val appt = Appointment(
            id = if (apptId > 0) apptId else 0,
            doctor = doctor,
            reason = edtReason.text.toString().trim(),
            place = edtPlace.text.toString().trim(),
            at = at,
            leadMin = LEAD_MINUTES[spnLead.selectedItemPosition]
        )
        var id = appt.id
        val newId = Db(this).saveAppointment(appt)
        if (newId > 0) id = newId

        // Re-arm: cancel any stale alarm, then schedule the new one.
        Scheduler.cancelAppointment(this, id)
        Db(this).getAppointment(id)?.let { Scheduler.scheduleAppointment(this, it) }

        Toast.makeText(this, R.string.appt_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.appt_delete_title)
            .setMessage(R.string.appt_delete_msg)
            .setPositiveButton(R.string.appt_delete) { _, _ ->
                Db(this).deleteAppointment(apptId)
                Scheduler.cancelAppointment(this, apptId)
                Toast.makeText(this, R.string.appt_deleted, Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
