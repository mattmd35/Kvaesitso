package de.mm20.launcher2.ui.launcher.search.apps.drawer

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.drawer.AppDrawerLayout
import de.mm20.launcher2.preferences.drawer.AppDrawerLayoutStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Holds the custom app drawer layout (order + folders) and the rearrange mode state.
 * Activity scoped, so that item popups (which are rendered in an overlay) can access it.
 */
class AppDrawerVM : ViewModel(), KoinComponent {
    private val store: AppDrawerLayoutStore by inject()

    private val _layout = MutableStateFlow(AppDrawerLayout())

    /**
     * The current layout. Edits are applied here immediately (important while dragging) and
     * persisted in the background.
     */
    val layout: StateFlow<AppDrawerLayout> = _layout

    /**
     * True while there are edits that haven't been written to the store yet. Store emissions
     * are ignored in that time, so that the UI doesn't jump back to an older state.
     */
    private var hasUnsavedChanges = false
    private val writeQueue = Channel<AppDrawerLayout>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            store.layout.collect {
                if (!hasUnsavedChanges) _layout.value = it
            }
        }
        viewModelScope.launch {
            for (layout in writeQueue) {
                withContext(NonCancellable) {
                    store.save(layout)
                }
                if (_layout.value == layout) hasUnsavedChanges = false
            }
        }
    }

    private fun setLayout(layout: AppDrawerLayout) {
        hasUnsavedChanges = true
        _layout.value = layout
        writeQueue.trySend(layout)
    }

    /**
     * Whether the app drawer grid is currently shown, i.e. whether rearranging is possible.
     */
    val canEdit = mutableStateOf(false)
    val isEditing = mutableStateOf(false)
    val openFolderId = mutableStateOf<String?>(null)

    /**
     * Keys of all apps (of all profiles) in their default order. Required to turn the default
     * order into an explicit one on the first edit.
     */
    var allAppKeys: List<String> = emptyList()

    fun startEditing() {
        if (!canEdit.value) return
        closeFolder()
        isEditing.value = true
    }

    fun stopEditing() {
        isEditing.value = false
    }

    fun openFolder(id: String) {
        openFolderId.value = id
    }

    fun closeFolder() {
        openFolderId.value = null
    }

    private fun edit(block: (AppDrawerLayout) -> AppDrawerLayout) {
        val current = layout.value
        val updated = block(current.materialize(allAppKeys))
        if (updated == current) return
        setLayout(updated)
    }

    fun move(draggedKey: String, targetKey: String) {
        edit { it.move(draggedKey, targetKey) }
    }

    fun merge(draggedKey: String, targetKey: String, newFolderName: String) {
        edit { it.merge(draggedKey, targetKey, newFolderName).first }
    }

    fun renameFolder(id: String, name: String) {
        edit { it.renameFolder(id, name) }
    }

    fun removeFromFolder(id: String, appKey: String) {
        edit { it.removeFromFolder(id, appKey) }
    }

    fun dissolveFolder(id: String) {
        if (openFolderId.value == id) closeFolder()
        edit { it.dissolveFolder(id) }
    }

    fun moveInFolder(id: String, fromKey: String, toKey: String) {
        edit { it.moveInFolder(id, fromKey, toKey) }
    }

    /**
     * Go back to the default alphabetical order without folders.
     */
    fun reset() {
        closeFolder()
        setLayout(AppDrawerLayout())
    }
}
