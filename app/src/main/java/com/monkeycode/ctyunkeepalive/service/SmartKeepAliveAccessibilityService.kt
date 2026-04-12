package com.monkeycode.ctyunkeepalive.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class SmartKeepAliveAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type in trackedEventTypes) {
            SmartKeepAliveTracker.recordInputActivity(this)
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        private val trackedEventTypes = setOf(
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
        )
    }
}
