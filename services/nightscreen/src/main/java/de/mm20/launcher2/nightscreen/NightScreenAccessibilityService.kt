package de.mm20.launcher2.nightscreen

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Optional accessibility service. It doesn't read anything on screen; it only exists because
 * accessibility overlays are the only windows a regular app can draw above the status bar,
 * notification shade and quick settings.
 */
class NightScreenAccessibilityService : AccessibilityService(), KoinComponent {
    private val nightScreen: NightScreen by inject()

    override fun onServiceConnected() {
        super.onServiceConnected()
        nightScreen.attachAccessibilityService(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        nightScreen.detachAccessibilityService(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        nightScreen.detachAccessibilityService(this)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
