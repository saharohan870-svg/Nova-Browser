package com.zorvi.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/** Platform-neutral rectangle in CSS pixel coordinates. */
internal data class ZorviRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init { require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) }
}

/** Immutable display-list commands. Coordinates are device-independent CSS pixels. */
internal sealed interface ZorviPaintCommand {
    data class FillRect(val bounds: ZorviRect, val color: Int) : ZorviPaintCommand
    data class DrawText(
        val text: String,
        val x: Float,
        val baseline: Float,
        val color: Int,
        val sizePx: Float,
        val typeface: Typeface = Typeface.DEFAULT
    ) : ZorviPaintCommand
    data class DrawBitmap(val bitmap: Bitmap, val destination: ZorviRect, val alpha: Int = 255) : ZorviPaintCommand
    data class ClipRect(val bounds: ZorviRect) : ZorviPaintCommand
    data class Translate(val dx: Float, val dy: Float) : ZorviPaintCommand
    data class Scale(val sx: Float, val sy: Float, val pivotX: Float = 0f, val pivotY: Float = 0f) : ZorviPaintCommand
    data class Rotate(val degrees: Float, val pivotX: Float = 0f, val pivotY: Float = 0f) : ZorviPaintCommand
    data object Save : ZorviPaintCommand
    data object Restore : ZorviPaintCommand
    data class SaveLayerAlpha(val bounds: ZorviRect, val alpha: Int) : ZorviPaintCommand
}

/**
 * Small Android raster backend for the engine display list.
 * It owns no DOM/layout state; callers provide an ordered immutable command snapshot.
 * Save/restore is balanced defensively so a malformed display list cannot underflow Canvas state.
 */
internal class ZorviGraphicsRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    fun render(canvas: Canvas, commands: List<ZorviPaintCommand>) {
        val initialSaveCount = canvas.saveCount
        commands.forEach { command ->
            when (command) {
                is ZorviPaintCommand.FillRect -> {
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.color = command.color
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(command.bounds.toRectF(), paint)
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
                    canvas.drawBitmap(command.bitmap, null, command.destination.toRectF(), paint)
                }
                is ZorviPaintCommand.ClipRect -> canvas.clipRect(command.bounds.toRectF())
                is ZorviPaintCommand.Translate -> if (command.dx.isFinite() && command.dy.isFinite()) canvas.translate(command.dx, command.dy)
                is ZorviPaintCommand.Scale -> if (command.sx.isFinite() && command.sy.isFinite() && command.pivotX.isFinite() && command.pivotY.isFinite()) {
                    canvas.scale(command.sx.coerceIn(-100f, 100f), command.sy.coerceIn(-100f, 100f), command.pivotX, command.pivotY)
                }
                is ZorviPaintCommand.Rotate -> if (command.degrees.isFinite() && command.pivotX.isFinite() && command.pivotY.isFinite()) {
                    canvas.rotate(command.degrees % 360f, command.pivotX, command.pivotY)
                }
                ZorviPaintCommand.Save -> canvas.save()
                ZorviPaintCommand.Restore -> if (canvas.saveCount > initialSaveCount) canvas.restore()
                is ZorviPaintCommand.SaveLayerAlpha -> canvas.saveLayerAlpha(
                    command.bounds.toRectF(), command.alpha.coerceIn(0, 255)
                )
            }
        }
        while (canvas.saveCount > initialSaveCount) canvas.restore()
    }

    private fun ZorviRect.toRectF() = RectF(left, top, right, bottom)
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
        return Typeface.create(base, styleFor(weight, italic))
    }

    internal fun styleFor(weight: Int, italic: Boolean): Int = when {
        weight >= 600 && italic -> Typeface.BOLD_ITALIC
        weight >= 600 -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
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
        while (sample <= (1 shl 29) && width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }
}
