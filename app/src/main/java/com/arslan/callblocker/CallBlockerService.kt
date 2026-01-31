package com.arslan.callblocker

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.SmsManager
import android.util.Log

class CallBlockerService : CallScreeningService() {

    companion object {
        private const val TAG = "CallBlockerService"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val prefs = getSharedPreferences("call_blocker_prefs", Context.MODE_PRIVATE)
        
        // Check if blocking is enabled
        if (!prefs.getBoolean("enabled", true)) {
            Log.d(TAG, "Blocking disabled, allowing call")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val phoneNumber = callDetails.handle?.schemeSpecificPart
        Log.d(TAG, "Incoming call from: $phoneNumber")

        if (phoneNumber.isNullOrEmpty()) {
            // Private/hidden number - block it
            Log.d(TAG, "Hidden number, blocking")
            blockCall(callDetails, null, prefs)
            return
        }

        // Check if number is in contacts
        if (isNumberInContacts(phoneNumber)) {
            Log.d(TAG, "Number is in contacts, allowing call")
            respondToCall(callDetails, CallResponse.Builder().build())
        } else {
            Log.d(TAG, "Number NOT in contacts, blocking")
            blockCall(callDetails, phoneNumber, prefs)
        }
    }

    private fun isNumberInContacts(phoneNumber: String): Boolean {
        val contentResolver: ContentResolver = contentResolver
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null
            )
            return cursor != null && cursor.count > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking contacts: ${e.message}")
            return false
        } finally {
            cursor?.close()
        }
    }

    private fun blockCall(callDetails: Call.Details, phoneNumber: String?, prefs: android.content.SharedPreferences) {
        // Block the call
        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)

        // Send messages if phone number is available
        if (!phoneNumber.isNullOrEmpty()) {
            val message = prefs.getString(
                "block_message",
                "Sorry, you're not in my contacts. Please WhatsApp me first."
            ) ?: return

            // Send SMS
            if (prefs.getBoolean("send_sms", true)) {
                sendSms(phoneNumber, message)
            }

            // Send WhatsApp
            if (prefs.getBoolean("send_whatsapp", true)) {
                sendWhatsAppMessage(phoneNumber, message)
            }

            // Log blocked call
            logBlockedCall(phoneNumber)
        }
    }

    private fun sendSms(phoneNumber: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            Log.d(TAG, "SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS: ${e.message}")
        }
    }

    private fun sendWhatsAppMessage(phoneNumber: String, message: String) {
        try {
            // Clean phone number (remove spaces, dashes, etc.)
            val cleanNumber = phoneNumber.replace(Regex("[^+0-9]"), "")
            
            // Create WhatsApp intent
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$cleanNumber?text=${Uri.encode(message)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // Check if WhatsApp is installed
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                Log.d(TAG, "WhatsApp message initiated to $cleanNumber")
            } else {
                Log.w(TAG, "WhatsApp not installed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending WhatsApp: ${e.message}")
        }
    }

    private fun logBlockedCall(phoneNumber: String) {
        try {
            val prefs = getSharedPreferences("blocked_calls_log", Context.MODE_PRIVATE)
            val timestamp = System.currentTimeMillis()
            val existingLog = prefs.getString("log", "") ?: ""
            val newEntry = "$timestamp|$phoneNumber\n"
            prefs.edit().putString("log", newEntry + existingLog).apply()
            Log.d(TAG, "Logged blocked call from $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging blocked call: ${e.message}")
        }
    }
}
