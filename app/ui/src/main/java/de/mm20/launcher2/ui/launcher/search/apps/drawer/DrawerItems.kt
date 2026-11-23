package de.mm20.launcher2.ui.launcher.search.apps.drawer

import de.mm20.launcher2.preferences.drawer.AppDrawerEntry
import de.mm20.launcher2.preferences.drawer.AppDrawerLayout
import de.mm20.launcher2.search.Application
import java.util.UUID

/**
 * An item as it is displayed in the app drawer grid.
 */
sealed interface DrawerItem {
    val key: String

    data class App(val app: Application) : DrawerItem {
        override val key: String
            get() = app.key
    }

    data class Folder(
        val folder: AppDrawerEntry.Folder,
        val apps: List<Application>,
    ) : DrawerItem {
        override val key: String
            get() = folder.entryKey
    }
}

/**
 * Arrange [apps] (in their default order) according to [layout].
 * Entries that reference apps that are not in [apps] (uninstalled, hidden, other profile) are
 * skipped; folders without any app in [apps] are not shown. Apps that are not referenced by the
 * layout are appended in their default order.
 */
fun buildDrawerItems(apps: List<Application>, layout: AppDrawerLayout): List<DrawerItem> {
    if (layout.entries.isEmpty()) return apps.map { DrawerItem.App(it) }
    val byKey = apps.associateBy { it.key }
    val used = HashSet<String>(apps.size)
    val result = ArrayList<DrawerItem>(apps.size)
    for (entry in layout.entries) {
        when (entry) {
            is AppDrawerEntry.App -> {
                val app = byKey[entry.key] ?: continue
                if (used.add(app.key)) result += DrawerItem.App(app)
            }

            is AppDrawerEntry.Folder -> {
                val folderApps = entry.apps.mapNotNull { byKey[it] }.filter { used.add(it.key) }
                if (folderApps.isNotEmpty()) result += DrawerItem.Folder(entry, folderApps)
            }
        }
    }
    for (app in apps) {
        if (app.key !in used) result += DrawerItem.App(app)
    }
    return result
}

/**
 * Make sure every app in [allAppKeys] is explicitly part of the layout, so that the current
 * (partially default) order becomes the stored order.
 */
fun AppDrawerLayout.materialize(allAppKeys: List<String>): AppDrawerLayout {
    val referenced = referencedAppKeys
    val missing = allAppKeys.filter { it !in referenced }.distinct()
    if (missing.isEmpty()) return this
    return copy(entries = entries + missing.map { AppDrawerEntry.App(it) })
}

/**
 * Move the entry [draggedKey] to the position of [targetKey].
 */
fun AppDrawerLayout.move(draggedKey: String, targetKey: String): AppDrawerLayout {
    val from = entries.indexOfFirst { it.entryKey == draggedKey }
    val to = entries.indexOfFirst { it.entryKey == targetKey }
    if (from == -1 || to == -1 || from == to) return this
    val list = entries.toMutableList()
    val entry = list.removeAt(from)
    list.add(to, entry)
    return copy(entries = list)
}

fun AppDrawerLayout.canMerge(draggedKey: String, targetKey: String): Boolean {
    if (draggedKey == targetKey) return false
    val dragged = entries.find { it.entryKey == draggedKey }
    val target = entries.find { it.entryKey == targetKey }
    return dragged is AppDrawerEntry.App && target != null
}

/**
 * Drop the app [draggedKey] onto [targetKey]. If the target is a folder, the app is added to
 * that folder. If it is an app, a new folder containing both apps is created in its place.
 * @return the new layout and the id of the folder the app was put into.
 */
fun AppDrawerLayout.merge(
    draggedKey: String,
    targetKey: String,
    newFolderName: String,
): Pair<AppDrawerLayout, String?> {
    if (!canMerge(draggedKey, targetKey)) return this to null
    val target = entries.first { it.entryKey == targetKey }
    val remaining = entries.filter { it.entryKey != draggedKey }
    return when (target) {
        is AppDrawerEntry.Folder -> {
            val newFolder = target.copy(apps = target.apps.filter { it != draggedKey } + draggedKey)
            copy(entries = remaining.map { if (it.entryKey == targetKey) newFolder else it }) to target.id
        }

        is AppDrawerEntry.App -> {
            val newFolder = AppDrawerEntry.Folder(
                id = UUID.randomUUID().toString(),
                name = newFolderName,
                apps = listOf(target.key, draggedKey),
            )
            copy(entries = remaining.map { if (it.entryKey == targetKey) newFolder else it }) to newFolder.id
        }
    }
}

private inline fun AppDrawerLayout.updateFolder(
    id: String,
    block: (AppDrawerEntry.Folder) -> List<AppDrawerEntry>,
): AppDrawerLayout {
    return copy(entries = entries.flatMap {
        if (it is AppDrawerEntry.Folder && it.id == id) block(it) else listOf(it)
    })
}

fun AppDrawerLayout.renameFolder(id: String, name: String): AppDrawerLayout {
    return updateFolder(id) { listOf(it.copy(name = name)) }
}

/**
 * Take [appKey] out of the folder and place it right after the folder.
 * Folders that are left with a single app are dissolved.
 */
fun AppDrawerLayout.removeFromFolder(id: String, appKey: String): AppDrawerLayout {
    return updateFolder(id) { folder ->
        val apps = folder.apps.filter { it != appKey }
        if (apps.size <= 1) {
            apps.map { AppDrawerEntry.App(it) } + AppDrawerEntry.App(appKey)
        } else {
            listOf(folder.copy(apps = apps), AppDrawerEntry.App(appKey))
        }
    }
}

/**
 * Replace the folder with its apps.
 */
fun AppDrawerLayout.dissolveFolder(id: String): AppDrawerLayout {
    return updateFolder(id) { folder -> folder.apps.map { AppDrawerEntry.App(it) } }
}

fun AppDrawerLayout.moveInFolder(id: String, fromKey: String, toKey: String): AppDrawerLayout {
    return updateFolder(id) { folder ->
        val from = folder.apps.indexOf(fromKey)
        val to = folder.apps.indexOf(toKey)
        if (from == -1 || to == -1 || from == to) return@updateFolder listOf(folder)
        val apps = folder.apps.toMutableList()
        apps.add(to, apps.removeAt(from))
        listOf(folder.copy(apps = apps))
    }
}
