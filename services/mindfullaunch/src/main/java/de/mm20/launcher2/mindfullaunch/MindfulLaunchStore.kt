package de.mm20.launcher2.mindfullaunch

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

@Serializable
data class MindfulLaunchData(
    val enabled: Boolean = true,
    /**
     * How long the pause lasts before the app can be opened.
     */
    val delaySeconds: Int = 5,
    /**
     * Package names of the apps that get a pause before they open.
     */
    val watchedPackages: Set<String> = emptySet(),
    /**
     * Day the counters below belong to, see [MindfulLaunch.today].
     */
    val day: Int = 0,
    val opensToday: Map<String, Int> = emptyMap(),
    val skipsToday: Map<String, Int> = emptyMap(),
    /**
     * Pop up a reminder every [interruptionMinutes] while a watched app stays open.
     */
    val interruptionsEnabled: Boolean = false,
    val interruptionMinutes: Int = 10,
    /**
     * Use the sweary phrase set.
     */
    val spicy: Boolean = false,
    /**
     * Watched apps only open after scanning one of [nfcTagIds] (hex tag UIDs).
     */
    val nfcUnlock: Boolean = false,
    val nfcTagIds: Set<String> = emptySet(),
    /**
     * Unlock by solving a small math problem.
     */
    val unlockMath: Boolean = false,
    /**
     * Unlock by typing out a sentence.
     */
    val unlockTyping: Boolean = false,
    /**
     * Skip the pause if the app was in use within this many seconds (0 = always pause).
     */
    val graceSeconds: Int = 120,
    /**
     * Show "Instead, you could…" with a suggestion and alternative apps.
     */
    val insteadEnabled: Boolean = true,
    /**
     * The user's own suggestions, used together with the built-in ones.
     */
    val insteadTasks: List<String> = emptyList(),
    /**
     * Package names of apps offered as better alternatives.
     */
    val insteadApps: List<String> = emptyList(),
)

class MindfulLaunchStore internal constructor(
    private val context: Context,
) : BaseSettings<MindfulLaunchData>(
    context,
    fileName = "mindful_launch.json",
    serializer = MindfulLaunchSerializer,
    migrations = emptyList(),
    corruptionHandler = ReplaceFileCorruptionHandler { MindfulLaunchData() },
) {
    val data: Flow<MindfulLaunchData>
        get() = context.dataStore.data

    suspend fun update(block: (MindfulLaunchData) -> MindfulLaunchData): MindfulLaunchData {
        return context.dataStore.updateData(block)
    }
}

internal object MindfulLaunchSerializer : Serializer<MindfulLaunchData> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    override val defaultValue: MindfulLaunchData
        get() = MindfulLaunchData()

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun readFrom(input: InputStream): MindfulLaunchData {
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
    override suspend fun writeTo(t: MindfulLaunchData, output: OutputStream) {
        json.encodeToStream(t, output)
    }
}
