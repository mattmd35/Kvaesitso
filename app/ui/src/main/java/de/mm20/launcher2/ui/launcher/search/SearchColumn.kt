package de.mm20.launcher2.ui.launcher.search

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.profiles.Profile
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.Article
import de.mm20.launcher2.search.CalendarEvent
import de.mm20.launcher2.search.Contact
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.Website
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.launcher.search.apps.drawer.AppDrawerVM
import de.mm20.launcher2.ui.launcher.search.apps.drawer.DrawerDragState
import de.mm20.launcher2.ui.launcher.search.apps.drawer.DrawerFolderTile
import de.mm20.launcher2.ui.launcher.search.apps.drawer.DrawerItem
import de.mm20.launcher2.ui.launcher.search.apps.drawer.FolderOverlay
import de.mm20.launcher2.ui.launcher.search.apps.drawer.buildDrawerItems
import de.mm20.launcher2.ui.launcher.search.apps.drawer.drawerDragGestures
import de.mm20.launcher2.ui.launcher.search.apps.drawer.rememberDrawerDragState
import de.mm20.launcher2.ui.launcher.search.common.grid.GridItem
import de.mm20.launcher2.ui.launcher.search.apps.AppResults
import de.mm20.launcher2.ui.launcher.search.calculator.CalculatorResults
import de.mm20.launcher2.ui.launcher.search.calendar.CalendarResults
import de.mm20.launcher2.ui.launcher.search.contacts.ContactResults
import de.mm20.launcher2.ui.launcher.search.favorites.SearchFavorites
import de.mm20.launcher2.ui.launcher.search.favorites.SearchFavoritesVM
import de.mm20.launcher2.ui.launcher.search.files.FileResults
import de.mm20.launcher2.ui.launcher.search.filters.SearchFilters
import de.mm20.launcher2.ui.launcher.search.location.LocationResults
import de.mm20.launcher2.ui.launcher.search.shortcut.ShortcutResults
import de.mm20.launcher2.ui.launcher.search.unitconverter.UnitConverterResults
import de.mm20.launcher2.ui.launcher.search.website.WebsiteResults
import de.mm20.launcher2.ui.launcher.search.wikipedia.ArticleResults
import de.mm20.launcher2.ui.launcher.sheets.HiddenItemsSheet
import de.mm20.launcher2.ui.launcher.sheets.LocalBottomSheetManager
import de.mm20.launcher2.ui.locals.LocalGridSettings
import de.mm20.launcher2.ui.theme.transparency.transparency
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.zip

@Composable
fun SearchColumn(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(0.dp),
    state: LazyListState = rememberLazyListState(),
    reverse: Boolean = false,
    userScrollEnabled: Boolean = true,
    onHideKeyboard: () -> Unit = {},
) {

    val columns = LocalGridSettings.current.columnCount
    val showList = LocalGridSettings.current.showList
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val viewModel: SearchVM = viewModel()

    val favoritesVM: SearchFavoritesVM = viewModel()
    val favorites by favoritesVM.favorites.collectAsState(emptyList())

    val hideFavs by viewModel.hideFavorites
    val favoritesEnabled by viewModel.favoritesEnabled.collectAsState(false)
    val allAppsEnabled by viewModel.allAppsEnabled.collectAsState(false)

    val apps = viewModel.appResults
    val workApps = viewModel.workAppResults
    val privateApps = viewModel.privateSpaceAppResults
    val profiles by viewModel.profiles.collectAsState(emptyList())
    val profileStates by viewModel.profileStates.collectAsState(emptyMap())

    val appShortcuts = viewModel.appShortcutResults
    val contacts = viewModel.contactResults
    val files = viewModel.fileResults
    val events = viewModel.calendarResults
    val unitConverter = viewModel.unitConverterResults
    val calculator = viewModel.calculatorResults
    val wikipedia = viewModel.articleResults
    val locations = viewModel.locationResults
    val website = viewModel.websiteResults
    val hiddenResults = viewModel.hiddenResults

    val bestMatch by viewModel.bestMatch

    val query by viewModel.searchQuery
    val isSearchEmpty by viewModel.isSearchEmpty

    val missingCalendarPermission by viewModel.missingCalendarPermission.collectAsState(false)
    val missingShortcutsPermission by viewModel.missingAppShortcutPermission.collectAsState(false)
    val missingContactsPermission by viewModel.missingContactsPermission.collectAsState(false)
    val missingLocationPermission by viewModel.missingLocationPermission.collectAsState(false)
    val missingFilesPermission by viewModel.missingFilesPermission.collectAsState(false)
    val hasProfilesPermission by viewModel.hasProfilesPermission.collectAsState(false)

    val pinnedTags by favoritesVM.pinnedTags.collectAsState(emptyList())
    val selectedTag by favoritesVM.selectedTag.collectAsState(null)
    val compactTags by favoritesVM.compactTags.collectAsState(false)
    val favoritesEditButton by favoritesVM.showEditButton.collectAsState(false)
    val favoritesTagsExpanded by favoritesVM.tagsExpanded.collectAsState(false)

    val expandedCategory: SearchCategory? by viewModel.expandedCategory

    var selectedAppProfileIndex by remember { mutableIntStateOf(-1) }
    var selectedAppIndex: Int by remember(query) { mutableIntStateOf(-1) }
    var selectedContactIndex: Int by remember(query) { mutableIntStateOf(-1) }
    var selectedFileIndex: Int by remember(query) { mutableIntStateOf(-1) }
    var selectedCalendarIndex: Int by remember(query) { mutableIntStateOf(-1) }
    var selectedLocationIndex: Int by remember(query) { mutableIntStateOf(-1) }
    var selectedShortcutIndex: Int by remember(query) { mutableIntStateOf(-1) }
    var selectedArticleIndex: Int by remember(query) { mutableIntStateOf(-1) }
    var selectedWebsiteIndex: Int by remember(query) { mutableIntStateOf(-1) }

    val showFilters by viewModel.showFilters

    val visibleProfiles = remember(profiles, profileStates) {
        profiles.filter { profileStates[it.type]?.hidden == false }
    }
    val showProfileTabs = isSearchEmpty && profiles.size > 1 && allAppsEnabled
    val selectedProfile =
        visibleProfiles.getOrNull(selectedAppProfileIndex) ?: visibleProfiles.firstOrNull()
    val drawerApps = if (showProfileTabs) {
        when (selectedProfile?.type) {
            Profile.Type.Private -> privateApps
            Profile.Type.Work -> workApps
            else -> apps
        }
    } else apps

    // Custom app drawer layout (order + folders)
    val drawerVM: AppDrawerVM = viewModel()
    val drawerLayout by drawerVM.layout.collectAsState()
    val isRearranging by drawerVM.isEditing
    val drawerGridShown = isSearchEmpty && allAppsEnabled && !showList && !showFilters
    val drawerItems by remember(drawerApps, drawerLayout, drawerGridShown) {
        derivedStateOf {
            if (drawerGridShown) buildDrawerItems(drawerApps, drawerLayout) else emptyList()
        }
    }
    val allAppKeys by remember {
        derivedStateOf { (apps + workApps + privateApps).map { it.key } }
    }
    SideEffect {
        drawerVM.allAppKeys = allAppKeys
    }
    LaunchedEffect(drawerGridShown) {
        drawerVM.canEdit.value = drawerGridShown
        if (!drawerGridShown) drawerVM.stopEditing()
    }
    DisposableEffect(Unit) {
        onDispose {
            drawerVM.canEdit.value = false
            drawerVM.stopEditing()
            drawerVM.closeFolder()
        }
    }
    BackHandler(isRearranging) {
        drawerVM.stopEditing()
    }

    val density = LocalDensity.current
    val defaultFolderName = stringResource(R.string.app_drawer_folder_default_name)
    val dragState = rememberDrawerDragState(state)
    SideEffect {
        dragState.onMove = { dragged, target -> drawerVM.move(dragged, target) }
        dragState.onMerge = { dragged, target -> drawerVM.merge(dragged, target, defaultFolderName) }
        dragState.canMerge = { dragged, target ->
            dragged != target && drawerItems.find { it.key == dragged } is DrawerItem.App
        }
        // Outside of rearrange mode, long pressing a folder starts rearranging right away.
        // Long pressing an app shows its popup menu instead.
        dragState.canDrag = { key ->
            drawerVM.isEditing.value || drawerItems.find { it.key == key } is DrawerItem.Folder
        }
        dragState.onDragStarted = { drawerVM.startEditing() }
        dragState.reverse = reverse
        with(density) {
            dragState.scrollEdgeTop = paddingValues.calculateTopPadding().toPx() + 48.dp.toPx()
            dragState.scrollEdgeBottom = paddingValues.calculateBottomPadding().toPx() + 48.dp.toPx()
            dragState.scrollSpeed = 800.dp.toPx()
        }
    }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(profiles) {
        var previousState: Profile.State? = null
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.profileStates.map { it[Profile.Type.Private] }.collect {

                if (it == previousState) return@collect
                previousState = it

                viewModel.search("")

                if (it?.locked == false && !it.hidden) {
                    val index = profiles.indexOfFirst { p -> p.type == Profile.Type.Private }
                    if (index != -1) {
                        selectedAppProfileIndex = index
                    }
                }
            }
        }
    }

    AnimatedContent(
        showFilters,
        modifier = modifier.padding(horizontal = 8.dp),
    ) {
        if (it) {
            BackHandler {
                viewModel.showFilters.value = false
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = if (reverse) Alignment.BottomCenter else Alignment.TopCenter,
            ) {
                SearchFilters(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                            MaterialTheme.shapes.medium
                        )
                        .padding(12.dp),
                    filters = viewModel.filters.value,
                    onFiltersChange = {
                        viewModel.setFilters(it)
                    }
                )
            }
        } else {
          Box {
            LazyColumn(
                state = state,
                userScrollEnabled = userScrollEnabled && dragState.draggedKey == null,
                contentPadding = paddingValues,
                reverseLayout = reverse,
                modifier = Modifier.drawerDragGestures(
                    dragState,
                    enabled = drawerGridShown,
                    hapticFeedback = hapticFeedback,
                ),
            ) {
                if (!hideFavs && favoritesEnabled) {
                    SearchFavorites(
                        favorites = favorites,
                        selectedTag = selectedTag,
                        pinnedTags = pinnedTags,
                        tagsExpanded = favoritesTagsExpanded,
                        onSelectTag = { favoritesVM.selectTag(it) },
                        reverse = reverse,
                        onExpandTags = {
                            favoritesVM.setTagsExpanded(it)
                        },
                        compactTags = compactTags,
                        editButton = favoritesEditButton
                    )
                } else {
                    // Empty item to maintain scroll position
                    item(key = "favorites") {
                    }
                }

                if (showProfileTabs) {
                    AppResults(
                        apps = drawerApps,
                        drawerItems = if (drawerGridShown) drawerItems else null,
                        isRearranging = isRearranging,
                        dragState = dragState,
                        onOpenFolder = { drawerVM.openFolder(it) },
                        onStopRearranging = { drawerVM.stopEditing() },
                        onResetLayout = { drawerVM.reset() },
                        highlightedItem = bestMatch as? Application,
                        profiles = visibleProfiles,
                        profileStates = profileStates,
                        selectedProfile = visibleProfiles.getOrNull(selectedAppProfileIndex),
                        onProfileSelected = {
                            selectedAppProfileIndex = visibleProfiles.indexOf(it)
                            onHideKeyboard()
                        },
                        onProfileLockChange = { p, l ->
                            viewModel.setProfileLock(p, l)
                        },
                        columns = columns,
                        reverse = reverse,
                        showProfileLockControls = hasProfilesPermission,
                        showList = showList,
                        selectedIndex = selectedAppIndex,
                        onSelect = { selectedAppIndex = it },
                    )
                } else if (!isSearchEmpty || allAppsEnabled) {
                    AppResults(
                        apps = apps,
                        drawerItems = if (drawerGridShown) drawerItems else null,
                        isRearranging = isRearranging,
                        dragState = dragState,
                        onOpenFolder = { drawerVM.openFolder(it) },
                        onStopRearranging = { drawerVM.stopEditing() },
                        onResetLayout = { drawerVM.reset() },
                        highlightedItem = bestMatch as? Application,
                        columns = columns,
                        reverse = reverse,
                        showList = showList,
                        selectedIndex = selectedAppIndex,
                        onSelect = { selectedAppIndex = it },
                    )
                }

                if (!isSearchEmpty) {

                    ShortcutResults(
                        shortcuts = appShortcuts,
                        missingPermission = missingShortcutsPermission,
                        onPermissionRequest = {
                            viewModel.requestAppShortcutPermission(context as AppCompatActivity)
                        },
                        onPermissionRequestRejected = {
                            viewModel.disableAppShortcutSearch()
                        },
                        reverse = reverse,
                        selectedIndex = selectedShortcutIndex,
                        onSelect = { selectedShortcutIndex = it },
                        highlightedItem = bestMatch as? AppShortcut,
                        truncate = expandedCategory != SearchCategory.Shortcuts,
                        onShowAll = {
                            viewModel.expandCategory(SearchCategory.Shortcuts)
                        },
                    )

                    UnitConverterResults(
                        converters = unitConverter,
                        reverse = reverse,
                        truncate = expandedCategory != SearchCategory.UnitConverter,
                        onShowAll = {
                            viewModel.expandCategory(SearchCategory.UnitConverter)
                        }
                    )

                    CalculatorResults(
                        calculator,
                        reverse = reverse
                    )

                    CalendarResults(
                        events = events,
                        missingPermission = missingCalendarPermission,
                        onPermissionRequest = {
                            viewModel.requestCalendarPermission(context as AppCompatActivity)
                        },
                        onPermissionRequestRejected = {
                            viewModel.disableCalendarSearch()
                        },
                        reverse = reverse,
                        selectedIndex = selectedCalendarIndex,
                        onSelect = { selectedCalendarIndex = it },
                        highlightedItem = bestMatch as? CalendarEvent,
                        truncate = expandedCategory != SearchCategory.Calendar,
                        onShowAll = {
                            viewModel.expandCategory(SearchCategory.Calendar)
                        }
                    )

                    ContactResults(
                        contacts = contacts,
                        missingPermission = missingContactsPermission,
                        onPermissionRequest = {
                            viewModel.requestContactsPermission(context as AppCompatActivity)
                        },
                        onPermissionRequestRejected = {
                            viewModel.disableContactsSearch()
                        },
                        reverse = reverse,
                        selectedIndex = selectedContactIndex,
                        onSelect = { selectedContactIndex = it },
                        highlightedItem = bestMatch as? Contact,
                        truncate = expandedCategory != SearchCategory.Contacts,
                        onShowAll = {
                            viewModel.expandCategory(SearchCategory.Contacts)
                        },
                    )

                    LocationResults(
                        locations = locations,
                        missingPermission = missingLocationPermission,
                        onPermissionRequest = {
                            viewModel.requestLocationPermission(context as AppCompatActivity)
                        },
                        onPermissionRequestRejected = {
                            viewModel.disableLocationSearch()
                        },
                        reverse = reverse,
                        selectedIndex = selectedLocationIndex,
                        onSelect = { selectedLocationIndex = it },
                        highlightedItem = bestMatch as? Location,
                        truncate = expandedCategory != SearchCategory.Location,
                        onShowAll = {
                            viewModel.expandCategory(SearchCategory.Location)
                        }
                    )
                    ArticleResults(
                        articles = wikipedia,
                        selectedIndex = selectedArticleIndex,
                        onSelect = { selectedArticleIndex = it },
                        highlightedItem = bestMatch as? Article,
                        reverse = reverse,
                    )
                    WebsiteResults(
                        websites = website,
                        selectedIndex = selectedWebsiteIndex,
                        onSelect = { selectedWebsiteIndex = it },
                        highlightedItem = bestMatch as? Website,
                        reverse = reverse,
                    )
                    FileResults(
                        files = files,
                        onPermissionRequest = {
                            viewModel.requestFilesPermission(context as AppCompatActivity)
                        },
                        onPermissionRequestRejected = {
                            viewModel.disableFilesSearch()
                        },
                        reverse = reverse,
                        highlightedItem = bestMatch as? File,
                        missingPermission = missingFilesPermission,
                        selectedIndex = selectedFileIndex,
                        onSelect = {
                            selectedFileIndex = it
                        },
                        truncate = expandedCategory != SearchCategory.Files,
                        onShowAll = {
                            viewModel.expandCategory(SearchCategory.Files)
                        }
                    )
                }
            }
            DraggedDrawerTile(dragState, drawerItems)
          }
        }

    }

    val openFolderId by drawerVM.openFolderId
    val openFolder = openFolderId?.let { id ->
        drawerItems.find { it is DrawerItem.Folder && it.folder.id == id } as? DrawerItem.Folder
    }
    LaunchedEffect(openFolderId, openFolder == null) {
        // Folder was dissolved or isn't visible anymore
        if (openFolderId != null && openFolder == null) drawerVM.closeFolder()
    }
    if (openFolder != null) {
        val folderId = openFolder.folder.id
        FolderOverlay(
            folder = openFolder,
            onDismiss = { drawerVM.closeFolder() },
            onRename = { drawerVM.renameFolder(folderId, it) },
            onMove = { from, to -> drawerVM.moveInFolder(folderId, from, to) },
            onRemove = { drawerVM.removeFromFolder(folderId, it) },
            onDissolve = { drawerVM.dissolveFolder(folderId) },
        )
    }

    val sheetManager = LocalBottomSheetManager.current
    HiddenItemsSheet(
        expanded = sheetManager.hiddenItemsSheetShown.value,
        items = hiddenResults,
        onDismiss = { sheetManager.dismissHiddenItemsSheet() })
}


fun LazyListScope.SingleResult(
    highlight: Boolean = false,
    content: @Composable (() -> Unit)?
) {
    if (content == null) return
    item {
        LauncherCard(
            modifier = Modifier
                .padding(
                    horizontal = 8.dp,
                    vertical = 4.dp,
                ),
            color = if (highlight) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface.copy(MaterialTheme.transparency.surface)
        ) {
            content()
        }
    }
}

/**
 * Floating copy of the drawer tile that is currently being dragged.
 */
@Composable
private fun DraggedDrawerTile(
    dragState: DrawerDragState,
    drawerItems: List<DrawerItem>,
) {
    val key = dragState.draggedKey ?: return
    val item = drawerItems.find { it.key == key } ?: return
    val density = LocalDensity.current
    val size = dragState.draggedTileSize
    Box(
        modifier = Modifier
            .zIndex(1f)
            .offset { (dragState.draggedTilePosition - dragState.containerOffset).round() }
            .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
            .graphicsLayer {
                scaleX = 1.1f
                scaleY = 1.1f
            }
    ) {
        when (item) {
            is DrawerItem.App -> GridItem(
                item = item.app,
                showLabels = LocalGridSettings.current.showLabels,
                enabled = false,
            )

            is DrawerItem.Folder -> DrawerFolderTile(folder = item)
        }
    }
}
