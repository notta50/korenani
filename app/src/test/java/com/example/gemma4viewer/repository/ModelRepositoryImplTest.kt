package com.example.gemma4viewer.repository

import com.example.gemma4viewer.ModelConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var repo: ModelRepositoryImpl

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("filesDir")
        repo = ModelRepositoryImpl(filesDir)
    }

    // ケース1: .litertlm ファイルが存在しない → isModelReady() == false
    @Test
    fun isModelReady_fileAbsent_returnsFalse() {
        assertFalse(repo.isModelReady())
    }

    // ケース2: .litertlm ファイルが存在するが size=0 → isModelReady() == false
    @Test
    fun isModelReady_fileExistsButEmpty_returnsFalse() {
        File(filesDir, ModelConfig.MODEL_FILENAME).createNewFile()
        // createNewFile() creates an empty file (length() == 0)
        assertFalse(repo.isModelReady())
    }

    // ケース3: .litertlm ファイルが存在し size>0 → isModelReady() == true
    @Test
    fun isModelReady_fileExistsWithContent_returnsTrue() {
        File(filesDir, ModelConfig.MODEL_FILENAME).writeBytes(ByteArray(1) { 0x00 })
        assertTrue(repo.isModelReady())
    }

    // ケース4: getModelPath() が filesDir/MODEL_FILENAME の絶対パスを返す
    @Test
    fun getModelPath_returnsAbsolutePathUnderFilesDir() {
        val expected = File(filesDir, ModelConfig.MODEL_FILENAME).absolutePath
        assertEquals(expected, repo.getModelPath())
    }

    // ケース5: getMmprojPath() が空文字を返す（LiteRT-LM は単一ファイルで完結）
    @Test
    fun getMmprojPath_returnsEmptyString() {
        assertEquals("", repo.getMmprojPath())
    }
}
