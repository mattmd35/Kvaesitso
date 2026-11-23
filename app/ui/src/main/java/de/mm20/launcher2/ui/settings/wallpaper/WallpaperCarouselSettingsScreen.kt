package de.mm20.launcher2.ui.settings.wallpaper

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.ui.platform.LocalContext
import de.mm20.launcher2.wallpaper.WallpaperSchedule
import java.util.Calendar
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.MainSwitchPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.wallpaper.WallpaperTarget
import kotlinx.serialization.Serializable

@Serializable
data object WallpaperCarouselSettingsRoute : NavKey

private const val Columns = 3

@Composable
fun WallpaperCarouselSettingsScreen() {
    val viewModel: WallpaperCarouselSettingsScreenVM = viewModel()
    val data by viewModel.data.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        viewModel.addImages(uris)
    }

    PreferenceScreen(title = stringResource(R.string.preference_screen_wallpaper_carousel)) {
        val data = data ?: return@PreferenceScreen
        item {
            MainSwitchPreference(
                title = stringResource(R.string.wallpaper_carousel_enable),
                value = data.enabled,
                onValueChanged = { viewModel.setEnabled(it) },
            )
        }
        item {
            PreferenceCategory {
                ListPreference(
                    title = stringResource(R.string.wallpaper_carousel_schedule),
                    items = listOf(
                        stringResource(R.string.wallpaper_carousel_schedule_interval) to WallpaperSchedule.Interval,
                        stringResource(R.string.wallpaper_carousel_schedule_times) to WallpaperSchedule.Times,
                    ),
                    value = data.schedule,
                    onValueChanged = { viewModel.setSchedule(it) },
                )
                AnimatedVisibility(data.schedule == WallpaperSchedule.Interval) {
                    ListPreference(
                        title = stringResource(R.string.wallpaper_carousel_interval),
                        items = listOf(
                            stringResource(R.string.wallpaper_carousel_interval_15m) to 15,
                            stringResource(R.string.wallpaper_carousel_interval_30m) to 30,
                            stringResource(R.string.wallpaper_carousel_interval_1h) to 60,
                            stringResource(R.string.wallpaper_carousel_interval_3h) to 180,
                            stringResource(R.string.wallpaper_carousel_interval_6h) to 360,
                            stringResource(R.string.wallpaper_carousel_interval_12h) to 720,
                            stringResource(R.string.wallpaper_carousel_interval_1d) to 1440,
                        ),
                        value = data.intervalMinutes,
                        onValueChanged = { viewModel.setInterval(it) },
                    )
                }
                AnimatedVisibility(data.schedule == WallpaperSchedule.Times) {
                    ScheduleTimes(
                        times = data.times,
                        onAdd = { viewModel.addTime(it) },
                        onRemove = { viewModel.removeTime(it) },
                    )
                }
                ListPreference(
                    title = stringResource(R.string.wallpaper_carousel_target),
                    items = listOf(
                        stringResource(R.string.wallpaper_carousel_target_both) to WallpaperTarget.HomeAndLock,
                        stringResource(R.string.wallpaper_carousel_target_home) to WallpaperTarget.Home,
                        stringResource(R.string.wallpaper_carousel_target_lock) to WallpaperTarget.Lock,
                    ),
                    value = data.target,
                    onValueChanged = { viewModel.setTarget(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.wallpaper_carousel_shuffle),
                    summary = stringResource(R.string.wallpaper_carousel_shuffle_summary),
                    value = data.shuffle,
                    onValueChanged = { viewModel.setShuffle(it) },
                )
                SwitchPreference(
                    title = stringResource(R.string.wallpaper_carousel_backup),
                    summary = stringResource(R.string.wallpaper_carousel_backup_summary),
                    value = data.includeInBackup,
                    onValueChanged = { viewModel.setIncludeInBackup(it) },
                )
                Preference(
                    title = stringResource(R.string.wallpaper_carousel_next),
                    icon = R.drawable.skip_next_24px,
                    enabled = data.wallpapers.isNotEmpty(),
                    onClick = { viewModel.next() },
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pluralStringResource(
                        R.plurals.wallpaper_carousel_count,
                        data.wallpapers.size,
                        data.wallpapers.size
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier
                        .padding(end = 12.dp)
                        .size(24.dp))
                }
                FilledTonalButton(
                    onClick = {
                        pickImages.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(
                        painterResource(R.drawable.add_24px),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = ButtonDefaults.IconSpacing)
                            .size(ButtonDefaults.IconSize),
                    )
                    Text(stringResource(R.string.wallpaper_carousel_add))
                }
            }
        }
        if (data.wallpapers.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.wallpaper_carousel_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        val rows = data.wallpapers.chunked(Columns)
        items(rows.size, key = { rows[it].first() }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (i in 0 until Columns) {
                    val name = rows[row].getOrNull(i)
                    if (name == null) {
                        Spacer(modifier = Modifier.weight(1f))
                        continue
                    }
                    WallpaperThumbnail(
                        modifier = Modifier.weight(1f),
                        file = viewModel.getFile(name),
                        isCurrent = data.wallpapers.getOrNull(data.currentIndex) == name,
                        onClick = { viewModel.show(name) },
                        onRemove = { viewModel.remove(name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WallpaperThumbnail(
    file: java.io.File,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .clip(shape)
            .then(
                if (isCurrent) Modifier.border(
                    BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                    shape
                ) else Modifier
            )
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.close_20px),
                contentDescription = stringResource(R.string.wallpaper_carousel_remove),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleTimes(
    times: List<Int>,
    onAdd: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            stringResource(R.string.wallpaper_carousel_times),
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            for (minute in times) {
                InputChip(
                    selected = false,
                    onClick = { onRemove(minute) },
                    label = { Text(formatMinute(context, minute)) },
                    trailingIcon = {
                        Icon(
                            painterResource(R.drawable.close_20px),
                            contentDescription = stringResource(R.string.wallpaper_carousel_remove),
                            modifier = Modifier.size(InputChipDefaults.IconSize),
                        )
                    },
                )
            }
            AssistChip(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m -> onAdd(h * 60 + m) },
                        12, 0,
                        DateFormat.is24HourFormat(context),
                    ).show()
                },
                label = { Text(stringResource(R.string.wallpaper_carousel_add_time)) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.add_24px),
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
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
