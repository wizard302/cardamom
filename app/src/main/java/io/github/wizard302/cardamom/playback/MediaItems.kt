package io.github.wizard302.cardamom.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.wizard302.cardamom.data.media.Track

/** Metadata extras key carrying the track's file path (for favorites/M3U). */
const val EXTRA_PATH = "cardamom.path"

/** Metadata extras key with the MediaStore duration, for exact lyrics lookups. */
const val EXTRA_DURATION_MS = "cardamom.durationMs"

internal fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(contentUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri)
                .setExtras(
                    Bundle().apply {
                        putString(EXTRA_PATH, path)
                        putLong(EXTRA_DURATION_MS, durationMs)
                    },
                )
                .build(),
        )
        .build()
