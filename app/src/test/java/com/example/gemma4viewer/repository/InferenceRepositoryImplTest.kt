package com.example.gemma4viewer.repository

import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceRepositoryImplTest {

    @Test
    fun `computeNThreads returns value between 1 and 8`() {
        val result = computeNThreads()
        assertTrue("nThreadsは1以上である必要がある", result >= 1)
        assertTrue("nThreadsは8以下である必要がある", result <= 8)
    }

    @Test
    fun `InferenceRepositoryImpl implements InferenceRepository`() {
        assertTrue(
            InferenceRepository::class.java.isAssignableFrom(InferenceRepositoryImpl::class.java)
        )
    }
}
