package com.example.mediremind.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.mediremind.R
import com.example.mediremind.data.Db
import com.example.mediremind.data.Prefs

/**
 * Guardian SMS delivery.
 * Messages always go out from the elder's own SIM card (SmsManager),
 * exactly as if the elder had typed them – so the guardian sees the
 * patient's own number. All messages are built in the app's selected language.
 */
object SmsHelper {

    private const val TAG = "SmsHelper"

    fun hasSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** Fire-and-forget on a background thread – used by UI actions (skip / SOS). */
    fun send(context: Context, number: String, message: String) {
        Thread { deliver(context, number, message) }.start()
    }

    /** Blocking version – receivers call it inside goAsync() + worker thread. */
    fun sendBlocking(context: Context, number: String, message: String) {
        deliver(context, number, message)
    }

    private fun deliver(context: Context, number: String, message: String) {
        if (number.isBlank()) return
        deviceSend(context, normalizeNumber(number), message)
    }

    /**
     * Cleans a guardian number so the carrier won't silently reject it:
     * strips spaces / dashes / brackets, turns a 00-prefix into +,
     * and drops the trunk 0 in front of a national 10-digit number
     * (e.g. "09876543210" → "9876543210"). Public for unit tests.
     */
    fun normalizeNumber(raw: String): String {
        val s = raw.filterNot {
            it.isWhitespace() || it == '-' || it == '(' || it == ')' || it == '.'
        }
        if (s.startsWith("00")) return "+" + s.substring(2)
        if (s.startsWith("+")) return s
        if (s.length == 11 && s.startsWith("0") && s.all { it.isDigit() }) return s.substring(1)
        return s
    }

    private fun deviceSend(context: Context, number: String, message: String) {
        if (!hasSmsPermission(context)) {
            Log.w(TAG, "SEND_SMS permission missing – SMS not sent")
            return
        }
        if (number.isEmpty()) {
            Log.w(TAG, "Empty guardian number – SMS not sent")
            return
        }
        try {
            SmsManager.getDefault().sendTextMessage(number, null, message, null, null)
            Log.i(TAG, "SMS sent via SIM card to $number")
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed", e)
        }
    }

    // ---------- message builders (always localised) ----------

    private fun guardianNumber(context: Context): String = Prefs(context).guardian.trim()

    private fun patientLabel(wrapped: Context): String =
        Prefs(wrapped).patientName.ifBlank { wrapped.getString(R.string.friend) }

    /** Dose nobody answered → the core escalation message. */
    fun sendMissed(context: Context, medName: String, dose: String, at: Long) {
        missedParts(context, medName, dose, at)?.let { send(context, it.first, it.second) }
    }

    fun sendMissedBlocking(context: Context, medName: String, dose: String, at: Long) {
        missedParts(context, medName, dose, at)?.let { sendBlocking(context, it.first, it.second) }
    }

    private fun missedParts(
        context: Context, medName: String, dose: String, at: Long
    ): Pair<String, String>? {
        val number = guardianNumber(context)
        if (number.isEmpty()) return null
        val wrapped = LocaleHelper.wrap(context)
        val msg = wrapped.getString(
            R.string.sms_missed,
            patientLabel(wrapped),
            medName, dose, TimeFmt.time(wrapped, at)
        )
        return number to msg
    }

    /** Elder tapped "I will not take it". */
    fun sendSkip(context: Context, medId: Long, at: Long) {
        val number = guardianNumber(context)
        if (number.isEmpty()) return
        val med = Db(context).getMedicine(medId) ?: return
        val wrapped = LocaleHelper.wrap(context)
        val msg = wrapped.getString(
            R.string.sms_skip,
            patientLabel(wrapped),
            med.name, med.dose, TimeFmt.time(wrapped, at)
        )
        send(context, number, msg)
    }

    /** Red SOS button. */
    fun sendSos(context: Context) {
        val number = guardianNumber(context)
        if (number.isEmpty()) return
        val wrapped = LocaleHelper.wrap(context)
        val msg = wrapped.getString(R.string.sms_sos, patientLabel(wrapped))
        send(context, number, msg)
    }
}
