package de.mm20.launcher2.ui.settings.wallpaper

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.wallpaper.WallpaperCarousel
import de.mm20.launcher2.wallpaper.WallpaperCarouselData
import de.mm20.launcher2.wallpaper.WallpaperSchedule
import de.mm20.launcher2.wallpaper.WallpaperTarget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class WallpaperCarouselSettingsScreenVM : ViewModel(), KoinComponent {
    private val carousel: WallpaperCarousel by inject()

    val data = carousel.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isImporting = mutableStateOf(false)

    fun getFile(name: String): File = carousel.getFile(name)

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            isImporting.value = true
            try {
                carousel.addImages(uris)
            } finally {
                isImporting.value = false
            }
        }
    }

    fun remove(name: String) = viewModelScope.launch { carousel.remove(name) }
    fun show(name: String) = viewModelScope.launch { carousel.show(name) }
    fun next() = viewModelScope.launch { carousel.next() }
    fun setEnabled(enabled: Boolean) = viewModelScope.launch { carousel.setEnabled(enabled) }
    fun setInterval(minutes: Int) = viewModelScope.launch { carousel.setInterval(minutes) }
    fun setSchedule(schedule: WallpaperSchedule) = viewModelScope.launch { carousel.setSchedule(schedule) }
    fun addTime(minute: Int) = viewModelScope.launch { carousel.addTime(minute) }
    fun removeTime(minute: Int) = viewModelScope.launch { carousel.removeTime(minute) }
    fun setTarget(target: WallpaperTarget) = viewModelScope.launch { carousel.setTarget(target) }
    fun setShuffle(shuffle: Boolean) = viewModelScope.launch { carousel.setShuffle(shuffle) }
    fun setIncludeInBackup(include: Boolean) = viewModelScope.launch { carousel.setIncludeInBackup(include) }

    val WallpaperCarouselData.currentWallpaper: String?
        get() = wallpapers.getOrNull(currentIndex)
}
