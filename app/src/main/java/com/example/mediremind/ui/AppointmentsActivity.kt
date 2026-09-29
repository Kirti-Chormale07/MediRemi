package com.example.mediremind.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mediremind.R
import com.example.mediremind.data.Appointment
import com.example.mediremind.data.Db
import com.example.mediremind.util.LocaleHelper
import com.example.mediremind.util.Scheduler
import com.example.mediremind.util.TimeFmt
import com.google.android.material.button.MaterialButton

/** List of upcoming doctor appointments – add / edit / delete. */
class AppointmentsActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private lateinit var empty: View

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appointments)

        list = findViewById(R.id.listAppts)
        empty = findViewById(R.id.emptyAppts)
        list.layoutManager = LinearLayoutManager(this)

        findViewById<MaterialButton>(R.id.btnAddAppt).setOnClickListener { openEditor(-1) }
    }

    override fun onResume() {
        super.onResume()
        if (LocaleHelper.isStale(this)) {
            recreate()
            return
        }
        refresh()
    }

    private fun refresh() {
        val appts = Db(this).upcomingAppointments()
        list.adapter = ApptAdapter(appts) { openEditor(it.id) }
        empty.visibility = if (appts.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (appts.isEmpty()) View.INVISIBLE else View.VISIBLE
    }

    private fun openEditor(apptId: Long) {
        startActivity(
            Intent(this, AddAppointmentActivity::class.java)
                .putExtra(AddAppointmentActivity.EXTRA_APPT_ID, apptId)
        )
    }

    private class ApptAdapter(
        private val items: List<Appointment>,
        private val onClick: (Appointment) -> Unit
    ) : RecyclerView.Adapter<ApptAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val doctor: TextView = view.findViewById(R.id.txtApptDoctor)
            val reason: TextView = view.findViewById(R.id.txtApptReason)
            val whenText: TextView = view.findViewById(R.id.txtApptWhen)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_appointment, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val a = items[position]
            val ctx = holder.itemView.context
            holder.doctor.text = a.doctor
            val detail = listOf(a.reason, a.place).filter { it.isNotBlank() }.joinToString(" • ")
            holder.reason.text = detail
            holder.reason.visibility = if (detail.isEmpty()) View.GONE else View.VISIBLE
            // "Today 10:30 AM" / "Tomorrow ..." / "28 Sep 2026, 10:30 AM"
            val day = relativeDay(ctx, a.at)
            holder.whenText.text =
                if (day.isEmpty()) TimeFmt.dateTime(ctx, a.at)
                else ctx.getString(R.string.appt_line, day, TimeFmt.dateTime(ctx, a.at))
            holder.itemView.setOnClickListener { onClick(a) }
        }

        private fun relativeDay(ctx: Context, at: Long): String {
            val nowCal = java.util.Calendar.getInstance()
            val atCal = java.util.Calendar.getInstance().apply { timeInMillis = at }
            val sameDay = nowCal.get(java.util.Calendar.YEAR) == atCal.get(java.util.Calendar.YEAR) &&
                nowCal.get(java.util.Calendar.DAY_OF_YEAR) == atCal.get(java.util.Calendar.DAY_OF_YEAR)
            if (sameDay) return ctx.getString(R.string.day_today)
            nowCal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            val tomorrow = nowCal.get(java.util.Calendar.YEAR) == atCal.get(java.util.Calendar.YEAR) &&
                nowCal.get(java.util.Calendar.DAY_OF_YEAR) == atCal.get(java.util.Calendar.DAY_OF_YEAR)
            return if (tomorrow) ctx.getString(R.string.day_tomorrow) else ""
        }
    }
}
