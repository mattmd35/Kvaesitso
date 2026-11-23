package de.mm20.launcher2.preferences.drawer

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import de.mm20.launcher2.settings.BaseSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Persists the [AppDrawerLayout] (custom app order and folders) to `drawer_layout.json`.
 * Included in backups.
 */
class AppDrawerLayoutStore internal constructor(
    private val context: Context,
) : BaseSettings<AppDrawerLayout>(
    context,
    fileName = "drawer_layout.json",
    serializer = AppDrawerLayoutSerializer,
    migrations = emptyList(),
    corruptionHandler = ReplaceFileCorruptionHandler { AppDrawerLayout() },
) {
    val layout: Flow<AppDrawerLayout>
        get() = context.dataStore.data

    suspend fun save(layout: AppDrawerLayout) {
        context.dataStore.updateData { layout }
    }
}

internal object AppDrawerLayoutSerializer : Serializer<AppDrawerLayout> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    override val defaultValue: AppDrawerLayout
        get() = AppDrawerLayout()

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun readFrom(input: InputStream): AppDrawerLayout {
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
    override suspend fun writeTo(t: AppDrawerLayout, output: OutputStream) {
        json.encodeToStream(t, output)
    }
}
