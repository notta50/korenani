package com.example.gemma4viewer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * JVMユニットテスト: LlamaEngine のシグネチャをリフレクションで検証する。
 *
 * NOTE: System.loadLibrary("llama-jni") はJVM環境では呼び出せないため、
 * クラスをインスタンス化せずにリフレクションでメソッド存在を確認する。
 */
class LlamaEngineTest {

    private val clazz = LlamaEngine::class.java

    @Test
    fun `nativeLoad exists with correct signature`() {
        val method = clazz.getDeclaredMethod("nativeLoad", String::class.java)
        assertNotNull(method)
        assertEquals(Int::class.java, method.returnType)
        assert(Modifier.isNative(method.modifiers)) {
            "nativeLoad must be declared as external (native)"
        }
    }

    @Test
    fun `nativePrepare exists with correct signature`() {
        val method = clazz.getDeclaredMethod("nativePrepare", Int::class.java, Int::class.java)
        assertNotNull(method)
        assertEquals(Int::class.java, method.returnType)
        assert(Modifier.isNative(method.modifiers)) {
            "nativePrepare must be declared as external (native)"
        }
    }

    @Test
    fun `nativeSystemInfo exists with correct signature`() {
        val method = clazz.getDeclaredMethod("nativeSystemInfo")
        assertNotNull(method)
        assertEquals(String::class.java, method.returnType)
        assert(Modifier.isNative(method.modifiers)) {
            "nativeSystemInfo must be declared as external (native)"
        }
    }

    @Test
    fun `nativeLoadMmproj exists with correct signature`() {
        val method = clazz.getDeclaredMethod("nativeLoadMmproj", String::class.java)
        assertNotNull(method)
        assertEquals(Int::class.java, method.returnType)
        assert(Modifier.isNative(method.modifiers)) {
            "nativeLoadMmproj must be declared as external (native)"
        }
    }

    @Test
    fun `nativeProcessImageTurn exists with correct signature`() {
        val method = clazz.getDeclaredMethod(
            "nativeProcessImageTurn",
            ByteArray::class.java,
            Int::class.java,
            Int::class.java,
            String::class.java
        )
        assertNotNull(method)
        assertEquals(Int::class.java, method.returnType)
        assert(Modifier.isNative(method.modifiers)) {
            "nativeProcessImageTurn must be declared as external (native)"
        }
    }

    @Test
    fun `nativeGenerateNextToken exists with correct signature`() {
        val method = clazz.getDeclaredMethod("nativeGenerateNextToken")
        assertNotNull(method)
        assertEquals(String::class.java, method.returnType)
        assert(Modifier.isNative(method.modifiers)) {
            "nativeGenerateNextToken must be declared as external (native)"
        }
    }

    @Test
    fun `nativeUnload exists with correct signature`() {
        val method = clazz.getDeclaredMethod("nativeUnload")
        assertNotNull(method)
        assertEquals(Void.TYPE, method.returnType)
        assert(Modifier.isNative(method.modifiers)) {
            "nativeUnload must be declared as external (native)"
        }
    }

    @Test
    fun `class has exactly 7 external methods`() {
        val nativeMethods = clazz.declaredMethods.filter { Modifier.isNative(it.modifiers) }
        assertEquals(
            "LlamaEngine must declare exactly 7 external (native) methods",
            7,
            nativeMethods.size
        )
    }
}
