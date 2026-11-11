package com.zorvi.engine

import org.junit.Assert.assertEquals
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
        val rect = android.graphics.RectF(1f, 2f, 30f, 40f)
        val command = ZorviPaintCommand.FillRect(rect, 0xff123456.toInt())
        assertEquals(1f, command.bounds.left)
        assertEquals(40f, command.bounds.bottom)
        assertEquals(0xff123456.toInt(), command.color)
    }
}
