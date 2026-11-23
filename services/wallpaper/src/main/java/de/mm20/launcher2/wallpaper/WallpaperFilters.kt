package de.mm20.launcher2.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.hypot

/**
 * Look adjustments applied to carousel images when they are set as wallpaper.
 */
internal object WallpaperFilters {

    fun apply(source: Bitmap, data: WallpaperCarouselData): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val w = source.width.toFloat()
        val h = source.height.toFloat()

        val matrix = toneMatrix(data.tone)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
            if (matrix != null) colorFilter = ColorMatrixColorFilter(matrix)
        })

        if (data.dim > 0f) {
            canvas.drawColor(Color.argb((data.dim * 0.85f * 255).toInt(), 0, 0, 0))
        }

        if (data.vignette > 0f) {
            val radius = hypot(w, h) / 2f
            val edge = (data.vignette * 255).toInt()
            val paint = Paint().apply {
                shader = RadialGradient(
                    w / 2f, h / 2f, radius,
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        Color.argb(edge / 2, 0, 0, 0),
                        Color.argb(edge, 0, 0, 0),
                    ),
                    // Stronger vignettes also reach further into the image
                    floatArrayOf(0f, 0.55f - 0.25f * data.vignette, 0.8f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        return out
    }

    private fun toneMatrix(tone: WallpaperTone): ColorMatrix? = when (tone) {
        WallpaperTone.None -> null
        WallpaperTone.Mono -> ColorMatrix().apply { setSaturation(0f) }
        WallpaperTone.Warm -> ColorMatrix(
            floatArrayOf(
                1.08f, 0f, 0f, 0f, 8f,
                0f, 1.0f, 0f, 0f, 0f,
                0f, 0f, 0.85f, 0f, -8f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        WallpaperTone.Cool -> ColorMatrix(
            floatArrayOf(
                0.88f, 0f, 0f, 0f, -6f,
                0f, 1.0f, 0f, 0f, 0f,
                0f, 0f, 1.1f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        WallpaperTone.Sepia -> ColorMatrix().apply {
            setSaturation(0f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.07f, 0f, 0f, 0f, 20f,
                        0f, 0.95f, 0f, 0f, 8f,
                        0f, 0f, 0.78f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            )
        }
        // Lifted blacks, lower contrast and saturation
        WallpaperTone.Faded -> ColorMatrix().apply {
            setSaturation(0.7f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        0.82f, 0f, 0f, 0f, 30f,
                        0f, 0.82f, 0f, 0f, 30f,
                        0f, 0f, 0.82f, 0f, 34f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            )
        }
    }
}
