package com.arslan.callblocker

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.arslan.callblocker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.SEND_SMS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            requestCallScreeningRole()
        } else {
            Toast.makeText(this, "Permissions required for app to work", Toast.LENGTH_LONG).show()
        }
        updateStatus()
    }

    private val callScreeningRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("call_blocker_prefs", Context.MODE_PRIVATE)

        setupUI()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun setupUI() {
        // Load saved message
        val defaultMessage = "Sorry, you're not in my contacts. Please WhatsApp me first."
        binding.editTextMessage.setText(prefs.getString("block_message", defaultMessage))

        // Enable/disable switch
        binding.switchEnabled.isChecked = prefs.getBoolean("enabled", true)
        binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enabled", isChecked).apply()
            updateStatus()
        }

        // Send SMS switch
        binding.switchSendSms.isChecked = prefs.getBoolean("send_sms", true)
        binding.switchSendSms.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("send_sms", isChecked).apply()
        }

        // Send WhatsApp switch
        binding.switchSendWhatsapp.isChecked = prefs.getBoolean("send_whatsapp", true)
        binding.switchSendWhatsapp.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("send_whatsapp", isChecked).apply()
        }

        // Save message button
        binding.buttonSaveMessage.setOnClickListener {
            val message = binding.editTextMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                prefs.edit().putString("block_message", message).apply()
                Toast.makeText(this, "Message saved!", Toast.LENGTH_SHORT).show()
            }
        }

        // Request permissions button
        binding.buttonRequestPermissions.setOnClickListener {
            checkPermissions()
        }

        // Set as default caller ID button
        binding.buttonSetDefault.setOnClickListener {
            requestCallScreeningRole()
        }

        // Enable accessibility button
        binding.buttonEnableAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }
    }

    private fun checkPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            requestCallScreeningRole()
        }
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                callScreeningRoleLauncher.launch(intent)
            }
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "Find 'Call Blocker' and enable it for auto-send WhatsApp",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Please enable accessibility manually in Settings", Toast.LENGTH_LONG).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val accessibilityEnabled = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            )
            if (accessibilityEnabled != 1) return false

            val serviceString = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return serviceString.contains("${packageName}/${WhatsAppAccessibilityService::class.java.canonicalName}")
        } catch (e: Exception) {
            return false
        }
    }

    private fun updateStatus() {
        val allPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        val isCallScreeningApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } else {
            true
        }

        val isAccessibilityEnabled = isAccessibilityServiceEnabled()
        val isEnabled = prefs.getBoolean("enabled", true)

        val statusText = when {
            !allPermissionsGranted -> "⚠️ Missing permissions - tap 'Request Permissions'"
            !isCallScreeningApp -> "⚠️ Not set as call screening app - tap 'Set as Default'"
            !isEnabled -> "⏸️ Blocking disabled"
            else -> "✅ Active - Unknown callers will be blocked"
        }

        val accessibilityStatus = if (isAccessibilityEnabled) {
            "✅ WhatsApp auto-send enabled"
        } else {
            "⚠️ WhatsApp auto-send disabled - tap button below to enable"
        }

        binding.textViewStatus.text = statusText
        binding.textViewAccessibilityStatus.text = accessibilityStatus

        // Show/hide buttons based on state
        binding.buttonRequestPermissions.visibility = 
            if (!allPermissionsGranted) android.view.View.VISIBLE else android.view.View.GONE
        binding.buttonSetDefault.visibility = 
            if (allPermissionsGranted && !isCallScreeningApp) android.view.View.VISIBLE else android.view.View.GONE
        binding.buttonEnableAccessibility.visibility =
            if (!isAccessibilityEnabled) android.view.View.VISIBLE else android.view.View.GONE
    }
}
