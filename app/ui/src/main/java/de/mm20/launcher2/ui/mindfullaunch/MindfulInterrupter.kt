package de.mm20.launcher2.ui.mindfullaunch

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.PowerManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.ceil
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import de.mm20.launcher2.mindfullaunch.MindfulLaunch
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.theme.LauncherTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * While a watched app stays in the foreground, shows a reminder card on top of it every few
 * minutes ("12 min on Instagram so far" + a phrase), with "Close it" / "Keep going".
 *
 * The foreground app is polled from usage events (needs usage access). The card is an overlay
 * window rather than an activity: background activity starts are unreliable (HyperOS blocks them
 * separately), overlays only need "display over other apps".
 */
object MindfulInterrupter : KoinComponent {
    private const val TAG = "MindfulInterrupter"
    private const val POLL_MS = 5_000L

    private val mindfulLaunch: MindfulLaunch by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var context: Context
    private var started = false

    private var lastQuery = 0L
    private var foregroundPackage: String? = null
    private var sessionPackage: String? = null
    private var sessionStart = 0L
    private var nextInterruption = 0L

    private var overlay: View? = null
    private var overlayOwner: OverlayOwner? = null

    fun start(context: Context) {
        if (started) return
        started = true
        this.context = context.applicationContext
        scope.launch {
            while (true) {
                try {
                    tick()
                } catch (e: Exception) {
                    Log.e(TAG, "tick failed", e)
                }
                delay(POLL_MS)
            }
        }
    }

    /**
     * The pause screen just opened [packageName]: start the session clock now.
     */
    fun onOpenedThroughPause(packageName: String) {
        if (!started) return
        startSession(packageName, System.currentTimeMillis())
        // Started while our pause screen is still visible, so this is allowed. The foreground
        // service keeps HyperOS from freezing the launcher process while the app is in use.
        if (mindfulLaunch.data.value?.interruptionsEnabled == true) {
            MindfulSessionService.start(context)
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val data = mindfulLaunch.data.value
        val interactive = context.getSystemService<PowerManager>()?.isInteractive == true
        if (data == null || !data.enabled || !data.interruptionsEnabled || !interactive ||
            !mindfulLaunch.hasUsageAccess() || !mindfulLaunch.canDrawOverlays()
        ) {
            endSession()
            lastQuery = now
            return
        }

        updateForeground(now)
        val pkg = foregroundPackage
        if (pkg != null && pkg in data.watchedPackages) {
            if (sessionPackage != pkg) {
                if (sessionPackage != null) MindfulSessionService.stop(context)
                startSession(pkg, now)
            } else if (now >= nextInterruption && overlay == null) {
                showOverlay(pkg, now - sessionStart, data.spicy, data.delaySeconds)
                nextInterruption = now + data.interruptionMinutes * 60_000L
            }
        } else {
            endSession()
        }
    }

    private fun startSession(packageName: String, time: Long) {
        val minutes = mindfulLaunch.data.value?.interruptionMinutes ?: 10
        sessionPackage = packageName
        sessionStart = time
        nextInterruption = time + minutes * 60_000L
        foregroundPackage = packageName
    }

    private fun endSession() {
        if (sessionPackage != null) MindfulSessionService.stop(context)
        sessionPackage = null
        removeOverlay()
    }

    private fun updateForeground(now: Long) {
        val usm = context.getSystemService<UsageStatsManager>() ?: return
        // On the first run look back far enough to find whatever is open right now
        val from = if (lastQuery == 0L) now - 30 * 60_000L else lastQuery - 1_000L
        val events = usm.queryEvents(from, now)
        val event = UsageEvents.Event()
        while (events.getNextEvent(event)) {
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            // Our own pause screen sits on top of the watched app; it doesn't end the session
            if (event.packageName == context.packageName &&
                event.className == MindfulLaunchActivity::class.java.name
            ) continue
            foregroundPackage = event.packageName
        }
        lastQuery = now
    }

    private fun showOverlay(packageName: String, sessionMillis: Long, spicy: Boolean, waitSeconds: Int) {
        val wm = context.getSystemService<WindowManager>() ?: return
        val pm = context.packageManager
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
        val icon = try {
            pm.getApplicationIcon(packageName).toBitmap(256, 256).asImageBitmap()
        } catch (e: Exception) {
            null
        }
        val phrase = MindfulText.phrase(context, spicy)
        val today = mindfulLaunch.foregroundTimeToday(packageName)

        val owner = OverlayOwner().also { it.start() }
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                LauncherTheme {
                    InterruptionScreen(
                        phrase = phrase,
                        icon = icon,
                        waitSeconds = waitSeconds,
                        session = context.getString(
                            R.string.mindful_launch_session,
                            MindfulText.duration(sessionMillis),
                            label
                        ),
                        today = today?.let {
                            context.getString(
                                R.string.mindful_launch_time_today,
                                MindfulText.duration(it)
                            )
                        },
                        onClose = { closeApp() },
                        onKeepGoing = { removeOverlay() },
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        try {
            wm.addView(view, params)
            overlay = view
            overlayOwner = owner
        } catch (e: Exception) {
            Log.e(TAG, "Could not show interruption", e)
            owner.stop()
        }
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        overlay = null
        try {
            context.getSystemService<WindowManager>()?.removeView(view)
        } catch (e: Exception) {
            Log.e(TAG, "Could not remove interruption", e)
        }
        overlayOwner?.stop()
        overlayOwner = null
    }

    private fun closeApp() {
        endSession()
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private class OverlayOwner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry get() = controller.savedStateRegistry

        fun start() {
            controller.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}

@androidx.compose.runtime.Composable
private fun InterruptionScreen(
    phrase: String,
    icon: ImageBitmap?,
    waitSeconds: Int,
    session: String,
    today: String?,
    onClose: () -> Unit,
    onKeepGoing: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(waitSeconds * 1000, easing = LinearEasing))
    }
    val done = progress.value >= 1f
    val remaining = ceil((1f - progress.value) * waitSeconds).toInt()
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathScale",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            // Swallow touches so the app underneath can't be used while this is up
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(breath)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), CircleShape)
                )
                CircularProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.size(200.dp),
                    strokeWidth = 3.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    gapSize = 0.dp,
                )
                if (icon != null) {
                    Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(96.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = phrase,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = session,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (today != null) {
                Text(
                    text = today,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(48.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth(0.7f)) {
                Text(stringResource(R.string.mindful_launch_close_app))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onKeepGoing,
                enabled = done,
                modifier = Modifier.fillMaxWidth(0.7f),
            ) {
                Text(
                    if (done) stringResource(R.string.mindful_launch_keep_going)
                    else stringResource(R.string.mindful_launch_keep_going_wait, remaining)
                )
            }
        }
    }
}
