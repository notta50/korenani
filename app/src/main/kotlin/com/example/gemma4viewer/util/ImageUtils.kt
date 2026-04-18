package com.example.gemma4viewer.util

import android.graphics.Bitmap

object ImageUtils {
    fun Bitmap.toRgbByteArray(): ByteArray {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixelsToRgb(pixels)
    }

    internal fun pixelsToRgb(pixels: IntArray): ByteArray {
        val rgb = ByteArray(pixels.size * 3)
        for (i in pixels.indices) {
            rgb[i * 3]     = ((pixels[i] shr 16) and 0xFF).toByte()
            rgb[i * 3 + 1] = ((pixels[i] shr 8)  and 0xFF).toByte()
            rgb[i * 3 + 2] = (pixels[i]           and 0xFF).toByte()
        }
        return rgb
    }
}
