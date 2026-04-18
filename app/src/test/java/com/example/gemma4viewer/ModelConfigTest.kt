package com.example.gemma4viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConfigTest {

    @Test
    fun modelUrlStartsWithHttps() {
        assertTrue(
            "MODEL_URL must start with https://",
            ModelConfig.MODEL_URL.startsWith("https://")
        )
    }

    @Test
    fun mmprojUrlStartsWithHttps() {
        assertTrue(
            "MMPROJ_URL must start with https://",
            ModelConfig.MMPROJ_URL.startsWith("https://")
        )
    }

    @Test
    fun modelUrlDoesNotContainHttp() {
        assertTrue(
            "MODEL_URL must not contain http://",
            !ModelConfig.MODEL_URL.contains("http://")
        )
    }

    @Test
    fun mmprojUrlDoesNotContainHttp() {
        assertTrue(
            "MMPROJ_URL must not contain http://",
            !ModelConfig.MMPROJ_URL.contains("http://")
        )
    }

    @Test
    fun modelFilenameIsCorrect() {
        assertEquals("model.gguf", ModelConfig.MODEL_FILENAME)
    }

    @Test
    fun mmprojFilenameIsCorrect() {
        assertEquals("mmproj.gguf", ModelConfig.MMPROJ_FILENAME)
    }
}
