package io.github.wizard302.cardamom.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumArtistTest {

    private fun track(artist: String, albumArtist: String = "") = Track(
        id = 0,
        title = "T",
        artist = artist,
        artistId = 0,
        album = "Album",
        albumId = 0,
        durationMs = 0,
        trackNumber = 0,
        year = 0,
        dateAdded = 0,
        path = "",
        sizeBytes = 0,
        bitrate = 0,
        albumArtist = albumArtist,
    )

    @Test
    fun prefersTheAlbumArtistTag() {
        val tracks = listOf(track("Guest"), track("Band", albumArtist = "Band"))
        assertEquals("Band", albumArtistOf(tracks))
    }

    @Test
    fun fallsBackToTheMostCommonArtist() {
        val tracks = listOf(track("Guest"), track("Band"), track("Band"))
        assertEquals("Band", albumArtistOf(tracks))
    }

    @Test
    fun emptyAlbumHasNoArtist() {
        assertEquals("", albumArtistOf(emptyList()))
    }
}
