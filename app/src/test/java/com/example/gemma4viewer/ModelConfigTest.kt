package com.example.gemma4viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConfigTest {

    @Test
    fun modelUrlStartsWithHttps() {
        assertTrue(
            "MODEL_URL は https:// で始まらなければならない",
            ModelConfig.MODEL_URL.startsWith("https://")
        )
    }

    @Test
    fun modelUrlHasLitertlmExtension() {
        assertTrue(
            "MODEL_URL は .litertlm で終わらなければならない",
            ModelConfig.MODEL_URL.endsWith(".litertlm")
        )
    }

    @Test
    fun modelUrlIsGemma4E2BLitertlm() {
        assertEquals(
            "MODEL_URL は Gemma 4 E2B .litertlm の HuggingFace URL でなければならない",
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            ModelConfig.MODEL_URL
        )
    }

    @Test
    fun modelFilenameIsLitertlm() {
        assertEquals(
            "MODEL_FILENAME は gemma-4-E2B-it.litertlm でなければならない",
            "gemma-4-E2B-it.litertlm",
            ModelConfig.MODEL_FILENAME
        )
    }

    @Test
    fun mmprojUrlIsEmpty() {
        assertEquals(
            "MMPROJ_URL は空文字でなければならない（getMmprojPath() 互換性のため維持）",
            "",
            ModelConfig.MMPROJ_URL
        )
    }

    @Test
    fun mmprojFilenameIsEmpty() {
        assertEquals(
            "MMPROJ_FILENAME は空文字でなければならない（getMmprojPath() 互換性のため維持）",
            "",
            ModelConfig.MMPROJ_FILENAME
        )
    }
}
