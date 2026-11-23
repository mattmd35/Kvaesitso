package de.mm20.launcher2.ui.settings.mindfullaunch

import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.mindfullaunch.MindfulLaunch
import de.mm20.launcher2.mindfullaunch.MindfulLaunchData
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.SearchablePicker
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.MainSwitchPreference
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.mindfullaunch.MindfulLaunchGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
data object MindfulLaunchSettingsRoute : NavKey

class MindfulLaunchSettingsScreenVM : ViewModel(), KoinComponent {
    private val mindfulLaunch: MindfulLaunch by inject()
    private val appRepository: AppRepository by inject()
    private val iconService: IconService by inject()

    val data = mindfulLaunch.data

    fun setEnabled(enabled: Boolean) = mindfulLaunch.setEnabled(enabled)
    fun setDelay(seconds: Int) = mindfulLaunch.setDelay(seconds)
    fun setWatched(packageName: String, watched: Boolean) =
        mindfulLaunch.setWatched(packageName, watched)

    fun setInterruptions(enabled: Boolean? = null, minutes: Int? = null) =
        mindfulLaunch.setInterruptions(enabled, minutes)
    fun setSpicy(spicy: Boolean) = mindfulLaunch.setSpicy(spicy)
    fun setInsteadEnabled(enabled: Boolean) = mindfulLaunch.setInsteadEnabled(enabled)
    fun addInsteadTask(task: String) = mindfulLaunch.addInsteadTask(task)
    fun removeInsteadTask(task: String) = mindfulLaunch.removeInsteadTask(task)
    fun setInsteadApp(packageName: String, added: Boolean) = mindfulLaunch.setInsteadApp(packageName, added)
    fun setGraceSeconds(seconds: Int) = mindfulLaunch.setGraceSeconds(seconds)
    fun setUnlockMath(enabled: Boolean) = mindfulLaunch.setUnlockMath(enabled)
    fun setUnlockTyping(enabled: Boolean) = mindfulLaunch.setUnlockTyping(enabled)
    fun setNfcUnlock(enabled: Boolean) = mindfulLaunch.setNfcUnlock(enabled)
    fun addNfcTag(id: ByteArray) = mindfulLaunch.addNfcTag(id)
    fun clearNfcTags() = mindfulLaunch.clearNfcTags()
    fun hasUsageAccess() = mindfulLaunch.hasUsageAccess()
    fun canDrawOverlays() = mindfulLaunch.canDrawOverlays()

    fun getApp(packageName: String): Flow<Application?> =
        appRepository.findOne(packageName, Process.myUserHandle())

    fun getIcon(app: Application?, size: Int) =
        if (app == null) flowOf(null) else iconService.getIcon(app, size)
}

@Composable
fun MindfulLaunchSettingsScreen() {
    val viewModel: MindfulLaunchSettingsScreenVM = viewModel()
    val data by viewModel.data.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showInsteadPicker by rememberSaveable { mutableStateOf(false) }
    var showTaskDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }

    // Permissions may have been granted in the system settings
    var hasUsageAccess by remember { mutableStateOf(viewModel.hasUsageAccess()) }
    var canDrawOverlays by remember { mutableStateOf(viewModel.canDrawOverlays()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasUsageAccess = viewModel.hasUsageAccess()
        canDrawOverlays = viewModel.canDrawOverlays()
    }

    PreferenceScreen(title = stringResource(R.string.mindful_launch)) {
        val data = data ?: return@PreferenceScreen
        item {
            MainSwitchPreference(
                title = stringResource(R.string.mindful_launch_enabled),
                value = data.enabled,
                onValueChanged = { viewModel.setEnabled(it) },
            )
        }
        item {
            PreferenceCategory {
                SliderPreference(
                    title = stringResource(R.string.mindful_launch_delay),
                    icon = R.drawable.timer_24px,
                    value = data.delaySeconds,
                    min = 1,
                    max = 30,
                    onValueChanged = { viewModel.setDelay(it) },
                    label = { Text(stringResource(R.string.mindful_launch_delay_value, it)) },
                )
                ListPreference(
                    title = stringResource(R.string.mindful_launch_grace),
                    icon = R.drawable.schedule_24px,
                    items = listOf(
                        stringResource(R.string.mindful_launch_grace_off) to 0,
                        "30 s" to 30,
                        "1 min" to 60,
                        "2 min" to 120,
                        "5 min" to 300,
                        "10 min" to 600,
                        "15 min" to 900,
                    ),
                    value = data.graceSeconds,
                    onValueChanged = { viewModel.setGraceSeconds(it) },
                )
            }
        }
        item {
            PreferenceCategory(stringResource(R.string.mindful_launch_category_apps)) {
                val packages = data.watchedPackages.sorted()
                if (packages.isEmpty()) {
                    Preference(
                        title = { Text(stringResource(R.string.mindful_launch_no_apps)) },
                        enabled = false,
                    )
                }
                for (pkg in packages) {
                    WatchedAppPreference(viewModel, pkg, data)
                }
                Preference(
                    title = stringResource(R.string.mindful_launch_add_app),
                    icon = R.drawable.add_24px,
                    onClick = { showPicker = true },
                )
            }
        }
        item {
            PreferenceCategory(stringResource(R.string.mindful_launch_category_interruptions)) {
                SwitchPreference(
                    title = stringResource(R.string.mindful_launch_interruptions),
                    summary = stringResource(R.string.mindful_launch_interruptions_summary),
                    icon = R.drawable.notifications_24px,
                    value = data.interruptionsEnabled,
                    onValueChanged = { viewModel.setInterruptions(enabled = it) },
                )
                if (data.interruptionsEnabled && !hasUsageAccess) {
                    Preference(
                        title = stringResource(R.string.mindful_launch_usage_permission),
                        summary = stringResource(R.string.mindful_launch_usage_permission_summary),
                        icon = R.drawable.warning_24px,
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    .setData(Uri.parse("package:${context.packageName}"))
                            )
                        },
                    )
                }
                if (data.interruptionsEnabled && !canDrawOverlays) {
                    Preference(
                        title = stringResource(R.string.mindful_launch_overlay_permission),
                        summary = stringResource(R.string.mindful_launch_overlay_permission_summary),
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
                SliderPreference(
                    title = stringResource(R.string.mindful_launch_interval),
                    icon = R.drawable.timer_24px,
                    value = data.interruptionMinutes,
                    min = 1,
                    max = 60,
                    enabled = data.interruptionsEnabled,
                    onValueChanged = { viewModel.setInterruptions(minutes = it) },
                    label = { Text(stringResource(R.string.mindful_launch_interval_value, it)) },
                )
            }
        }
        item {
            PreferenceCategory(stringResource(R.string.mindful_instead_category)) {
                SwitchPreference(
                    title = stringResource(R.string.mindful_instead_enabled),
                    summary = stringResource(R.string.mindful_instead_enabled_summary),
                    icon = R.drawable.lightbulb_24px,
                    value = data.insteadEnabled,
                    onValueChanged = { viewModel.setInsteadEnabled(it) },
                )
                for (pkg in data.insteadApps) {
                    InsteadAppPreference(viewModel, pkg)
                }
                Preference(
                    title = stringResource(R.string.mindful_instead_add_app),
                    icon = R.drawable.add_24px,
                    enabled = data.insteadEnabled,
                    onClick = { showInsteadPicker = true },
                )
                for (task in data.insteadTasks) {
                    Preference(
                        title = { Text(task) },
                        controls = {
                            IconButton(onClick = { viewModel.removeInsteadTask(task) }) {
                                Icon(painterResource(R.drawable.close_24px), null)
                            }
                        },
                    )
                }
                Preference(
                    title = stringResource(R.string.mindful_instead_add_task),
                    summary = stringResource(R.string.mindful_instead_add_task_summary),
                    icon = R.drawable.add_24px,
                    enabled = data.insteadEnabled,
                    onClick = { showTaskDialog = true },
                )
            }
        }
        item {
            PreferenceCategory(stringResource(R.string.mindful_launch_category_tone)) {
                SwitchPreference(
                    title = stringResource(R.string.mindful_launch_spicy),
                    summary = stringResource(R.string.mindful_launch_spicy_summary),
                    icon = R.drawable.mood_24px,
                    value = data.spicy,
                    onValueChanged = { viewModel.setSpicy(it) },
                )
            }
        }
        item {
            PreferenceCategory(stringResource(R.string.mindful_launch_category_unlock)) {
                SwitchPreference(
                    title = stringResource(R.string.mindful_launch_unlock_math),
                    summary = stringResource(R.string.mindful_launch_unlock_math_summary),
                    icon = R.drawable.calculate_24px,
                    value = data.unlockMath,
                    onValueChanged = { viewModel.setUnlockMath(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.mindful_launch_unlock_typing),
                    summary = stringResource(R.string.mindful_launch_unlock_typing_summary),
                    icon = R.drawable.keyboard_24px,
                    value = data.unlockTyping,
                    onValueChanged = { viewModel.setUnlockTyping(it) },
                )
                if (nfcAdapter == null) {
                    Preference(
                        title = { Text(stringResource(R.string.mindful_launch_nfc_unavailable)) },
                        enabled = false,
                    )
                } else {
                    SwitchPreference(
                        title = stringResource(R.string.mindful_launch_nfc),
                        summary = stringResource(R.string.mindful_launch_nfc_summary),
                        icon = R.drawable.lock_24px,
                        value = data.nfcUnlock && data.nfcTagIds.isNotEmpty(),
                        enabled = data.nfcTagIds.isNotEmpty(),
                        onValueChanged = { viewModel.setNfcUnlock(it) },
                    )
                    Preference(
                        title = stringResource(R.string.mindful_launch_nfc_register),
                        summary = if (data.nfcTagIds.isEmpty()) stringResource(R.string.mindful_launch_nfc_none)
                        else pluralStringResource(
                            R.plurals.mindful_launch_nfc_registered,
                            data.nfcTagIds.size,
                            data.nfcTagIds.size
                        ),
                        icon = R.drawable.add_24px,
                        onClick = {
                            if (nfcAdapter.isEnabled) context.startActivity(
                                Intent(context, de.mm20.launcher2.ui.mindfullaunch.NfcRegisterActivity::class.java)
                            )
                            else context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                        },
                    )
                    if (data.nfcTagIds.isNotEmpty()) {
                        Preference(
                            title = stringResource(R.string.mindful_launch_nfc_clear),
                            icon = R.drawable.delete_24px,
                            onClick = { viewModel.clearNfcTags() },
                        )
                    }
                }
            }
        }
    }

    if (showTaskDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTaskDialog = false },
            title = { Text(stringResource(R.string.mindful_instead_add_task)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.mindful_instead_task_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        viewModel.addInsteadTask(text)
                        showTaskDialog = false
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTaskDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    DismissableBottomSheet(
        expanded = showInsteadPicker,
        onDismissRequest = { showInsteadPicker = false },
    ) {
        SearchablePicker(
            value = null,
            onValueChanged = { picked ->
                picked?.let { MindfulLaunchGate.packageOf(it) }?.let {
                    viewModel.setInsteadApp(it, true)
                }
                showInsteadPicker = false
            },
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
        )
    }

    DismissableBottomSheet(
        expanded = showPicker,
        onDismissRequest = { showPicker = false },
    ) {
        SearchablePicker(
            value = null,
            onValueChanged = { picked ->
                picked?.let { MindfulLaunchGate.packageOf(it) }?.let {
                    viewModel.setWatched(it, true)
                }
                showPicker = false
            },
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
        )
    }
}

@Composable
private fun WatchedAppPreference(
    viewModel: MindfulLaunchSettingsScreenVM,
    packageName: String,
    data: MindfulLaunchData,
) {
    val app by remember(packageName) { viewModel.getApp(packageName) }
        .collectAsStateWithLifecycle(null)
    val iconSize = 32.dp
    val iconPx = iconSize.toPixels().toInt()
    val icon by remember(app) { viewModel.getIcon(app, iconPx) }
        .collectAsStateWithLifecycle(null)
    val isToday = data.day == MindfulLaunch.today()
    val opens = if (isToday) data.opensToday[packageName] ?: 0 else 0
    val skips = if (isToday) data.skipsToday[packageName] ?: 0 else 0

    Preference(
        title = { Text(app?.let { it.labelOverride ?: it.label } ?: packageName) },
        summary = { Text(stringResource(R.string.mindful_launch_app_stats, opens, skips)) },
        icon = { ShapedLauncherIcon(size = iconSize, icon = { icon }) },
        controls = {
            IconButton(onClick = { viewModel.setWatched(packageName, false) }) {
                Icon(painterResource(R.drawable.close_24px), null)
            }
        },
    )
}

@Composable
private fun InsteadAppPreference(
    viewModel: MindfulLaunchSettingsScreenVM,
    packageName: String,
) {
    val app by remember(packageName) { viewModel.getApp(packageName) }
        .collectAsStateWithLifecycle(null)
    val iconSize = 32.dp
    val iconPx = iconSize.toPixels().toInt()
    val icon by remember(app) { viewModel.getIcon(app, iconPx) }
        .collectAsStateWithLifecycle(null)
    Preference(
        title = { Text(app?.let { it.labelOverride ?: it.label } ?: packageName) },
        icon = { ShapedLauncherIcon(size = iconSize, icon = { icon }) },
        controls = {
            IconButton(onClick = { viewModel.setInsteadApp(packageName, false) }) {
                Icon(painterResource(R.drawable.close_24px), null)
            }
        },
    )
}
