package de.mm20.launcher2.ui.mindfullaunch

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.util.Log

/**
 * NFC tag scanning through foreground dispatch. Reader mode fails to activate some cards (e.g.
 * ISIC cards on NXP controllers report DISCOVERY_TARGET_ACTIVATION_FAILED), while the regular
 * discovery path reads them fine. Tags arrive in the activity's onNewIntent, so the activity
 * must be singleTop.
 */
internal object NfcScan {
    fun enable(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        val intent = Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            activity, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        )
        try {
            // No filters: catch every tag while this activity is in front
            adapter.enableForegroundDispatch(activity, pending, null, null)
        } catch (e: Exception) {
            Log.e("MindfulNfc", "enableForegroundDispatch failed", e)
        }
    }

    fun disable(activity: Activity) {
        try {
            NfcAdapter.getDefaultAdapter(activity)?.disableForegroundDispatch(activity)
        } catch (e: Exception) {
            Log.e("MindfulNfc", "disableForegroundDispatch failed", e)
        }
    }

    /**
     * The tag UID from a dispatched intent, or null if the intent isn't a tag scan.
     */
    fun tagId(intent: Intent?): ByteArray? {
        intent ?: return null
        intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)?.let { return it }
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        return tag?.id
    }

    /**
     * 4-byte UIDs starting with 0x08 are random: a new one on every tap.
     */
    fun isRandomId(id: ByteArray) = id.size == 4 && id[0] == 0x08.toByte()

    fun hex(id: ByteArray) = id.joinToString("") { "%02x".format(it) }
}
