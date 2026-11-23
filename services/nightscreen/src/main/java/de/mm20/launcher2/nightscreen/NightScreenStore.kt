package de.mm20.launcher2.nightscreen

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import de.mm20.launcher2.settings.BaseSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Calendar

@Serializable
data class NightScreenData(
    /**
     * How much the screen is darkened, 0..1.
     */
    val dim: Float = 0.7f,
    /**
     * Strength of the warm (amber) tint, 0..1.
     */
    val warmth: Float = 0.6f,
    val scheduleEnabled: Boolean = false,
    /**
     * Minutes after midnight.
     */
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60,
    /**
     * Manual on/off override. Takes precedence over the schedule until [manualUntil].
     */
    val manualState: Boolean? = null,
    val manualUntil: Long = 0L,
) {
    fun isScheduled(time: Long): Boolean {
        if (!scheduleEnabled || startMinute == endMinute) return false
        val minute = minuteOfDay(time)
        return if (startMinute < endMinute) {
            minute in startMinute until endMinute
        } else {
            minute >= startMinute || minute < endMinute
        }
    }

    fun isActive(time: Long): Boolean {
        if (manualState != null && time < manualUntil) return manualState
        return isScheduled(time)
    }

    /**
     * Time of the next schedule start or end after [time].
     */
    fun nextTransition(time: Long): Long {
        if (!scheduleEnabled) return Long.MAX_VALUE
        return minOf(nextOccurrence(time, startMinute), nextOccurrence(time, endMinute))
    }

    companion object {
        fun minuteOfDay(time: Long): Int {
            val cal = Calendar.getInstance().apply { timeInMillis = time }
            return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        }

        fun nextOccurrence(time: Long, minuteOfDay: Int): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = time
                set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
                set(Calendar.MINUTE, minuteOfDay % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= time) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
    }
}

class NightScreenStore internal constructor(
    private val context: Context,
) : BaseSettings<NightScreenData>(
    context,
    fileName = "night_screen.json",
    serializer = NightScreenSerializer,
    migrations = emptyList(),
    corruptionHandler = ReplaceFileCorruptionHandler { NightScreenData() },
) {
    val data: Flow<NightScreenData>
        get() = context.dataStore.data

    suspend fun update(block: (NightScreenData) -> NightScreenData): NightScreenData {
        return context.dataStore.updateData(block)
    }
}

internal object NightScreenSerializer : Serializer<NightScreenData> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    override val defaultValue: NightScreenData
        get() = NightScreenData()

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun readFrom(input: InputStream): NightScreenData {
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
    override suspend fun writeTo(t: NightScreenData, output: OutputStream) {
        json.encodeToStream(t, output)
    }
}
