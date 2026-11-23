package de.mm20.launcher2.ui.settings.wallpaper

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.wallpaper.WallpaperCarousel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Share target ("Add to wallpaper carousel") for images shared from other apps.
 * Has no UI: imports the images, shows a toast and finishes.
 */
class AddToCarouselActivity : ComponentActivity() {
    private val carousel: WallpaperCarousel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = getSharedImages(intent)
        if (uris.isEmpty()) {
            finish()
            return
        }
        Toast.makeText(this, R.string.wallpaper_carousel_share_adding, Toast.LENGTH_SHORT).show()
        // Import while the activity is alive: the read permission for the shared URIs is only
        // granted to this activity.
        lifecycleScope.launch {
            val added = carousel.addImages(uris)
            val message = if (added > 0) {
                resources.getQuantityString(R.plurals.wallpaper_carousel_share_added, added, added)
            } else {
                getString(R.string.wallpaper_carousel_share_failed)
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun getSharedImages(intent: Intent): List<Uri> {
        return when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            )

            Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }.orEmpty()
            }

            else -> emptyList()
        }
    }
}
