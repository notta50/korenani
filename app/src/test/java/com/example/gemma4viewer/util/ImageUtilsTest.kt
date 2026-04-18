package com.example.gemma4viewer.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageUtilsTest {

    @Test
    fun `pixelsToRgb red pixels produce correct byte pattern`() {
        val redPixel = (0xFF shl 24) or (0xFF shl 16) or (0x00 shl 8) or 0x00
        val pixels = IntArray(2) { redPixel }

        val result = ImageUtils.pixelsToRgb(pixels)

        assertArrayEquals(
            byteArrayOf(255.toByte(), 0, 0, 255.toByte(), 0, 0),
            result
        )
    }

    @Test
    fun `pixelsToRgb green pixels produce correct byte pattern`() {
        val greenPixel = (0xFF shl 24) or (0x00 shl 16) or (0xFF shl 8) or 0x00
        val pixels = IntArray(2) { greenPixel }

        val result = ImageUtils.pixelsToRgb(pixels)

        assertArrayEquals(
            byteArrayOf(0, 255.toByte(), 0, 0, 255.toByte(), 0),
            result
        )
    }

    @Test
    fun `pixelsToRgb blue pixels produce correct byte pattern`() {
        val bluePixel = (0xFF shl 24) or (0x00 shl 16) or (0x00 shl 8) or 0xFF
        val pixels = IntArray(2) { bluePixel }

        val result = ImageUtils.pixelsToRgb(pixels)

        assertArrayEquals(
            byteArrayOf(0, 0, 255.toByte(), 0, 0, 255.toByte()),
            result
        )
    }

    @Test
    fun `pixelsToRgb output size is pixel count times 3`() {
        val pixels = IntArray(6) { 0 }
        val result = ImageUtils.pixelsToRgb(pixels)
        assertEquals(18, result.size)
    }

    @Test
    fun `pixelsToRgb mixed pixels produce correct per-pixel RGB`() {
        val redPixel   = (0xFF shl 24) or (0xFF shl 16)
        val greenPixel = (0xFF shl 24) or (0xFF shl 8)
        val bluePixel  = (0xFF shl 24) or 0xFF
        val pixels = intArrayOf(redPixel, greenPixel, bluePixel)

        val result = ImageUtils.pixelsToRgb(pixels)

        assertArrayEquals(
            byteArrayOf(
                255.toByte(), 0, 0,
                0, 255.toByte(), 0,
                0, 0, 255.toByte()
            ),
            result
        )
    }

    @Test
    fun `pixelsToRgb empty input returns empty array`() {
        val result = ImageUtils.pixelsToRgb(IntArray(0))
        assertEquals(0, result.size)
    }
}
