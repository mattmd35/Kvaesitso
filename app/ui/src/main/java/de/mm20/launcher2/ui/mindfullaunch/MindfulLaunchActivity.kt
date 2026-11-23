package de.mm20.launcher2.ui.mindfullaunch

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay
import org.koin.android.ext.android.inject
import kotlin.math.ceil

internal enum class NfcGate {
    /** No NFC needed: the countdown unlocks "Open". */
    None,
    /** Waiting for a registered tag. */
    Ready,
    /** NFC is needed but switched off in system settings. */
    Off,
    /** A tag was scanned, but it isn't a registered one. */
    WrongTag,
}

/**
 * The pause before a watched app opens. After a short breathing countdown the app can be opened,
 * or through the enabled unlock challenges (NFC tag, math problem, typing a sentence). "Not now" (or back) returns home.
 */
class MindfulLaunchActivity : BaseActivity() {
    private val mindfulLaunch: MindfulLaunch by inject()
    private val favoritesService: FavoritesService by inject()
    private val iconService: IconService by inject()

    private var searchable: SavableSearchable? = null
    private var packageName: String? = null
    private var decided = false
    private var nfcGate by mutableStateOf(NfcGate.None)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val searchable = MindfulLaunchGate.pending ?: return finish()
        MindfulLaunchGate.pending = null
        this.searchable = searchable
        val packageName = MindfulLaunchGate.packageOf(searchable) ?: return finish()
        this.packageName = packageName

        enableEdgeToEdge()

        val data = mindfulLaunch.data.value
        val delaySeconds = data?.delaySeconds ?: 5
        val opensToday = mindfulLaunch.opensToday(packageName)
        val timeToday = mindfulLaunch.foregroundTimeToday(packageName)
        val phrase = MindfulText.phrase(this, data?.spicy == true)

        setContent {
            LauncherTheme {
                ProvideSettings {
                    BackHandler { skip() }
                    val iconSize = 96.dp
                    val iconPx = iconSize.toPixels().toInt()
                    val icon by remember(searchable.key) {
                        iconService.getIcon(searchable, iconPx)
                    }.collectAsStateWithLifecycle(null)

                    PauseScreen(
                        phrase = phrase,
                        label = searchable.labelOverride ?: searchable.label,
                        opensToday = opensToday,
                        timeToday = timeToday,
                        delaySeconds = delaySeconds,
                        nfcGate = nfcGate,
                        unlockMath = data?.unlockMath == true,
                        unlockTyping = data?.unlockTyping == true,
                        icon = { ShapedLauncherIcon(size = iconSize, icon = { icon }) },
                        onOpen = { open() },
                        onSkip = { skip() },
                        onEnableNfc = {
                            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                        },
                        onWrongTagShown = { nfcGate = NfcGate.Ready },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = NfcAdapter.getDefaultAdapter(this)
        nfcGate = when {
            // Without NFC hardware (e.g. settings restored from another phone) fall back to the countdown
            !mindfulLaunch.requiresNfc() || adapter == null -> NfcGate.None
            !adapter.isEnabled -> NfcGate.Off
            else -> NfcGate.Ready
        }
        android.util.Log.i(
            "MindfulNfc",
            "pause screen: gate=$nfcGate requires=${mindfulLaunch.requiresNfc()} tags=${mindfulLaunch.data.value?.nfcTagIds?.size}"
        )
        if (nfcGate == NfcGate.Ready) NfcScan.enable(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val id = NfcScan.tagId(intent) ?: return
        if (nfcGate == NfcGate.None) return
        val known = mindfulLaunch.isKnownTag(id)
        android.util.Log.i("MindfulNfc", "unlock: id=${NfcScan.hex(id)} known=$known")
        if (known) open() else nfcGate = NfcGate.WrongTag
    }

    override fun onPause() {
        super.onPause()
        NfcScan.disable(this)
    }

    private fun open() {
        val searchable = searchable ?: return
        val packageName = packageName ?: return
        if (decided) return
        decided = true
        if (searchable.launch(this, null)) {
            favoritesService.reportLaunch(searchable)
            mindfulLaunch.recordOpen(packageName)
            MindfulInterrupter.onOpenedThroughPause(packageName)
        }
        finish()
        overridePendingTransition(0, 0)
    }

    private fun skip() {
        val packageName = packageName ?: return finish()
        if (decided) return
        decided = true
        mindfulLaunch.recordSkip(packageName)
        finish()
    }

    override fun onStop() {
        super.onStop()
        // Left via home/recents: don't linger on top of the launcher. Opening the NFC settings
        // also stops this activity, but then we want to come back.
        if (!isChangingConfigurations && !isFinishing && nfcGate != NfcGate.Off) finish()
    }
}

@Composable
private fun PauseScreen(
    phrase: String,
    label: String,
    opensToday: Int,
    timeToday: Long?,
    delaySeconds: Int,
    nfcGate: NfcGate,
    unlockMath: Boolean,
    unlockTyping: Boolean,
    icon: @Composable () -> Unit,
    onOpen: () -> Unit,
    onSkip: () -> Unit,
    onEnableNfc: () -> Unit,
    onWrongTagShown: () -> Unit,
) {
    var challenge by remember { mutableStateOf<Challenge?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            tween(durationMillis = delaySeconds * 1000, easing = LinearEasing)
        )
    }
    LaunchedEffect(nfcGate) {
        if (nfcGate == NfcGate.WrongTag) {
            delay(2000)
            onWrongTagShown()
        }
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
            .imePadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
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
                text = phrase,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (opensToday == 0) stringResource(R.string.mindful_launch_first_today)
                else pluralStringResource(R.plurals.mindful_launch_opens_today, opensToday, opensToday),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (timeToday != null && timeToday >= 60_000) {
                Text(
                    text = stringResource(
                        R.string.mindful_launch_time_today,
                        MindfulText.duration(timeToday)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(48.dp))
            // "Not now" is the prominent action; opening is available but quieter.
            Button(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(0.7f),
            ) {
                Text(stringResource(R.string.mindful_launch_skip))
            }
            Spacer(Modifier.height(8.dp))
            val anyChallenge = unlockMath || unlockTyping || nfcGate != NfcGate.None
            if (!anyChallenge) {
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
            // Math / typing become available after the countdown; the card works any time.
            if (unlockMath || unlockTyping) {
                when (challenge) {
                    Challenge.Math -> MathChallenge(onSolved = onOpen)
                    Challenge.Typing -> TypingChallenge(label = label, onDone = onOpen)
                    null -> {
                        if (unlockMath) {
                            OutlinedButton(
                                onClick = { challenge = Challenge.Math },
                                enabled = done,
                                modifier = Modifier.fillMaxWidth(0.7f),
                            ) {
                                Text(
                                    if (done) stringResource(R.string.mindful_launch_unlock_math_button)
                                    else stringResource(R.string.mindful_launch_wait, remaining)
                                )
                            }
                        }
                        if (unlockTyping) {
                            OutlinedButton(
                                onClick = { challenge = Challenge.Typing },
                                enabled = done,
                                modifier = Modifier.fillMaxWidth(0.7f),
                            ) {
                                Text(
                                    if (done) stringResource(R.string.mindful_launch_unlock_typing_button)
                                    else stringResource(R.string.mindful_launch_wait, remaining)
                                )
                            }
                        }
                    }
                }
            }
            when (nfcGate) {
                NfcGate.None -> {}
                NfcGate.Off -> TextButton(onClick = onEnableNfc) {
                    Text(stringResource(R.string.mindful_launch_nfc_turn_on))
                }

                NfcGate.Ready, NfcGate.WrongTag -> Text(
                    text = if (nfcGate == NfcGate.WrongTag) stringResource(R.string.mindful_launch_nfc_wrong_tag)
                    else stringResource(R.string.mindful_launch_nfc_scan_to_open, label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (nfcGate == NfcGate.WrongTag) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

internal enum class Challenge { Math, Typing }
