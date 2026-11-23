package de.mm20.launcher2.ui.launcher.widgets.irremote

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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

    val layout = IrRemotes.keys(widget.config.layout)
    val flat = layout.flatten()
    val powerOn = remember(flat) { flat.firstOrNull { it.kind == IrKey.Kind.PowerOn } }
    val powerOff = remember(flat) { flat.firstOrNull { it.kind == IrKey.Kind.PowerOff } }
    val brightnessUp = remember(flat) { flat.firstOrNull { it.kind == IrKey.Kind.BrightnessUp } }
    val brightnessDown = remember(flat) { flat.firstOrNull { it.kind == IrKey.Kind.BrightnessDown } }
    val playPause = remember(flat) { flat.firstOrNull { it.kind == IrKey.Kind.PlayPause } }
    val colorRows = remember(layout) {
        layout.map { row -> row.filter { it.kind == IrKey.Kind.Color } }.filter { it.isNotEmpty() }
    }
    val effects = remember(flat) { flat.filter { it.kind == IrKey.Kind.Text } }

    var isOn by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 380.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Power + quick controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(if (isOn) R.string.ir_remote_on else R.string.ir_remote_off),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = isOn,
                    onCheckedChange = { checked ->
                        isOn = checked
                        val key = if (checked) powerOn else powerOff
                        if (key != null) transmitter.send(key.code)
                    },
                )
            }
            if (playPause != null) {
                IconControl(R.drawable.play_arrow_24px, stringResource(R.string.ir_remote_play_pause), transmitter, playPause)
            }
        }

        // Brightness
        if (brightnessDown != null && brightnessUp != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BrightnessControl(R.drawable.light_mode_24px, stringResource(R.string.ir_remote_dimmer), transmitter, brightnessDown, Modifier.weight(1f))
                BrightnessControl(R.drawable.light_mode_24px_filled, stringResource(R.string.ir_remote_brighter), transmitter, brightnessUp, Modifier.weight(1f))
            }
        }

        // Colors
        for (row in colorRows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (key in row) {
                    ColorSwatch(key, transmitter, Modifier.weight(1f))
                }
                // keep alignment if the last row is short
                repeat(colorRows.first().size - row.size) { Box(Modifier.weight(1f)) }
            }
        }

        // Effects
        if (effects.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (key in effects.take(4)) {
                    EffectButton(key, transmitter, Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Send once on press, then repeat slowly while held. A subtle press highlight, no bounce.
 */
private fun Modifier.irPress(
    transmitter: IrTransmitter,
    key: IrKey,
    scope: kotlinx.coroutines.CoroutineScope,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onPressedChange: (Boolean) -> Unit,
): Modifier = pointerInput(key) {
    detectTapGestures(
        onPress = {
            onPressedChange(true)
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            val job = scope.launch {
                withContext(Dispatchers.IO) { transmitter.send(key.code) }
                delay(450)
                while (isActive) {
                    withContext(Dispatchers.IO) { transmitter.send(key.code) }
                    delay(220)
                }
            }
            tryAwaitRelease()
            job.cancel()
            onPressedChange(false)
        }
    )
}

@Composable
private fun ColorSwatch(key: IrKey, transmitter: IrTransmitter, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val color = key.color ?: Color.Transparent
    val ring by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
        label = "ring",
    )
    Box(
        modifier = modifier
            .aspectRatio(1.5f)
            .clip(CircleShape)
            .background(color)
            .border(if (pressed) 3.dp else 1.dp, ring, CircleShape)
            .irPress(transmitter, key, scope, haptic) { pressed = it },
    )
}

@Composable
private fun IconControl(icon: Int, desc: String, transmitter: IrTransmitter, key: IrKey) {
    BrightnessControl(icon, desc, transmitter, key, Modifier.size(56.dp, 48.dp))
}

@Composable
private fun BrightnessControl(icon: Int, desc: String, transmitter: IrTransmitter, key: IrKey, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
        label = "bg",
    )
    val fg = if (pressed) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(MaterialTheme.shapes.large)
            .background(bg)
            .irPress(transmitter, key, scope, haptic) { pressed = it },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = desc, tint = fg)
    }
}

@Composable
private fun EffectButton(key: IrKey, transmitter: IrTransmitter, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "bg",
    )
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .irPress(transmitter, key, scope, haptic) { pressed = it },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            key.label ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
