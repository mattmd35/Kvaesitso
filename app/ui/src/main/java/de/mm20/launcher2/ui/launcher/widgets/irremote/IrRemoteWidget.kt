package de.mm20.launcher2.ui.launcher.widgets.irremote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.widgets.IrRemoteWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun IrRemoteWidget(widget: IrRemoteWidget) {
    val context = LocalContext.current
    val transmitter = remember { IrTransmitter(context.applicationContext) }

    if (!transmitter.isAvailable) {
        Text(
            stringResource(R.string.ir_remote_no_emitter),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    val keys = IrRemotes.keys(widget.config.layout)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (row in keys) {
            Row(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (key in row) {
                    IrKeyButton(
                        key = key,
                        transmitter = transmitter,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun IrKeyButton(
    key: IrKey,
    transmitter: IrTransmitter,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }

    val isColor = key.kind == IrKey.Kind.Color && key.color != null
    val background = when {
        isColor -> key.color!!
        key.kind == IrKey.Kind.PowerOn -> MaterialTheme.colorScheme.primary
        key.kind == IrKey.Kind.PowerOff -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when {
        isColor -> if (background.luminance() > 0.5f) Color.Black else Color.White
        key.kind == IrKey.Kind.PowerOn -> MaterialTheme.colorScheme.onPrimary
        key.kind == IrKey.Kind.PowerOff -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = modifier
            .aspectRatio(if (key.kind == IrKey.Kind.Text) 1.6f else 1.6f)
            .scale(if (pressed) 0.92f else 1f)
            .clip(CircleShape)
            .background(background)
            .then(
                if (isColor) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                else Modifier
            )
            .pointerInput(key) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        // Send once, then repeat while the button is held
                        val job = scope.launch {
                            withContext(Dispatchers.IO) { transmitter.send(key.code) }
                            delay(400)
                            while (isActive) {
                                withContext(Dispatchers.IO) { transmitter.send(key.code) }
                                delay(150)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                        pressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when (key.kind) {
            IrKey.Kind.PowerOn, IrKey.Kind.PowerOff -> Icon(
                painterResource(R.drawable.power_settings_new_24px),
                contentDescription = stringResource(
                    if (key.kind == IrKey.Kind.PowerOn) R.string.ir_remote_on else R.string.ir_remote_off
                ),
                tint = content,
            )

            IrKey.Kind.PlayPause -> Icon(
                painterResource(R.drawable.play_arrow_24px),
                contentDescription = stringResource(R.string.ir_remote_play_pause),
                tint = content,
            )

            IrKey.Kind.BrightnessUp -> Icon(
                painterResource(R.drawable.light_mode_24px_filled),
                contentDescription = stringResource(R.string.ir_remote_brighter),
                tint = content,
            )

            IrKey.Kind.BrightnessDown -> Icon(
                painterResource(R.drawable.light_mode_24px),
                contentDescription = stringResource(R.string.ir_remote_dimmer),
                tint = content,
                modifier = Modifier.size(18.dp),
            )

            IrKey.Kind.Text -> Text(
                key.label ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )

            IrKey.Kind.Color -> {}
        }
    }
}
