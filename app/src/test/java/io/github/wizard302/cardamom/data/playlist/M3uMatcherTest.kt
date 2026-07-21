package io.github.wizard302.cardamom.data.playlist

import io.github.wizard302.cardamom.data.media.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uMatcherTest {

    private fun track(id: Long, path: String, title: String = "T", artist: String = "A"): Track =
        Track(
            id = id,
            title = title,
            artist = artist,
            artistId = 0,
            album = "Album",
            albumId = 0,
            durationMs = 0,
            trackNumber = 0,
            year = 0,
            dateAdded = 0,
            path = path,
            sizeBytes = 0,
            bitrate = 0,
        )

    @Test
    fun `matches by exact absolute path`() {
        val library = listOf(track(1, "/music/rock/a.mp3"))
        val result = M3uMatcher.match(
            listOf(ParsedM3uEntry("/music/rock/a.mp3", null, null, null)),
            library,
        )
        assertEquals(1L, result.single().track?.id)
    }

    @Test
    fun `resolves relative entry against playlist dir`() {
        val library = listOf(track(2, "/music/rock/a.mp3"))
        val result = M3uMatcher.match(
            listOf(ParsedM3uEntry("rock/a.mp3", null, null, null)),
            library,
            playlistDir = "/music",
        )
        assertEquals(2L, result.single().track?.id)
    }

    @Test
    fun `falls back to filename and parent suffix match`() {
        val library = listOf(
            track(3, "/storage/emulated/0/Music/rock/song.mp3"),
        )
        val result = M3uMatcher.match(
            listOf(ParsedM3uEntry("/old/path/rock/song.mp3", null, null, null)),
            library,
        )
        assertEquals(3L, result.single().track?.id)
    }

    @Test
    fun `disambiguates duplicate filenames by parent directory`() {
        val library = listOf(
            track(4, "/music/live/intro.mp3"),
            track(5, "/music/studio/intro.mp3"),
        )
        val result = M3uMatcher.match(
            listOf(ParsedM3uEntry("/elsewhere/studio/intro.mp3", null, null, null)),
            library,
        )
        assertEquals(5L, result.single().track?.id)
    }

    @Test
    fun `falls back to title and artist`() {
        val library = listOf(track(6, "/music/x.mp3", title = "Wish You Were Here", artist = "Pink Floyd"))
        val result = M3uMatcher.match(
            listOf(ParsedM3uEntry("/gone/y.mp3", 120, "pink floyd", "wish you were here")),
            library,
        )
        assertEquals(6L, result.single().track?.id)
    }

    @Test
    fun `reports unresolved entries`() {
        val library = listOf(track(7, "/music/a.mp3", title = "A", artist = "X"))
        val result = M3uMatcher.match(
            listOf(ParsedM3uEntry("/missing/b.mp3", null, "Nobody", "Nothing")),
            library,
        )
        assertNull(result.single().track)
    }
}
