package de.mm20.launcher2.ui.launcher.widgets.irremote

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import androidx.compose.ui.graphics.Color
import de.mm20.launcher2.widgets.IrRemoteLayout

/**
 * A button of an IR remote.
 * @param code 32 bit NEC code, in transmission order (first transmitted bit is the MSB). This is
 * the format most code tables (e.g. Arduino IRremote dumps) use.
 */
data class IrKey(
    val code: Long,
    val color: Color? = null,
    val label: String? = null,
    val kind: Kind = Kind.Color,
) {
    enum class Kind { Color, PowerOn, PowerOff, PlayPause, BrightnessUp, BrightnessDown, Text }
}

object IrRemotes {

    private fun c(code: Long, color: Long) = IrKey(code, color = Color(color))
    private fun t(code: Long, label: String) = IrKey(code, label = label, kind = IrKey.Kind.Text)

    /**
     * Common 24 key RGB remote, rows of 4.
     */
    val Rgb24: List<List<IrKey>> = listOf(
        listOf(
            IrKey(0x00F700FF, kind = IrKey.Kind.BrightnessUp),
            IrKey(0x00F7807F, kind = IrKey.Kind.BrightnessDown),
            IrKey(0x00F740BF, kind = IrKey.Kind.PowerOff),
            IrKey(0x00F7C03F, kind = IrKey.Kind.PowerOn),
        ),
        listOf(c(0x00F720DF, 0xFFFF0000), c(0x00F7A05F, 0xFF00FF00), c(0x00F7609F, 0xFF0000FF), c(0x00F7E01F, 0xFFFFFFFF)),
        listOf(c(0x00F710EF, 0xFFFF4500), c(0x00F7906F, 0xFF00FF80), c(0x00F750AF, 0xFF2050FF), t(0x00F7D02F, "Flash")),
        listOf(c(0x00F730CF, 0xFFFF8000), c(0x00F7B04F, 0xFF00FFFF), c(0x00F7708F, 0xFF6020FF), t(0x00F7F00F, "Strobe")),
        listOf(c(0x00F708F7, 0xFFFFB000), c(0x00F78877, 0xFF00B0D0), c(0x00F748B7, 0xFFA000FF), t(0x00F7C837, "Fade")),
        listOf(c(0x00F728D7, 0xFFFFFF00), c(0x00F7A857, 0xFF0080C0), c(0x00F76897, 0xFFFF00FF), t(0x00F7E817, "Smooth")),
    )

    /**
     * Common 44 key RGB remote, rows of 4.
     */
    val Rgb44: List<List<IrKey>> = listOf(
        listOf(
            IrKey(0x00FF3AC5, kind = IrKey.Kind.BrightnessUp),
            IrKey(0x00FFBA45, kind = IrKey.Kind.BrightnessDown),
            IrKey(0x00FF827D, kind = IrKey.Kind.PlayPause),
            IrKey(0x00FF02FD, kind = IrKey.Kind.PowerOff),
        ),
        listOf(c(0x00FF1AE5, 0xFFFF0000), c(0x00FF9A65, 0xFF00FF00), c(0x00FFA25D, 0xFF0000FF), c(0x00FF22DD, 0xFFFFFFFF)),
        listOf(c(0x00FF2AD5, 0xFFFF4500), c(0x00FFAA55, 0xFF00FF80), c(0x00FF926D, 0xFF2050FF), c(0x00FF12ED, 0xFFFFC0CB)),
        listOf(c(0x00FF0AF5, 0xFFFF8000), c(0x00FF8A75, 0xFF00FFFF), c(0x00FFB24D, 0xFF6020FF), c(0x00FF32CD, 0xFFFFC0E0)),
        listOf(c(0x00FF38C7, 0xFFFFB000), c(0x00FFB847, 0xFF00B0D0), c(0x00FF7887, 0xFFA000FF), c(0x00FFF807, 0xFFB0E0FF)),
        listOf(c(0x00FF18E7, 0xFFFFFF00), c(0x00FF9867, 0xFF0080C0), c(0x00FF58A7, 0xFFFF00FF), c(0x00FFD827, 0xFFA0D0FF)),
        listOf(t(0x00FF28D7, "R +"), t(0x00FFA857, "G +"), t(0x00FF6897, "B +"), t(0x00FFE817, "Quick")),
        listOf(t(0x00FF08F7, "R −"), t(0x00FF8877, "G −"), t(0x00FF48B7, "B −"), t(0x00FFC837, "Slow")),
        listOf(t(0x00FF30CF, "DIY1"), t(0x00FFB04F, "DIY2"), t(0x00FF708F, "DIY3"), t(0x00FFF00F, "Auto")),
        listOf(t(0x00FF10EF, "DIY4"), t(0x00FF906F, "DIY5"), t(0x00FF50AF, "DIY6"), t(0x00FFD02F, "Flash")),
        listOf(t(0x00FF20DF, "Jump3"), t(0x00FFA05F, "Jump7"), t(0x00FF609F, "Fade3"), t(0x00FFE01F, "Fade7")),
    )

    fun keys(layout: IrRemoteLayout) = when (layout) {
        IrRemoteLayout.Rgb24 -> Rgb24
        IrRemoteLayout.Rgb44 -> Rgb44
    }
}

/**
 * Sends NEC codes with the device's IR blaster.
 */
class IrTransmitter(context: Context) {
    private val irManager = context.getSystemService(ConsumerIrManager::class.java)

    val isAvailable: Boolean
        get() = irManager?.hasIrEmitter() == true

    fun send(code: Long): Boolean {
        val manager = irManager ?: return false
        return try {
            manager.transmit(Carrier, necPattern(code))
            true
        } catch (e: Exception) {
            Log.e("IrRemote", "Failed to transmit", e)
            false
        }
    }

    companion object {
        private const val Carrier = 38_000

        /**
         * NEC frame: 9 ms burst, 4.5 ms space, 32 bits (562 µs burst, then 562 µs space for 0 or
         * 1687 µs space for 1), final 562 µs burst. Durations alternate on/off, starting with on.
         */
        fun necPattern(code: Long): IntArray {
            val pattern = IntArray(2 + 32 * 2 + 1)
            pattern[0] = 9000
            pattern[1] = 4500
            for (i in 0 until 32) {
                val bit = (code shr (31 - i)) and 1L
                pattern[2 + i * 2] = 562
                pattern[3 + i * 2] = if (bit == 1L) 1687 else 562
            }
            pattern[pattern.lastIndex] = 562
            return pattern
        }
    }
}
