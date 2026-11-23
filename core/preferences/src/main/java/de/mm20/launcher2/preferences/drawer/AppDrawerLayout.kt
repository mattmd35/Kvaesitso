package de.mm20.launcher2.preferences.drawer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * User defined order of the app drawer.
 *
 * [entries] contains top level entries in display order. Apps that are not referenced anywhere
 * in the layout (for example newly installed apps, or all apps before the user has rearranged
 * anything) are appended after all entries in their default (alphabetical) order.
 */
@Serializable
data class AppDrawerLayout(
    val entries: List<AppDrawerEntry> = emptyList(),
) {
    /**
     * All app keys referenced by this layout, either at top level or inside of a folder.
     */
    val referencedAppKeys: Set<String>
        get() = buildSet {
            for (entry in entries) {
                when (entry) {
                    is AppDrawerEntry.App -> add(entry.key)
                    is AppDrawerEntry.Folder -> addAll(entry.apps)
                }
            }
        }
}

@Serializable
sealed interface AppDrawerEntry {
    /**
     * Unique key of this entry in the layout.
     */
    val entryKey: String

    @Serializable
    @SerialName("app")
    data class App(
        val key: String,
    ) : AppDrawerEntry {
        override val entryKey: String
            get() = key
    }

    @Serializable
    @SerialName("folder")
    data class Folder(
        val id: String,
        val name: String,
        val apps: List<String> = emptyList(),
    ) : AppDrawerEntry {
        override val entryKey: String
            get() = folderKey(id)
    }

    companion object {
        fun folderKey(id: String) = "folder://$id"
    }
}
