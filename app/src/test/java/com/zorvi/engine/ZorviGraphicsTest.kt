package com.zorvi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZorviGraphicsTest {
    @Test fun imageSamplingUsesPowerOfTwoAndDoesNotUpscale() {
        assertEquals(4, ZorviImageDecoder.calculateSampleSize(4000, 3000, 800, 600))
        assertEquals(1, ZorviImageDecoder.calculateSampleSize(320, 240, 800, 600))
        assertEquals(2, ZorviImageDecoder.calculateSampleSize(1600, 1200, 800, 600))
    }

    @Test(expected = IllegalArgumentException::class)
    fun imageSamplingRejectsInvalidDimensions() {
        ZorviImageDecoder.calculateSampleSize(0, 100, 50, 50)
    }

    @Test fun paintDisplayListCommandsKeepRequestedGeometry() {
        val rect = ZorviRect(1f, 2f, 30f, 40f)
        val command = ZorviPaintCommand.FillRect(rect, 0xff123456.toInt())
        assertEquals(1f, command.bounds.left)
        assertEquals(40f, command.bounds.bottom)
        assertEquals(0xff123456.toInt(), command.color)
    }

    @Test fun displayListSupportsClippingTransformsAndLayerOpacity() {
        val clip = ZorviPaintCommand.ClipRect(ZorviRect(0f, 0f, 100f, 80f))
        val translate = ZorviPaintCommand.Translate(12f, 18f)
        val scale = ZorviPaintCommand.Scale(2f, 2f, 4f, 5f)
        val rotate = ZorviPaintCommand.Rotate(45f, 10f, 10f)
        val layer = ZorviPaintCommand.SaveLayerAlpha(ZorviRect(0f, 0f, 100f, 100f), 128)
        assertTrue(clip.bounds.right == 100f)
        assertEquals(12f, translate.dx)
        assertEquals(2f, scale.sx)
        assertEquals(45f, rotate.degrees)
        assertEquals(128, layer.alpha)
    }

    @Test fun fontResolverAcceptsGenericFamiliesAndStyles() {
        assertEquals(android.graphics.Typeface.BOLD_ITALIC, ZorviFontResolver.styleFor(700, true))
        assertEquals(android.graphics.Typeface.NORMAL, ZorviFontResolver.styleFor(400, false))
        assertEquals(android.graphics.Typeface.ITALIC, ZorviFontResolver.styleFor(400, true))
        assertEquals(android.graphics.Typeface.BOLD, ZorviFontResolver.styleFor(700, false))
    }
}
