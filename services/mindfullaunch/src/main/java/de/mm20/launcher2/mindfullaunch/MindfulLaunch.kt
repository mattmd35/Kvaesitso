package de.mm20.launcher2.mindfullaunch

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Adds friction to attention-trap apps: watched apps show a short pause before they open,
 * so opening them is a decision rather than a reflex. Nothing is blocked.
 */
class MindfulLaunch internal constructor(
    private val context: Context,
    private val store: MindfulLaunchStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Kept hot so launch interception can be decided synchronously.
     */
    val data: StateFlow<MindfulLaunchData?> = store.data
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun shouldPause(packageName: String): Boolean {
        val data = data.value ?: return false
        return data.enabled && packageName in data.watchedPackages
    }

    fun opensToday(packageName: String): Int {
        val data = data.value ?: return 0
        if (data.day != today()) return 0
        return data.opensToday[packageName] ?: 0
    }

    fun setWatched(packageName: String, watched: Boolean) {
        scope.launch {
            store.update {
                it.copy(
                    watchedPackages = if (watched) it.watchedPackages + packageName
                    else it.watchedPackages - packageName
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        scope.launch { store.update { it.copy(enabled = enabled) } }
    }

    fun setDelay(seconds: Int) {
        scope.launch { store.update { it.copy(delaySeconds = seconds.coerceIn(1, 60)) } }
    }

    /**
     * The user went through with opening the app.
     */
    fun recordOpen(packageName: String) = count(packageName, skipped = false)

    /**
     * The user decided not to open the app.
     */
    fun recordSkip(packageName: String) = count(packageName, skipped = true)

    private fun count(packageName: String, skipped: Boolean) {
        scope.launch {
            store.update {
                val today = today()
                val base = if (it.day == today) it
                else it.copy(day = today, opensToday = emptyMap(), skipsToday = emptyMap())
                if (skipped) {
                    base.copy(skipsToday = base.skipsToday.increment(packageName))
                } else {
                    base.copy(opensToday = base.opensToday.increment(packageName))
                }
            }
        }
    }

    fun setInterruptions(enabled: Boolean? = null, minutes: Int? = null) {
        scope.launch {
            store.update {
                it.copy(
                    interruptionsEnabled = enabled ?: it.interruptionsEnabled,
                    interruptionMinutes = (minutes ?: it.interruptionMinutes).coerceIn(1, 120),
                )
            }
        }
    }

    fun setSpicy(spicy: Boolean) {
        scope.launch { store.update { it.copy(spicy = spicy) } }
    }

    fun setNfcUnlock(enabled: Boolean) {
        scope.launch { store.update { it.copy(nfcUnlock = enabled) } }
    }

    fun setUnlockMath(enabled: Boolean) {
        scope.launch { store.update { it.copy(unlockMath = enabled) } }
    }

    fun setUnlockTyping(enabled: Boolean) {
        scope.launch { store.update { it.copy(unlockTyping = enabled) } }
    }

    fun addNfcTag(id: ByteArray) {
        val hex = id.toHex()
        scope.launch { store.update { it.copy(nfcTagIds = it.nfcTagIds + hex) } }
    }

    fun clearNfcTags() {
        scope.launch { store.update { it.copy(nfcTagIds = emptySet(), nfcUnlock = false) } }
    }

    /**
     * Whether opening watched apps currently needs an NFC tag scan.
     */
    fun requiresNfc(): Boolean {
        val data = data.value ?: return false
        return data.nfcUnlock && data.nfcTagIds.isNotEmpty()
    }

    fun isKnownTag(id: ByteArray): Boolean = data.value?.nfcTagIds?.contains(id.toHex()) == true

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService<AppOpsManager>() ?: return false
        return appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    /**
     * Foreground time of [packageName] since midnight, or null without usage access.
     */
    fun foregroundTimeToday(packageName: String): Long? {
        if (!hasUsageAccess()) return null
        val usm = context.getSystemService<UsageStatsManager>() ?: return null
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val stats = usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())
        return stats[packageName]?.totalTimeInForeground ?: 0L
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun Map<String, Int>.increment(key: String) = this + (key to ((this[key] ?: 0) + 1))

    companion object {
        fun today(): Int {
            val cal = Calendar.getInstance()
            return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        }
    }
}
