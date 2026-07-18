package io.github.wizard302.cardamom.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.wizard302.cardamom.data.media.Track

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
                .build(),
        )
        .build()
