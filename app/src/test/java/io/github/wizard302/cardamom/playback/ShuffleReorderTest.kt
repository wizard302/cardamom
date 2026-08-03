package io.github.wizard302.cardamom.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShuffleReorderTest {

    @Test
    fun `play next moves inserted items right after the current one`() {
        // Timeline 0..4; 3 and 4 were just inserted at timeline index 3 while
        // the shuffled order had scattered them.
        val order = listOf(3, 1, 0, 4, 2)
        val result = reorderShuffle(order, insertAt = 3, count = 2, current = 0, next = true)
        assertArrayEquals(intArrayOf(1, 0, 3, 4, 2), result)
    }

    @Test
    fun `play next keeps the inserted items in their own order`() {
        val order = listOf(0, 4, 1, 3, 2)
        val result = reorderShuffle(order, insertAt = 3, count = 2, current = 1, next = true)
        assertArrayEquals(intArrayOf(0, 1, 3, 4, 2), result)
    }

    @Test
    fun `insertion right after the current item is left alone`() {
        val order = listOf(2, 0, 3, 1)
        val result = reorderShuffle(order, insertAt = 3, count = 1, current = 0, next = true)
        assertArrayEquals(intArrayOf(2, 0, 3, 1), result)
    }

    @Test
    fun `an insertion into an empty queue is left shuffled`() {
        // Nothing was playing, so there is no anchor to follow: whatever order
        // shuffle picked for the new items stands.
        assertNull(reorderShuffle(listOf(1, 0), insertAt = 0, count = 2, current = 0, next = true))
    }

    @Test
    fun `add to queue puts the inserted items last`() {
        val order = listOf(3, 1, 0, 4, 2)
        val result = reorderShuffle(order, insertAt = 3, count = 2, current = 0, next = false)
        assertArrayEquals(intArrayOf(1, 0, 2, 3, 4), result)
    }

    @Test
    fun `an order missing the inserted indices is rejected`() {
        assertNull(reorderShuffle(listOf(0, 1, 2), insertAt = 3, count = 1, current = 0, next = true))
    }

    @Test
    fun `an order missing the current index is rejected`() {
        assertNull(reorderShuffle(listOf(0, 1, 2), insertAt = 2, count = 1, current = 5, next = true))
    }

    @Test
    fun `an empty insertion is rejected`() {
        assertNull(reorderShuffle(listOf(0, 1), insertAt = 1, count = 0, current = 0, next = true))
    }
}
