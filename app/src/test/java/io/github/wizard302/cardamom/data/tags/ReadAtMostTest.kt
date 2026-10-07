package io.github.wizard302.cardamom.data.tags

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class ReadAtMostTest {

    @Test
    fun returnsStreamWithinLimit() {
        val data = ByteArray(20_000) { it.toByte() }
        assertArrayEquals(data, ByteArrayInputStream(data).readAtMost(20_000))
    }

    @Test
    fun rejectsStreamOverLimit() {
        assertNull(ByteArrayInputStream(ByteArray(20_001)).readAtMost(20_000))
    }

    @Test
    fun readsEmptyStream() {
        assertArrayEquals(ByteArray(0), ByteArrayInputStream(ByteArray(0)).readAtMost(10))
    }
}
