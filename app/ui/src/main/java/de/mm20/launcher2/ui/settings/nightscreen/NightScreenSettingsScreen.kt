package de.mm20.launcher2.ui.settings.nightscreen

import android.app.StatusBarManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.nightscreen.NightScreen
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.MainSwitchPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.nightscreen.NightScreenTileService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Calendar
import kotlin.math.roundToInt

@Serializable
data object NightScreenSettingsRoute : NavKey

class NightScreenSettingsScreenVM : ViewModel(), KoinComponent {
    private val nightScreen: NightScreen by inject()

    val data = nightScreen.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val isActive = nightScreen.isActive
    val hasPermission = nightScreen.hasPermission
    val isAccessibilityEnabled = nightScreen.isAccessibilityEnabled

    fun refresh() = nightScreen.refresh()
    fun setActive(active: Boolean) = viewModelScope.launch { nightScreen.setActive(active) }
    fun setDim(dim: Float) = viewModelScope.launch { nightScreen.setDim(dim) }
    fun setWarmth(warmth: Float) = viewModelScope.launch { nightScreen.setWarmth(warmth) }
    fun setSchedule(enabled: Boolean, start: Int? = null, end: Int? = null) =
        viewModelScope.launch { nightScreen.setSchedule(enabled, start, end) }
}

@Composable
fun NightScreenSettingsScreen() {
    val viewModel: NightScreenSettingsScreenVM = viewModel()
    val context = LocalContext.current
    val data by viewModel.data.collectAsStateWithLifecycle()
    val isActive by viewModel.isActive.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsStateWithLifecycle()

    // Permission might have been granted in the system settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    PreferenceScreen(title = stringResource(R.string.night_screen)) {
        val data = data ?: return@PreferenceScreen
        if (!hasPermission) {
            item {
                PreferenceCategory {
                    Preference(
                        title = stringResource(R.string.night_screen_permission),
                        summary = stringResource(R.string.night_screen_permission_summary),
                        icon = R.drawable.warning_24px,
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        },
                    )
                }
            }
        }
        item {
            MainSwitchPreference(
                title = stringResource(R.string.night_screen_on),
                value = isActive,
                enabled = hasPermission,
                onValueChanged = { viewModel.setActive(it) },
            )
        }
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(R.string.night_screen_accessibility),
                    summary = stringResource(
                        if (isAccessibilityEnabled) R.string.night_screen_accessibility_on
                        else R.string.night_screen_accessibility_off
                    ),
                    icon = R.drawable.visibility_off_24px,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                )
            }
        }
        item {
            PreferenceCategory(stringResource(R.string.night_screen_category_look)) {
                SliderPreference(
                    title = stringResource(R.string.night_screen_dim),
                    value = data.dim,
                    onValueChanged = { viewModel.setDim(it) },
                    label = { Text("${(it * 100).roundToInt()} %") },
                )
                SliderPreference(
                    title = stringResource(R.string.night_screen_warmth),
                    value = data.warmth,
                    onValueChanged = { viewModel.setWarmth(it) },
                    label = { Text("${(it * 100).roundToInt()} %") },
                )
            }
        }
        item {
            PreferenceCategory(stringResource(R.string.night_screen_category_schedule)) {
                SwitchPreference(
                    title = stringResource(R.string.night_screen_schedule),
                    summary = stringResource(R.string.night_screen_schedule_summary),
                    icon = R.drawable.schedule_24px,
                    value = data.scheduleEnabled,
                    onValueChanged = { viewModel.setSchedule(it) },
                )
                AnimatedVisibility(data.scheduleEnabled) {
                    androidx.compose.foundation.layout.Column {
                        Preference(
                            title = stringResource(R.string.night_screen_start),
                            summary = formatMinute(context, data.startMinute),
                            onClick = {
                                pickTime(context, data.startMinute) {
                                    viewModel.setSchedule(true, start = it)
                                }
                            },
                        )
                        Preference(
                            title = stringResource(R.string.night_screen_end),
                            summary = formatMinute(context, data.endMinute),
                            onClick = {
                                pickTime(context, data.endMinute) {
                                    viewModel.setSchedule(true, end = it)
                                }
                            },
                        )
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                PreferenceCategory {
                    Preference(
                        title = stringResource(R.string.night_screen_add_tile),
                        summary = stringResource(R.string.night_screen_add_tile_summary),
                        icon = R.drawable.dark_mode_24px,
                        onClick = {
                            context.getSystemService(StatusBarManager::class.java)
                                .requestAddTileService(
                                    ComponentName(context, NightScreenTileService::class.java),
                                    context.getString(R.string.night_screen),
                                    Icon.createWithResource(context, R.drawable.dark_mode_24px),
                                    context.mainExecutor,
                                ) {}
                        },
                    )
                }
            }
        }
        if (!isAccessibilityEnabled) item {
            Text(
                stringResource(R.string.night_screen_accessibility_restricted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
        }
        item {
            Text(
                stringResource(R.string.night_screen_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}

private fun formatMinute(context: android.content.Context, minute: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minute / 60)
        set(Calendar.MINUTE, minute % 60)
    }
    return DateFormat.getTimeFormat(context).format(cal.time)
}

internal fun pickTime(context: android.content.Context, minute: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, h, m -> onPicked(h * 60 + m) },
        minute / 60,
        minute % 60,
        DateFormat.is24HourFormat(context),
    ).show()
}
