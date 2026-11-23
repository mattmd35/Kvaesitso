package de.mm20.launcher2.ui.settings.mindfullaunch

import android.os.Process
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
