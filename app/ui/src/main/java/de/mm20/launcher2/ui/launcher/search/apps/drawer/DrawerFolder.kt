package de.mm20.launcher2.ui.launcher.search.apps.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.LocalIconShape
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.dragndrop.DraggableItem
import de.mm20.launcher2.ui.component.dragndrop.LazyVerticalDragAndDropGrid
import de.mm20.launcher2.ui.component.dragndrop.rememberLazyDragAndDropGridState
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import de.mm20.launcher2.ui.launcher.search.common.grid.GridItem
import de.mm20.launcher2.ui.launcher.search.listItemViewModel
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.overlays.Overlay
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlin.math.absoluteValue

/**
 * Folder icon: up to four app icons in a 2x2 grid on a shaped background.
 */
@Composable
fun FolderIcon(
    apps: List<Application>,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val miniSize = size * 0.36f
    val spacing = size * 0.06f
    Box(
        modifier = modifier
            .size(size)
            .clip(LocalIconShape.current)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            for (row in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    for (col in 0 until 2) {
                        val app = apps.getOrNull(row * 2 + col)
                        if (app != null) {
                            FolderPreviewIcon(app, miniSize)
                        } else {
                            Spacer(Modifier.size(miniSize))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderPreviewIcon(app: Application, size: Dp) {
    val viewModel: SearchableItemVM = listItemViewModel(key = "folder-preview-${app.key}")
    val sizePx = size.toPixels()
    LaunchedEffect(app, sizePx) {
        viewModel.init(app, sizePx.toInt())
    }
    val icon by viewModel.icon.collectAsStateWithLifecycle()
    ShapedLauncherIcon(size = size, icon = { icon })
}

/**
 * A folder in the app drawer grid. Mirrors the layout of [GridItem].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerFolderTile(
    folder: DrawerItem.Folder,
    modifier: Modifier = Modifier,
    showLabels: Boolean = LocalGridSettings.current.showLabels,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .padding(4.dp)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    )
                } else Modifier
            ) then if (!showLabels) Modifier.aspectRatio(1f) else Modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FolderIcon(
            apps = folder.apps,
            size = LocalGridSettings.current.iconSize.dp,
            modifier = Modifier.padding(4.dp),
        )
        if (showLabels) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                text = folder.folder.name,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/**
 * Gently rotate back and forth while the drawer is being rearranged.
 */
@Composable
fun Modifier.wiggle(enabled: Boolean, seed: Any): Modifier {
    if (!enabled) return this
    val transition = rememberInfiniteTransition(label = "wiggle")
    val duration = remember(seed) { 260 + (seed.hashCode().absoluteValue % 80) }
    val rotation by transition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            tween(duration, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "wiggle",
    )
    return this.graphicsLayer { rotationZ = rotation }
}

/**
 * Opened folder, shown as an overlay on top of the launcher.
 */
@Composable
fun FolderOverlay(
    folder: DrawerItem.Folder,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onMove: (fromKey: String, toKey: String) -> Unit,
    onRemove: (appKey: String) -> Unit,
    onDissolve: () -> Unit,
) {
    // The overlay content is only registered once, so everything it uses must be read
    // through updated state to reflect changes.
    val currentFolder by rememberUpdatedState(folder)
    val dismiss by rememberUpdatedState(onDismiss)
    val rename by rememberUpdatedState(onRename)
    val move by rememberUpdatedState(onMove)
    val remove by rememberUpdatedState(onRemove)
    val dissolve by rememberUpdatedState(onDissolve)

    // Close the folder when an app has been launched from it
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) dismiss()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Overlay {
        var renaming by remember { mutableStateOf(false) }
        var arranging by remember { mutableStateOf(false) }
        var name by remember { mutableStateOf(currentFolder.folder.name) }

        fun commitName() {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty() && trimmed != currentFolder.folder.name) rename(trimmed)
            renaming = false
        }

        BackHandler {
            if (renaming) commitName() else dismiss()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (renaming) commitName()
                        dismiss()
                    }
                }
                .systemBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
            ) {
                LauncherCard(
                    modifier = Modifier
                        .padding(16.dp)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        // Don't dismiss when tapping inside of the card
                        .pointerInput(Unit) { detectTapGestures { } },
                    elevation = 8.dp,
                    backgroundOpacity = MaterialTheme.transparency.elevatedSurface,
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        FolderHeader(
                            name = if (renaming) name else currentFolder.folder.name,
                            renaming = renaming,
                            arranging = arranging,
                            onNameChange = { name = it },
                            onStartRename = {
                                name = currentFolder.folder.name
                                renaming = true
                            },
                            onCommitRename = { commitName() },
                            onArrangingChange = { arranging = it },
                        )
                        FolderGrid(
                            apps = currentFolder.apps,
                            arranging = arranging,
                            onMove = { from, to -> move(from, to) },
                            onRemove = { remove(it) },
                        )
                        AnimatedVisibility(arranging) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.app_drawer_folder_arrange_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { dissolve() }) {
                                    Text(stringResource(R.string.app_drawer_folder_ungroup))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderHeader(
    name: String,
    renaming: Boolean,
    arranging: Boolean,
    onNameChange: (String) -> Unit,
    onStartRename: () -> Unit,
    onCommitRename: () -> Unit,
    onArrangingChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (renaming) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                label = { Text(stringResource(R.string.app_drawer_folder_name)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommitRename() }),
            )
            IconButton(onClick = onCommitRename) {
                Icon(
                    painterResource(R.drawable.check_24px),
                    contentDescription = stringResource(R.string.app_drawer_folder_rename)
                )
            }
        } else {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) { detectTapGestures { onStartRename() } },
            )
            IconButton(onClick = onStartRename) {
                Icon(
                    painterResource(R.drawable.edit_24px),
                    contentDescription = stringResource(R.string.app_drawer_folder_rename)
                )
            }
            FilledIconToggleButton(
                checked = arranging,
                onCheckedChange = onArrangingChange,
            ) {
                Icon(
                    painterResource(R.drawable.drag_indicator_24px),
                    contentDescription = stringResource(R.string.app_drawer_rearrange)
                )
            }
        }
    }
}

@Composable
private fun FolderGrid(
    apps: List<Application>,
    arranging: Boolean,
    onMove: (fromKey: String, toKey: String) -> Unit,
    onRemove: (appKey: String) -> Unit,
) {
    val columns = LocalGridSettings.current.columnCount
    val showLabels = LocalGridSettings.current.showLabels
    val gridModifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 420.dp)

    if (arranging) {
        val move by rememberUpdatedState(onMove)
        val dragState = rememberLazyDragAndDropGridState(
            onItemMove = { from, to ->
                val fromKey = from.key as? String
                val toKey = to.key as? String
                if (fromKey != null && toKey != null) move(fromKey, toKey)
            }
        )
        LazyVerticalDragAndDropGrid(
            state = dragState,
            columns = GridCells.Fixed(columns),
            modifier = gridModifier,
        ) {
            items(apps, key = { it.key }) { app ->
                DraggableItem(state = dragState, key = app.key) { isDragged ->
                    Box(modifier = Modifier.wiggle(!isDragged, app.key)) {
                        GridItem(item = app, showLabels = showLabels, enabled = false)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { onRemove(app.key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.close_20px),
                                contentDescription = stringResource(R.string.app_drawer_folder_remove_app),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = gridModifier,
        ) {
            items(apps, key = { it.key }) { app ->
                GridItem(item = app, showLabels = showLabels)
            }
        }
    }
}
