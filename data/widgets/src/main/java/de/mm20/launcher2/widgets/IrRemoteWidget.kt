package de.mm20.launcher2.widgets

import android.content.Context
import de.mm20.launcher2.database.entities.PartialWidgetEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class IrRemoteLayout {
    /**
     * Common 24 key RGB LED strip remote (NEC, address 0xEF00).
     */
    Rgb24,

    /**
     * Common 44 key RGB LED strip remote (NEC, address 0x00).
     */
    Rgb44,
}

@Serializable
data class IrRemoteWidgetConfig(
    val layout: IrRemoteLayout = IrRemoteLayout.Rgb24,
)

/**
 * Infrared remote control for LED strips, using the device's IR blaster.
 */
data class IrRemoteWidget(
    override val id: UUID,
    val config: IrRemoteWidgetConfig = IrRemoteWidgetConfig(),
) : Widget() {
    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = Json.encodeToString(config),
        )
    }

    override fun getLabel(context: Context): String {
        return context.getString(R.string.widget_name_ir_remote)
    }

    companion object {
        const val Type = "ir_remote"
    }
}
