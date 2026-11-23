package de.mm20.launcher2.wallpaper

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import de.mm20.launcher2.settings.BaseSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@Serializable
enum class WallpaperTarget {
    HomeAndLock,
    Home,
    Lock,
}

@Serializable
data class WallpaperCarouselData(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 60,
    val target: WallpaperTarget = WallpaperTarget.HomeAndLock,
    val shuffle: Boolean = false,
    /**
     * Whether the images themselves are included in backups (the settings always are).
     */
    val includeInBackup: Boolean = true,
    /**
     * File names of the wallpapers in [WallpaperCarouselStore.imageDir], in carousel order.
     */
    val wallpapers: List<String> = emptyList(),
    val currentIndex: Int = -1,
)

/**
 * Persists the wallpaper carousel settings to `wallpaper_carousel.json`. The images are stored
 * in `files/wallpaper_carousel/`. Both are included in backups (images optionally).
 */
class WallpaperCarouselStore internal constructor(
    private val context: Context,
) : BaseSettings<WallpaperCarouselData>(
    context,
    fileName = "wallpaper_carousel.json",
    serializer = WallpaperCarouselSerializer,
    migrations = emptyList(),
    corruptionHandler = ReplaceFileCorruptionHandler { WallpaperCarouselData() },
) {
    val imageDir: File
        get() = File(context.filesDir, "wallpaper_carousel").apply { mkdirs() }

    val data: Flow<WallpaperCarouselData>
        get() = context.dataStore.data

    suspend fun update(block: (WallpaperCarouselData) -> WallpaperCarouselData): WallpaperCarouselData {
        return context.dataStore.updateData(block)
    }

    override suspend fun backup(toDir: File) {
        super.backup(toDir)
        val data = data.first()
        if (!data.includeInBackup) return
        for (name in data.wallpapers) {
            val file = File(imageDir, name)
            if (!file.exists()) continue
            file.copyTo(File(toDir, BackupPrefix + name), overwrite = true)
        }
    }

    override suspend fun restore(fromDir: File) {
        super.restore(fromDir)
        val dir = imageDir
        for (name in data.first().wallpapers) {
            val file = File(fromDir, BackupPrefix + name)
            if (!file.exists()) continue
            try {
                file.copyTo(File(dir, name), overwrite = true)
            } catch (e: IOException) {
                Log.e("WallpaperCarousel", "Failed to restore $name", e)
            }
        }
        // Drop entries whose images were not part of the backup
        val restored = update { data ->
            data.copy(wallpapers = data.wallpapers.filter { File(dir, it).exists() })
        }
        WallpaperCarouselScheduler.update(context, restored)
    }

    companion object {
        private const val BackupPrefix = "wallpaper_carousel_"
    }
}

internal object WallpaperCarouselSerializer : Serializer<WallpaperCarouselData> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    override val defaultValue: WallpaperCarouselData
        get() = WallpaperCarouselData()

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun readFrom(input: InputStream): WallpaperCarouselData {
        try {
            return json.decodeFromStream(input)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Cannot read json.", e)
        } catch (e: SerializationException) {
            throw CorruptionException("Cannot read json.", e)
        } catch (e: IOException) {
            throw CorruptionException("Cannot read json.", e)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun writeTo(t: WallpaperCarouselData, output: OutputStream) {
        json.encodeToStream(t, output)
    }
}
