package io.github.wizard302.cardamom.data.media

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val dateAdded: Long,
    /** Absolute path from MediaStore DATA column; used for the Folders tab and M3U later. */
    val path: String,
) {
    val contentUri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    val albumArtUri: Uri
        get() = ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)

    companion object {
        private val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val year: Int,
    val trackCount: Int,
    val artUri: Uri,
)

data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val trackCount: Int,
)
