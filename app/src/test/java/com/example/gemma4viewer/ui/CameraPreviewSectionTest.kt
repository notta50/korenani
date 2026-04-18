package com.example.gemma4viewer.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CameraPreviewSectionTest {

    @Test
    fun `resolveCameraPermissionMessage returns non-null when permission denied`() {
        val message = resolveCameraPermissionMessage(hasPermission = false)
        assertNotNull("権限なし時にメッセージが返される必要がある", message)
    }

    @Test
    fun `resolveCameraPermissionMessage returns null when permission granted`() {
        val message = resolveCameraPermissionMessage(hasPermission = true)
        assertNull("権限あり時にnullが返される必要がある", message)
    }
}
