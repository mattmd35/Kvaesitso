package de.mm20.launcher2.mindfullaunch

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

    private fun Map<String, Int>.increment(key: String) = this + (key to ((this[key] ?: 0) + 1))

    companion object {
        fun today(): Int {
            val cal = Calendar.getInstance()
            return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        }
    }
}
