package com.zorvi.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/** Immutable display-list commands. Coordinates are device-independent CSS pixels. */
internal sealed interface ZorviPaintCommand {
    data class FillRect(val bounds: RectF, val color: Int) : ZorviPaintCommand
    data class DrawText(
        val text: String,
        val x: Float,
        val baseline: Float,
        val color: Int,
        val sizePx: Float,
        val typeface: Typeface = Typeface.DEFAULT
    ) : ZorviPaintCommand
    data class DrawBitmap(val bitmap: Bitmap, val destination: RectF, val alpha: Int = 255) : ZorviPaintCommand
}

/**
 * Small Android raster backend for the engine display list.
 * It owns no DOM/layout state; callers provide an ordered immutable command snapshot.
 */
internal class ZorviGraphicsRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    fun render(canvas: Canvas, commands: List<ZorviPaintCommand>) {
        commands.forEach { command ->
            when (command) {
                is ZorviPaintCommand.FillRect -> {
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.color = command.color
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(command.bounds, paint)
                }
                is ZorviPaintCommand.DrawText -> {
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.color = command.color
                    paint.textSize = command.sizePx.coerceIn(1f, 256f)
                    paint.typeface = command.typeface
                    canvas.drawText(command.text, command.x, command.baseline, paint)
                }
                is ZorviPaintCommand.DrawBitmap -> {
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.isFilterBitmap = true
                    paint.alpha = command.alpha.coerceIn(0, 255)
                    canvas.drawBitmap(command.bitmap, null, command.destination, paint)
                }
            }
        }
    }
}

/** Deterministic Android font-family fallback for CSS generic-family requests. */
internal object ZorviFontResolver {
    fun resolve(family: String?, weight: Int = 400, italic: Boolean = false): Typeface {
        val normalized = family.orEmpty().trim().trim('"', '\'').lowercase()
        val base = when (normalized) {
            "serif", "georgia", "times", "times new roman" -> "serif"
            "monospace", "monaco", "consolas", "courier", "courier new" -> "monospace"
            "cursive" -> "cursive"
            "fantasy" -> "fantasy"
            "sans-serif", "arial", "helvetica", "roboto", "" -> "sans-serif"
            else -> normalized
        }
        val style = when {
            weight >= 600 && italic -> Typeface.BOLD_ITALIC
            weight >= 600 -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, style)
    }
}

/** Bounds image allocation before decoding untrusted page-provided bytes. */
internal object ZorviImageDecoder {
    const val MAX_ENCODED_BYTES = 16 * 1024 * 1024
    const val MAX_DECODED_PIXELS = 16_000_000L

    data class Result(val bitmap: Bitmap?, val error: String? = null) {
        val isSuccess: Boolean get() = bitmap != null && error == null
    }

    fun decode(bytes: ByteArray, targetWidth: Int, targetHeight: Int): Result {
        if (bytes.isEmpty()) return Result(null, "Empty image")
        if (bytes.size > MAX_ENCODED_BYTES) return Result(null, "Image exceeds encoded-size limit")
        if (targetWidth <= 0 || targetHeight <= 0) return Result(null, "Invalid target dimensions")
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return Result(null, "Unsupported or invalid image")
            if (width.toLong() * height.toLong() > MAX_DECODED_PIXELS) {
                return Result(null, "Image exceeds decoded-pixel limit")
            }
            val sample = calculateSampleSize(width, height, targetWidth, targetHeight)
            val options = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: return Result(null, "Image decoding failed")
            Result(bitmap)
        } catch (error: OutOfMemoryError) {
            Result(null, "Insufficient memory to decode image")
        } catch (error: Exception) {
            Result(null, error.message ?: "Image decoding failed")
        }
    }

    internal fun calculateSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        require(width > 0 && height > 0 && targetWidth > 0 && targetHeight > 0)
        var sample = 1
        while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }
}
