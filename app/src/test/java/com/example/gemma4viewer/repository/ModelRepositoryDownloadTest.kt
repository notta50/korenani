package com.example.gemma4viewer.repository

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelRepositoryDownloadTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        filesDir = tempFolder.newFolder("filesDir")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun makeRepo(): ModelRepositoryImpl {
        val baseUrl = server.url("/").toString()
        return ModelRepositoryImpl(
            filesDir = filesDir,
            modelUrl = baseUrl + "model.gguf",
            mmprojUrl = baseUrl + "mmproj.gguf"
        )
    }

    // テスト1: 正常ダウンロード完了 → Progress が複数 emit され最後に Finished
    @Test
    fun downloadModels_success_emitsProgressThenFinished() {
        // model.gguf レスポンス (小さいデータ)
        val modelBody = Buffer().apply { write(ByteArray(1024) { it.toByte() }) }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Length", "1024")
                .setBody(modelBody)
        )
        // mmproj.gguf レスポンス
        val mmprojBody = Buffer().apply { write(ByteArray(512) { it.toByte() }) }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Length", "512")
                .setBody(mmprojBody)
        )

        val results = mutableListOf<DownloadState>()
        runBlocking {
            makeRepo().downloadModels().toList(results)
        }

        // 少なくとも1つの Progress と最後が Finished
        assertTrue("Progress が 1 件以上 emit されること", results.any { it is DownloadState.Progress })
        assertTrue("最後の状態が Finished であること", results.last() is DownloadState.Finished)
    }

    // テスト2: ネットワーク失敗（500エラー）→ Failed が emit される
    @Test
    fun downloadModels_serverError_emitsFailed() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val results = mutableListOf<DownloadState>()
        runBlocking {
            makeRepo().downloadModels().toList(results)
        }

        assertTrue("Failed が emit されること", results.any { it is DownloadState.Failed })
    }

    // テスト3: 既存ファイルがある場合 Range ヘッダーが送信される
    @Test
    fun downloadModels_existingFile_sendsRangeHeader() {
        // 既存の部分ファイルを作成 (100バイト)
        val existingModelFile = File(filesDir, "model.gguf")
        existingModelFile.writeBytes(ByteArray(100) { 0 })

        // サーバーは 206 Partial Content を返す
        val remainingBody = Buffer().apply { write(ByteArray(924) { it.toByte() }) }
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .addHeader("Content-Length", "924")
                .setBody(remainingBody)
        )
        // mmproj.gguf レスポンス
        val mmprojBody = Buffer().apply { write(ByteArray(512) { it.toByte() }) }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Length", "512")
                .setBody(mmprojBody)
        )

        runBlocking {
            makeRepo().downloadModels().toList()
        }

        // model.gguf リクエストの Range ヘッダーを確認
        val modelRequest = server.takeRequest()
        assertEquals("bytes=100-", modelRequest.getHeader("Range"))
    }

    // テスト4: Content-Length が -1 (chunked) の場合 percent = -1 で Progress が emit される
    @Test
    fun downloadModels_chunkedTransfer_emitsProgressWithMinusOne() {
        // chunked エンコーディングで Content-Length を省略
        // MockWebServer では setChunkedBody を使うことで Transfer-Encoding: chunked になる
        val modelData = ByteArray(1024) { it.toByte() }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setChunkedBody(Buffer().apply { write(modelData) }, 256)
                // setChunkedBody は Transfer-Encoding: chunked を設定し Content-Length は省略
        )
        val mmprojData = ByteArray(512) { it.toByte() }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setChunkedBody(Buffer().apply { write(mmprojData) }, 128)
        )

        val results = mutableListOf<DownloadState>()
        runBlocking {
            makeRepo().downloadModels().toList(results)
        }

        // percent = -1 の Progress が少なくとも1件あること
        val unknownProgress = results.filterIsInstance<DownloadState.Progress>().filter { it.percent == -1 }
        assertTrue("percent=-1 の Progress が 1 件以上 emit されること", unknownProgress.isNotEmpty())
    }
}
