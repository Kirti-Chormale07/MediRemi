package com.example.mediremind

import com.example.mediremind.data.Appointment
import com.example.mediremind.util.Scheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppointmentTriggerTest {

    private fun appt(at: Long, lead: Int) =
        Appointment(id = 1, doctor = "Dr. Sharma", reason = "BP", place = "Clinic", at = at, leadMin = lead)

    @Test
    fun leadTime_subtractsMinutesFromAppointmentTime() {
        val at = 1_790_000_000_000L
        assertEquals(at, Scheduler.apptTrigger(appt(at, 0)))
        assertEquals(at - 15 * 60_000L, Scheduler.apptTrigger(appt(at, 15)))
        assertEquals(at - 60 * 60_000L, Scheduler.apptTrigger(appt(at, 60)))
        assertEquals(at - 24L * 60 * 60_000L, Scheduler.apptTrigger(appt(at, 1440)))
    }

    @Test
    fun leadOptions_areAlwaysBeforeTheVisit() {
        val at = 1_790_000_000_000L
        for (lead in intArrayOf(0, 15, 60, 1440)) {
            assertTrue(Scheduler.apptTrigger(appt(at, lead)) <= at)
        }
    }

    @Test
    fun appointmentStaysValid_untilItStarts() {
        val at = 1_790_000_000_000L
        val appointment = appt(at, 60)
        // The reminder fires an hour early, the visit itself is unchanged.
        assertEquals(at, appointment.at)
        assertEquals(60, appointment.leadMin)
    }
}
