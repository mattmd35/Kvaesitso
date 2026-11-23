package de.mm20.launcher2.nightscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Darkens and warms the whole screen with an overlay window, manually or on a schedule.
 *
 * Kvaesitso is the home app, so its process is kept alive by the system. That's why the overlay
 * is managed directly in the app process: no foreground service or alarms are needed. While the
 * screen is on, the state is re-evaluated every minute (ACTION_TIME_TICK).
 */
class NightScreen internal constructor(
    private val context: Context,
    private val store: NightScreenStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val now = MutableStateFlow(System.currentTimeMillis())
    private val permission = MutableStateFlow(canDrawOverlays())

    val data: Flow<NightScreenData> = store.data

    /**
     * Whether night screen should currently be on (regardless of the overlay permission).
     */
    val isActive: StateFlow<Boolean> = combine(store.data, now) { data, time -> data.isActive(time) }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val hasPermission: StateFlow<Boolean> = permission

    private var overlay: View? = null
    private var started = false

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = refresh()
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        scope.launch {
            combine(store.data, isActive, permission) { data, active, permitted ->
                Triple(data, active, permitted)
            }.collectLatest { (data, active, permitted) ->
                if (active && permitted) show(data) else hide()
                // Wake up exactly at the next schedule change (TIME_TICK only covers whole minutes
                // while the screen is on).
                val wait = data.nextTransition(System.currentTimeMillis()) - System.currentTimeMillis()
                val manualWait = data.manualUntil - System.currentTimeMillis()
                val next = listOf(wait, manualWait).filter { it > 0 }.minOrNull()
                if (next != null && next < 24 * 60 * 60 * 1000L) {
                    delay(next + 500)
                    refresh()
                }
            }
        }
    }

    /**
     * Re-evaluate time and permission, e.g. after returning from the permission screen.
     */
    fun refresh() {
        now.value = System.currentTimeMillis()
        permission.value = canDrawOverlays()
    }

    suspend fun setActive(active: Boolean) {
        refresh()
        val time = System.currentTimeMillis()
        store.update {
            // The override lasts until the schedule would change the state by itself
            it.copy(manualState = active, manualUntil = it.nextTransition(time))
        }
        refresh()
    }

    suspend fun toggle() = setActive(!isActive.value)

    suspend fun setDim(dim: Float) {
        store.update { it.copy(dim = dim.coerceIn(0f, 1f)) }
    }

    suspend fun setWarmth(warmth: Float) {
        store.update { it.copy(warmth = warmth.coerceIn(0f, 1f)) }
    }

    suspend fun setSchedule(enabled: Boolean, startMinute: Int? = null, endMinute: Int? = null) {
        store.update {
            it.copy(
                scheduleEnabled = enabled,
                startMinute = startMinute ?: it.startMinute,
                endMinute = endMinute ?: it.endMinute,
                // A new schedule replaces any manual override
                manualState = null,
                manualUntil = 0L,
            )
        }
        refresh()
    }

    private fun show(data: NightScreenData) {
        val (color, alpha) = overlayColor(data)
        val windowManager = context.getSystemService(WindowManager::class.java)
        val existing = overlay
        if (existing != null) {
            existing.setBackgroundColor(color)
            val params = existing.layoutParams as WindowManager.LayoutParams
            if (params.alpha != alpha) {
                params.alpha = alpha
                windowManager.updateViewLayout(existing, params)
            }
            return
        }

        val metrics = context.resources.displayMetrics
        // Square of the larger screen dimension, so that it covers the screen in any orientation
        val size = max(metrics.widthPixels, metrics.heightPixels) + 400
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = -200
            y = -200
            this.alpha = alpha
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            }
            title = "Night screen"
        }
        val view = View(context).apply {
            setBackgroundColor(color)
            this.alpha = 0f
        }
        try {
            windowManager.addView(view, params)
            overlay = view
            view.animate().alpha(1f).setDuration(600).start()
        } catch (e: Exception) {
            Log.e("NightScreen", "Failed to add overlay", e)
        }
    }

    private fun hide() {
        val view = overlay ?: return
        overlay = null
        view.animate().alpha(0f).setDuration(400).withEndAction {
            try {
                context.getSystemService(WindowManager::class.java).removeView(view)
            } catch (e: Exception) {
                Log.e("NightScreen", "Failed to remove overlay", e)
            }
        }.start()
    }

    companion object {
        /**
         * Overlays that are more opaque than this block touches to the apps below them
         * (Android 12+).
         */
        private const val MaxAlpha = 0.8f

        /**
         * Color and window alpha of the overlay. The overlay is blended over the screen, so the
         * screen is darkened by `alpha` and tinted by `color * alpha`.
         */
        fun overlayColor(data: NightScreenData): Pair<Int, Float> {
            val dimAlpha = data.dim * MaxAlpha
            val tintAlpha = data.warmth * 0.3f
            val alpha = min(MaxAlpha, dimAlpha + tintAlpha * (1f - dimAlpha))
            if (alpha <= 0f) return Color.TRANSPARENT to 0f
            // Share of the overlay that is amber instead of black
            val warmShare = (tintAlpha * (1f - dimAlpha) / alpha).coerceIn(0f, 1f)
            val color = Color.rgb(
                (255 * warmShare).toInt(),
                (120 * warmShare).toInt(),
                (20 * warmShare).toInt(),
            )
            return color to alpha
        }
    }
}
