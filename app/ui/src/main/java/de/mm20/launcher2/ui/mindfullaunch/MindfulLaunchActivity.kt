package de.mm20.launcher2.ui.mindfullaunch

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.mindfullaunch.MindfulLaunch
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.services.favorites.FavoritesService
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideSettings
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.theme.LauncherTheme
import org.koin.android.ext.android.inject
import kotlin.math.ceil

/**
 * The pause before a watched app opens. Not a blocker: after a short breathing countdown the
 * app can be opened. "Not now" (or back) returns to the home screen.
 */
class MindfulLaunchActivity : BaseActivity() {
    private val mindfulLaunch: MindfulLaunch by inject()
    private val favoritesService: FavoritesService by inject()
    private val iconService: IconService by inject()

    private var searchable: SavableSearchable? = null
    private var decided = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val searchable = MindfulLaunchGate.pending ?: return finish()
        MindfulLaunchGate.pending = null
        this.searchable = searchable
        val packageName = MindfulLaunchGate.packageOf(searchable) ?: return finish()

        enableEdgeToEdge()

        val delaySeconds = mindfulLaunch.data.value?.delaySeconds ?: 5
        val opensToday = mindfulLaunch.opensToday(packageName)

        setContent {
            LauncherTheme {
                ProvideSettings {
                    BackHandler { skip(packageName) }
                    val iconSize = 96.dp
                    val iconPx = iconSize.toPixels().toInt()
                    val icon by remember(searchable.key) {
                        iconService.getIcon(searchable, iconPx)
                    }.collectAsStateWithLifecycle(null)

                    PauseScreen(
                        label = searchable.labelOverride ?: searchable.label,
                        opensToday = opensToday,
                        delaySeconds = delaySeconds,
                        icon = { ShapedLauncherIcon(size = iconSize, icon = { icon }) },
                        onOpen = { open(searchable, packageName) },
                        onSkip = { skip(packageName) },
                    )
                }
            }
        }
    }

    private fun open(searchable: SavableSearchable, packageName: String) {
        if (decided) return
        decided = true
        if (searchable.launch(this, null)) {
            favoritesService.reportLaunch(searchable)
            mindfulLaunch.recordOpen(packageName)
        }
        finish()
        overridePendingTransition(0, 0)
    }

    private fun skip(packageName: String) {
        if (decided) return
        decided = true
        mindfulLaunch.recordSkip(packageName)
        finish()
    }

    override fun onStop() {
        super.onStop()
        // Left via home/recents: don't linger on top of the launcher.
        if (!isChangingConfigurations && !isFinishing) finish()
    }
}

@Composable
private fun PauseScreen(
    label: String,
    opensToday: Int,
    delaySeconds: Int,
    icon: @Composable () -> Unit,
    onOpen: () -> Unit,
    onSkip: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            tween(durationMillis = delaySeconds * 1000, easing = LinearEasing)
        )
    }
    val done = progress.value >= 1f
    val remaining = ceil((1f - progress.value) * delaySeconds).toInt()

    // Slow breathing rhythm: ~4s in, ~4s out
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(4000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "breathScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
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
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            CircleShape
                        )
                )
                CircularProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.size(200.dp),
                    strokeWidth = 3.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    gapSize = 0.dp,
                )
                icon()
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.mindful_launch_breathe),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.mindful_launch_question, label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (opensToday == 0) stringResource(R.string.mindful_launch_first_today)
                else pluralStringResource(R.plurals.mindful_launch_opens_today, opensToday, opensToday),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))
            // "Not now" is the prominent action; opening is available but quieter.
            Button(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(0.7f),
            ) {
                Text(stringResource(R.string.mindful_launch_skip))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpen,
                enabled = done,
                modifier = Modifier.fillMaxWidth(0.7f),
            ) {
                AnimatedContent(
                    targetState = if (done) -1 else remaining,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "openLabel",
                ) {
                    Text(
                        if (it < 0) stringResource(R.string.mindful_launch_open, label)
                        else stringResource(R.string.mindful_launch_wait, it)
                    )
                }
            }
        }
    }
}
