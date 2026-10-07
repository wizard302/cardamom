package io.github.wizard302.cardamom.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseTrackOrderTest {

    private fun medium(position: Int, vararg titles: String) =
        MbMedium(
            position = position,
            tracks = titles.mapIndexed { i, t -> MbTrack(position = i + 1, title = t) },
        )

    @Test
    fun multiDiscReleaseKeepsDiscsTogether() {
        val media = listOf(medium(1, "1-1", "1-2", "1-3"), medium(2, "2-1", "2-2"))
        assertEquals(
            listOf("1-1", "1-2", "1-3", "2-1", "2-2"),
            media.releaseTrackOrder().map { it.title },
        )
    }

    @Test
    fun mediaAreOrderedByDiscNumber() {
        val media = listOf(medium(2, "2-1"), medium(1, "1-1"))
        assertEquals(listOf("1-1", "2-1"), media.releaseTrackOrder().map { it.title })
    }

    @Test
    fun tracksAreSortedWithinADisc() {
        val media = listOf(
            MbMedium(
                position = 1,
                tracks = listOf(MbTrack(position = 2, title = "b"), MbTrack(position = 1, title = "a")),
            ),
        )
        assertEquals(listOf("a", "b"), media.releaseTrackOrder().map { it.title })
    }

    @Test
    fun missingDiscNumbersKeepResponseOrder() {
        val media = listOf(medium(0, "x"), medium(0, "y"))
        assertEquals(listOf("x", "y"), media.releaseTrackOrder().map { it.title })
    }
}
