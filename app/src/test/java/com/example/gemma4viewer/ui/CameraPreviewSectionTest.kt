package com.example.gemma4viewer.ui

import com.example.gemma4viewer.viewmodel.AppState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `resolveIsCaptureEnabled returns false when Inferencing`() {
        assertFalse(resolveIsCaptureEnabled(AppState.Inferencing))
    }

    @Test
    fun `resolveIsCaptureEnabled returns true when ModelReady`() {
        assertTrue(resolveIsCaptureEnabled(AppState.ModelReady))
    }

    @Test
    fun `resolveIsCaptureEnabled returns true when InferenceResult`() {
        assertTrue(resolveIsCaptureEnabled(AppState.InferenceResult("text")))
    }

    @Test
    fun `resolveIsCaptureEnabled returns true when InferenceError`() {
        assertTrue(resolveIsCaptureEnabled(AppState.InferenceError("err")))
    }
}
