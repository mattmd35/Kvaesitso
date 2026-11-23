package de.mm20.launcher2.wallpaper

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
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

    /**
     * @param width target width (the screen), so the corners end up exactly in the screen corners
     * @param height target height
     */
    fun apply(source: Bitmap, data: WallpaperCarouselData, width: Int, height: Int): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val w = width.toFloat()
        val h = height.toFloat()

        // Center-crop the image to the screen
        val scale = max(w / source.width, h / source.height)
        val dw = source.width * scale
        val dh = source.height * scale
        val dst = RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)

        val matrix = toneMatrix(data.tone)
        canvas.drawBitmap(source, null, dst, Paint(Paint.FILTER_BITMAP_FLAG).apply {
            if (matrix != null) colorFilter = ColorMatrixColorFilter(matrix)
        })

        if (data.dim > 0f) {
            canvas.drawColor(Color.argb((data.dim * 0.85f * 255).toInt(), 0, 0, 0))
        }

        if (data.vignette > 0f) drawCorners(canvas, w, h, data.vignette)
        return out
    }

    /**
     * Heavy dark rounded corners: everything outside a large rounded rectangle is black, with a
     * soft edge. Stronger = bigger radius, wider and darker falloff.
     */
    private fun drawCorners(canvas: Canvas, w: Float, h: Float, strength: Float) {
        val minDim = min(w, h)
        val radius = minDim * (0.12f + 0.38f * strength)
        val feather = minDim * (0.03f + 0.09f * strength)
        val inset = feather * 0.8f

        val layer = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val lc = Canvas(layer)
        lc.drawColor(Color.BLACK)
        lc.drawRoundRect(
            inset, inset, w - inset, h - inset, radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                maskFilter = BlurMaskFilter(feather, BlurMaskFilter.Blur.NORMAL)
            }
        )
        canvas.drawBitmap(layer, 0f, 0f, Paint().apply {
            alpha = (180 + 75 * strength).toInt().coerceAtMost(255)
        })
        layer.recycle()

        // Plus a soft overall falloff towards the edges
        val paint = Paint().apply {
            shader = RadialGradient(
                w / 2f, h / 2f, hypot(w, h) / 2f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb((110 * strength).toInt(), 0, 0, 0)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, paint)
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
