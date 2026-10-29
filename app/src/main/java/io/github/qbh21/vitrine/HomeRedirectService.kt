package io.github.qbh21.vitrine

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Makes Vitrine the home screen without disabling the stock launcher.
 *
 * On this TV the stock launcher's HOME filter has priority 2, which Android picks before any saved
 * default, and an app outside the system image can't match it. So this service watches for the stock
 * launcher's window (it only receives events from that one package, and can't read screen content)
 * and brings Vitrine up in its place: after the Home button, at startup, or when an app exits to home.
 *
 * Safety: if the stock launcher keeps coming back right after we switched away from it (Vitrine
 * crashing, say), stop redirecting for a while so the stock launcher stays usable as a fallback.
 */
class HomeRedirectService : AccessibilityService() {
    private var lastRedirect = 0L
    private var quickReturns = 0
    private var pausedUntil = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName != STOCK_LAUNCHER) return
        val now = SystemClock.elapsedRealtime()
        if (now < pausedUntil) return

        quickReturns = if (now - lastRedirect < QUICK_RETURN_MS) quickReturns + 1 else 0
        if (quickReturns >= MAX_QUICK_RETURNS) {
            pausedUntil = now + PAUSE_MS   // Vitrine isn't staying up; leave the stock launcher alone
            quickReturns = 0
            return
        }
        lastRedirect = now
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setClassName(packageName, MainActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(home)
        } catch (e: Exception) {
            pausedUntil = now + PAUSE_MS
        }
    }

    override fun onInterrupt() {}

    private companion object {
        const val STOCK_LAUNCHER = "com.google.android.tvlauncher"
        const val QUICK_RETURN_MS = 3_000L
        const val MAX_QUICK_RETURNS = 3
        const val PAUSE_MS = 10 * 60_000L
    }
}
