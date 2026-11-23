package de.mm20.launcher2.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Cycles the system wallpaper through a list of user selected images.
 */
class WallpaperCarousel internal constructor(
    private val context: Context,
    private val store: WallpaperCarouselStore,
) {
    val data: Flow<WallpaperCarouselData> = store.data

    private val mutex = Mutex()

    fun getFile(name: String): File = File(store.imageDir, name)

    /**
     * Copy the given images into the app's storage (downscaled to the screen size).
     * @return number of images that were added
     */
    suspend fun addImages(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val names = uris.mapNotNull { importImage(it) }
        if (names.isNotEmpty()) {
            update { it.copy(wallpapers = it.wallpapers + names) }
        }
        names.size
    }

    suspend fun remove(name: String) {
        update { data ->
            val index = data.wallpapers.indexOf(name)
            if (index == -1) return@update data
            val current = when {
                data.currentIndex > index -> data.currentIndex - 1
                data.currentIndex == index -> data.currentIndex - 1
                else -> data.currentIndex
            }
            data.copy(wallpapers = data.wallpapers - name, currentIndex = current)
        }
        withContext(Dispatchers.IO) { getFile(name).delete() }
    }

    suspend fun setEnabled(enabled: Boolean) {
        val data = update { it.copy(enabled = enabled) }
        // Give immediate feedback when the carousel is turned on
        if (enabled && data.wallpapers.isNotEmpty()) next()
    }

    suspend fun setInterval(minutes: Int) {
        update { it.copy(intervalMinutes = minutes.coerceAtLeast(MinIntervalMinutes)) }
    }

    suspend fun setSchedule(schedule: WallpaperSchedule) {
        update { it.copy(schedule = schedule) }
    }

    suspend fun addTime(minuteOfDay: Int) {
        update { it.copy(times = (it.times + minuteOfDay).distinct().sorted()) }
    }

    suspend fun removeTime(minuteOfDay: Int) {
        update { it.copy(times = it.times - minuteOfDay) }
    }

    suspend fun setTarget(target: WallpaperTarget) {
        update { it.copy(target = target) }
    }

    suspend fun setShuffle(shuffle: Boolean) {
        update { it.copy(shuffle = shuffle) }
    }

    suspend fun setIncludeInBackup(include: Boolean) {
        update { it.copy(includeInBackup = include) }
    }

    /**
     * Switch to the next wallpaper.
     * @return false if there is no wallpaper to switch to or it couldn't be applied
     */
    suspend fun next(): Boolean {
        val data = store.data.first()
        val count = data.wallpapers.size
        if (count == 0) return false
        val index = when {
            count == 1 -> 0
            data.shuffle -> {
                // Pick a random wallpaper other than the current one
                val i = Random.nextInt(count - 1)
                if (i >= data.currentIndex) i + 1 else i
            }

            else -> (data.currentIndex + 1).mod(count)
        }
        return show(index)
    }

    /**
     * Show the wallpaper with the given file name now.
     */
    suspend fun show(name: String): Boolean {
        val index = store.data.first().wallpapers.indexOf(name)
        if (index == -1) return false
        return show(index)
    }

    private suspend fun show(index: Int): Boolean = mutex.withLock {
        val data = store.data.first()
        val name = data.wallpapers.getOrNull(index) ?: return false
        val success = withContext(Dispatchers.IO) { apply(getFile(name), data) }
        if (success) store.update { it.copy(currentIndex = index) }
        success
    }

    suspend fun setFilters(
        vignette: Float? = null,
        dim: Float? = null,
        tone: WallpaperTone? = null,
    ) {
        val data = update {
            it.copy(
                vignette = (vignette ?: it.vignette).coerceIn(0f, 1f),
                dim = (dim ?: it.dim).coerceIn(0f, 1f),
                tone = tone ?: it.tone,
            )
        }
        // Re-apply the current wallpaper so the change is visible right away
        if (data.enabled && data.currentIndex in data.wallpapers.indices) show(data.currentIndex)
    }

    private fun apply(file: File, data: WallpaperCarouselData): Boolean {
        if (!file.exists()) return false
        val flags = when (data.target) {
            WallpaperTarget.HomeAndLock -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            WallpaperTarget.Home -> WallpaperManager.FLAG_SYSTEM
            WallpaperTarget.Lock -> WallpaperManager.FLAG_LOCK
        }
        return try {
            val wm = WallpaperManager.getInstance(context)
            if (data.hasFilters) {
                val source = BitmapFactory.decodeFile(file.absolutePath) ?: return false
                val filtered = WallpaperFilters.apply(source, data)
                source.recycle()
                wm.setBitmap(filtered, null, true, flags)
                filtered.recycle()
            } else {
                file.inputStream().use { wm.setStream(it, null, true, flags) }
            }
            true
        } catch (e: Exception) {
            Log.e("WallpaperCarousel", "Failed to set wallpaper", e)
            false
        }
    }

    private suspend fun update(block: (WallpaperCarouselData) -> WallpaperCarouselData): WallpaperCarouselData {
        val data = store.update(block)
        WallpaperCarouselScheduler.update(context, data)
        return data
    }

    /**
     * Decode the image, scale it down so that it's not (much) larger than the screen and save
     * it as JPEG.
     * @return the file name of the imported image
     */
    private fun importImage(uri: Uri): String? {
        val metrics = context.resources.displayMetrics
        // Allow some headroom for parallax scrolling and screen rotation
        val targetLong = max(metrics.widthPixels, metrics.heightPixels) * 1.25f
        val targetShort = min(metrics.widthPixels, metrics.heightPixels) * 1.25f
        return try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = min(
                        1f,
                        max(targetShort / min(w, h), targetLong / max(w, h)),
                    )
                    if (scale < 1f) {
                        decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
                var sample = 1
                while (min(options.outWidth, options.outHeight) / (sample * 2) >= targetShort) {
                    sample *= 2
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            } ?: return null
            val name = "${UUID.randomUUID()}.jpg"
            File(store.imageDir, name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }
            bitmap.recycle()
            name
        } catch (e: Exception) {
            Log.e("WallpaperCarousel", "Failed to import $uri", e)
            null
        }
    }

    companion object {
        /**
         * WorkManager doesn't allow shorter periodic intervals.
         */
        const val MinIntervalMinutes = 15
    }
}

internal object WallpaperCarouselScheduler {
    private const val WorkName = "wallpaper_carousel"
    private const val TimedWorkName = "wallpaper_carousel_timed"

    /**
     * @param fromWorker true when called by the worker itself, to queue the next run after the
     * current one instead of replacing (cancelling) it.
     */
    fun update(context: Context, data: WallpaperCarouselData, fromWorker: Boolean = false) {
        val workManager = WorkManager.getInstance(context)
        if (!data.enabled || data.wallpapers.size < 2) {
            workManager.cancelUniqueWork(WorkName)
            workManager.cancelUniqueWork(TimedWorkName)
            return
        }
        if (data.schedule == WallpaperSchedule.Times) {
            workManager.cancelUniqueWork(WorkName)
            if (data.times.isEmpty()) {
                workManager.cancelUniqueWork(TimedWorkName)
                return
            }
            val now = System.currentTimeMillis()
            val next = data.times.minOf { nextOccurrence(now, it) }
            val request = OneTimeWorkRequestBuilder<WallpaperCarouselWorker>()
                .setInitialDelay(next - now, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniqueWork(
                TimedWorkName,
                if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                request,
            )
            return
        }
        workManager.cancelUniqueWork(TimedWorkName)
        if (fromWorker) return
        val interval = data.intervalMinutes.coerceAtLeast(WallpaperCarousel.MinIntervalMinutes).toLong()
        val request = PeriodicWorkRequestBuilder<WallpaperCarouselWorker>(interval, TimeUnit.MINUTES)
            .setInitialDelay(interval, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(WorkName, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class WallpaperCarouselWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params), KoinComponent {
    private val carousel: WallpaperCarousel by inject()

    private val store: WallpaperCarouselStore by inject()

    override suspend fun doWork(): Result {
        carousel.next()
        // Time based schedules use one-time work, queue the next one
        val data = store.data.first()
        if (data.schedule == WallpaperSchedule.Times) {
            WallpaperCarouselScheduler.update(applicationContext, data, fromWorker = true)
        }
        return Result.success()
    }
}

private fun nextOccurrence(time: Long, minuteOfDay: Int): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= time + 30_000) cal.add(Calendar.DAY_OF_YEAR, 1)
    return cal.timeInMillis
}
