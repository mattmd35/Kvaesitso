package de.mm20.launcher2.ui.mindfullaunch

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.mindfullaunch.MindfulLaunch
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.theme.LauncherTheme
import org.koin.android.ext.android.inject

/**
 * Small dialog that waits for an NFC card/tag and registers its UID for mindful launch unlock.
 */
class NfcRegisterActivity : BaseActivity() {
    private val mindfulLaunch: MindfulLaunch by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(24.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.lock_24px),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.mindful_launch_nfc_register),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.mindful_launch_nfc_scan_now),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { finish() }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        NfcScan.enable(this)
    }

    override fun onPause() {
        super.onPause()
        NfcScan.disable(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val id = NfcScan.tagId(intent) ?: return
        Log.i("MindfulNfc", "register: id=${NfcScan.hex(id)}")
        if (NfcScan.isRandomId(id)) {
            Toast.makeText(this, R.string.mindful_launch_nfc_random_id, Toast.LENGTH_LONG).show()
            return
        }
        mindfulLaunch.addNfcTag(id)
        Toast.makeText(this, R.string.mindful_launch_nfc_added, Toast.LENGTH_SHORT).show()
        finish()
    }
}
