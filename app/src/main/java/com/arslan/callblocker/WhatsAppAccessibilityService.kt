package com.arslan.callblocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WhatsAppAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "WhatsAppAutoSend"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        
        // Static flag to track if we're waiting to send
        @Volatile
        var pendingSend = false
        
        @Volatile
        var pendingNumber: String? = null
        
        @Volatile
        var pendingMessage: String? = null
        
        fun triggerSend(context: Context, phoneNumber: String, message: String) {
            pendingSend = true
            pendingNumber = phoneNumber
            pendingMessage = message
            Log.d(TAG, "Triggered send to $phoneNumber")
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private val maxRetries = 5

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service connected")
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames = arrayOf(WHATSAPP_PACKAGE)
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!pendingSend || event == null) return
        
        val packageName = event.packageName?.toString() ?: return
        if (packageName != WHATSAPP_PACKAGE) return

        Log.d(TAG, "WhatsApp event: ${event.eventType}")
        
        // Wait a moment for UI to settle, then try to send
        handler.postDelayed({
            tryToSendMessage()
        }, 1500)
    }

    private fun tryToSendMessage() {
        if (!pendingSend) return
        
        val rootNode = rootInActiveWindow ?: run {
            Log.d(TAG, "No root node available")
            retryIfNeeded()
            return
        }

        Log.d(TAG, "Searching for send button...")
        
        // Try to find and click the send button
        val sendButton = findSendButton(rootNode)
        
        if (sendButton != null) {
            Log.d(TAG, "Found send button, clicking...")
            sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            
            // Success! Reset state
            pendingSend = false
            pendingNumber = null
            pendingMessage = null
            retryCount = 0
            
            // Go back to home after a short delay
            handler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_HOME)
                Log.d(TAG, "Message sent, returning to home")
            }, 500)
        } else {
            Log.d(TAG, "Send button not found")
            retryIfNeeded()
        }
        
        rootNode.recycle()
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Method 1: Find by content description (most reliable)
        val sendDescriptions = listOf("Send", "إرسال", "أرسل", "Envoyer", "Senden")
        for (desc in sendDescriptions) {
            val nodes = root.findAccessibilityNodeInfosByText(desc)
            for (node in nodes) {
                if (node.isClickable && node.isEnabled) {
                    Log.d(TAG, "Found send button by text: $desc")
                    return node
                }
            }
        }
        
        // Method 2: Find by view ID (WhatsApp specific)
        val sendIds = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp:id/send_btn",
            "com.whatsapp:id/conversation_entry_send_button"
        )
        for (id in sendIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty() && nodes[0].isClickable) {
                Log.d(TAG, "Found send button by ID: $id")
                return nodes[0]
            }
        }
        
        // Method 3: Traverse and find ImageButton that looks like send
        return findSendButtonRecursive(root)
    }

    private fun findSendButtonRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val className = node.className?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        
        // Look for image buttons with send-related content descriptions
        if ((className.contains("ImageButton") || className.contains("ImageView")) &&
            node.isClickable && node.isEnabled) {
            if (contentDesc.contains("send") || contentDesc.contains("أرسل") || 
                contentDesc.contains("إرسال")) {
                return node
            }
        }
        
        // Recurse through children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findSendButtonRecursive(child)
            if (result != null) return result
            child.recycle()
        }
        
        return null
    }

    private fun retryIfNeeded() {
        retryCount++
        if (retryCount < maxRetries && pendingSend) {
            Log.d(TAG, "Retry $retryCount/$maxRetries")
            handler.postDelayed({
                tryToSendMessage()
            }, 1000)
        } else {
            Log.d(TAG, "Max retries reached, giving up")
            pendingSend = false
            retryCount = 0
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "Accessibility Service destroyed")
    }
}
