package de.mm20.launcher2.ui.launcher.search.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.apps.drawer.DrawerDragState
import de.mm20.launcher2.ui.launcher.search.apps.drawer.DrawerFolderTile
import de.mm20.launcher2.ui.launcher.search.apps.drawer.DrawerItem
import de.mm20.launcher2.ui.launcher.search.apps.drawer.drawerDragTile
import de.mm20.launcher2.ui.launcher.search.apps.drawer.wiggle
import de.mm20.launcher2.ui.launcher.search.common.grid.GridItem
import de.mm20.launcher2.ui.launcher.search.common.grid.GridResults
import de.mm20.launcher2.ui.launcher.search.common.list.ListItem
import de.mm20.launcher2.ui.launcher.search.common.list.ListResults
import de.mm20.launcher2.ui.layout.BottomReversed
import de.mm20.launcher2.ui.locals.LocalGridSettings

fun LazyListScope.AppResults(
    onProfileSelected: (Profile) -> Unit = {},
    profiles: List<Profile> = emptyList(),
    selectedProfile: Profile? = null,
    profileStates: Map<Profile.Type, Profile.State> = emptyMap(),
    showProfileLockControls: Boolean = false,
    onProfileLockChange: ((Profile, Boolean) -> Unit)? = null,
    apps: List<Application>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    highlightedItem: Application? = null,
    columns: Int,
    reverse: Boolean,
    showList: Boolean,
    /**
     * Apps arranged according to the custom drawer layout (including folders). If null, [apps]
     * are shown as they are. Only used in grid mode.
     */
    drawerItems: List<DrawerItem>? = null,
    isRearranging: Boolean = false,
    dragState: DrawerDragState? = null,
    onOpenFolder: (folderId: String) -> Unit = {},
    onStopRearranging: () -> Unit = {},
    onResetLayout: () -> Unit = {},
) {

    val profileIndex by derivedStateOf {
        profiles.indexOf(selectedProfile).takeIf { it >= 0 } ?: 0
    }
    val selectedProfileType by derivedStateOf {
        selectedProfile?.type ?: Profile.Type.Personal
    }

    val isProfileLocked by derivedStateOf {
        profileStates[selectedProfileType]?.locked == true
    }

    val profileHeader = if (profiles.size > 1) {
         @Composable {
            Column(
                verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top,
            ) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = profileIndex,
                    containerColor = Color.Transparent,
                    edgePadding = 16.dp,
                    divider = {}
                ) {
                    for (profile in profiles) {
                        val selected = profile.type == selectedProfileType
                        LeadingIconTab(
                            selected = selected,
                            text = {
                                Text(
                                    when (profile.type) {
                                        Profile.Type.Personal -> stringResource(R.string.apps_profile_main)
                                        Profile.Type.Work -> stringResource(R.string.apps_profile_work)
                                        Profile.Type.Private -> stringResource(R.string.apps_profile_private)
                                    }
                                )
                            },
                            icon = {
                                when (profile.type) {
                                    Profile.Type.Personal -> Icon(
                                        painterResource(if (selected) R.drawable.person_24px_filled else R.drawable.person_24px),
                                        contentDescription = null
                                    )

                                    Profile.Type.Work -> Icon(
                                        painterResource(if (selected) R.drawable.enterprise_24px_filled else R.drawable.enterprise_24px),
                                        contentDescription = null
                                    )

                                    Profile.Type.Private -> Icon(
                                        painterResource(if (selected) R.drawable.encrypted_24px_filled else R.drawable.encrypted_24px),
                                        contentDescription = null
                                    )
                                }
                            },
                            onClick = {
                                onProfileSelected(profile)
                            }
                        )
                    }
                }

                if (!showList || isProfileLocked) {
                    HorizontalDivider()
                }

                if (selectedProfile != null && selectedProfile?.type != Profile.Type.Personal) {
                    if (isProfileLocked) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    MaterialTheme.shapes.small
                                )
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainer,
                                    MaterialTheme.shapes.small
                                )
                                .padding(vertical = 64.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                painterResource(if (selectedProfileType == Profile.Type.Work) R.drawable.enterprise_off_48px else R.drawable.lock_48px),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                stringResource(
                                    if (selectedProfileType == Profile.Type.Work) R.string.profile_work_profile_state_locked
                                    else R.string.profile_private_profile_state_locked
                                ),
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (showProfileLockControls) {
                                Button(
                                    modifier = Modifier.padding(top = 32.dp),
                                    onClick = {
                                        onProfileLockChange?.invoke(
                                            selectedProfile!!,
                                            false
                                        )
                                    },
                                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                                ) {
                                    Icon(
                                        painterResource(if (selectedProfileType == Profile.Type.Work) R.drawable.enterprise_20px else R.drawable.lock_open_20px),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(end = ButtonDefaults.IconSpacing)
                                            .size(ButtonDefaults.IconSize)
                                    )
                                    Text(
                                        stringResource(
                                            if (selectedProfileType == Profile.Type.Work) R.string.profile_work_profile_action_unlock
                                            else R.string.profile_private_profile_action_unlock
                                        )
                                    )
                                }
                            }
                        }
                    } else if (showProfileLockControls) {
                        FilledTonalButton(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            onClick = {
                                onProfileLockChange?.invoke(
                                    selectedProfile,
                                    true
                                )
                            },
                            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                        ) {
                            Icon(
                                painterResource(if (selectedProfileType == Profile.Type.Work) R.drawable.enterprise_off_20px else R.drawable.lock_20px),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = ButtonDefaults.IconSpacing)
                                    .size(ButtonDefaults.IconSize)
                            )
                            Text(
                                stringResource(
                                    if (selectedProfileType == Profile.Type.Work) R.string.profile_work_profile_action_lock
                                    else R.string.profile_private_profile_action_lock
                                )
                            )
                        }
                    }
                }
            }
        }
    } else null

    val before: (@Composable () -> Unit)? = if (isRearranging && drawerItems != null && !showList) {
        @Composable {
            Column(
                verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top,
            ) {
                profileHeader?.invoke()
                RearrangeHeader(
                    onDone = onStopRearranging,
                    onReset = onResetLayout,
                )
            }
        }
    } else profileHeader

    if (showList) {
        ListResults(
            key = "apps",
            items = if (isProfileLocked) emptyList() else apps,
            before = before?.let { { it() } },
            selectedIndex = selectedIndex,
            itemContent = { app, showDetails, index ->
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth(),
                    item = app,
                    showDetails = showDetails,
                    onShowDetails = { onSelect(if(it) index else -1) },
                    highlight = highlightedItem?.key == app.key
                )
            },
            reverse = reverse,
        )
    } else if (drawerItems != null) {
        GridResults(
            key = "apps",
            items = if (isProfileLocked) emptyList() else drawerItems,
            before = before,
            itemContent = { item ->
                DrawerTile(
                    item = item,
                    isRearranging = isRearranging,
                    dragState = dragState,
                    highlight = item is DrawerItem.App && item.app.key == highlightedItem?.key,
                    onOpenFolder = onOpenFolder,
                )
            },
            reverse = reverse,
            columns = columns,
        )
    } else {
        GridResults(
            key = "apps",
            items = if (isProfileLocked) emptyList() else apps,
            before = before,
            itemContent = {
                GridItem(
                    item = it,
                    showLabels = LocalGridSettings.current.showLabels,
                    highlight = it.key == highlightedItem?.key
                )
            },
            reverse = reverse,
            columns = columns,
        )
    }

}

@Composable
private fun DrawerTile(
    item: DrawerItem,
    isRearranging: Boolean,
    dragState: DrawerDragState?,
    highlight: Boolean,
    onOpenFolder: (folderId: String) -> Unit,
) {
    val isDragged = dragState?.draggedKey == item.key
    val isMergeTarget = dragState?.mergeTargetKey == item.key
    Box(
        modifier = Modifier
            .drawerDragTile(dragState, item.key)
            // The dragged tile is drawn as a floating copy, keep its slot empty
            .graphicsLayer { alpha = if (isDragged) 0f else 1f }
            .wiggle(isRearranging && !isDragged, item.key)
    ) {
        if (isMergeTarget) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(2.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        MaterialTheme.shapes.medium,
                    )
            )
        }
        when (item) {
            is DrawerItem.App -> GridItem(
                item = item.app,
                showLabels = LocalGridSettings.current.showLabels,
                highlight = highlight,
                enabled = !isRearranging,
            )

            is DrawerItem.Folder -> DrawerFolderTile(
                folder = item,
                onClick = { onOpenFolder(item.folder.id) },
            )
        }
    }
}

@Composable
private fun RearrangeHeader(
    onDone: () -> Unit,
    onReset: () -> Unit,
) {
    var showResetDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.app_drawer_rearrange_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        TextButton(onClick = { showResetDialog = true }) {
            Text(stringResource(R.string.app_drawer_reset))
        }
        FilledTonalButton(onClick = onDone) {
            Text(stringResource(R.string.action_done))
        }
    }
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.app_drawer_reset_title)) },
            text = { Text(stringResource(R.string.app_drawer_reset_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    onReset()
                }) {
                    Text(stringResource(R.string.app_drawer_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
