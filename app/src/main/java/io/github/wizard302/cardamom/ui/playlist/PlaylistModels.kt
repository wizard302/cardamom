package io.github.wizard302.cardamom.ui.playlist

import android.net.Uri
import io.github.wizard302.cardamom.data.media.Track

/**
 * A playlist/favorites entry joined against the current library. [track] is
 * non-null when the file still exists in the library (and thus is playable);
 * otherwise only the cached display fields are shown.
 */
data class ResolvedRow(
    /** Stable key: playlist row id for playlists, MediaStore id for favorites. */
    val key: Long,
    val mediaId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    /** Durable file path, kept even when the file is missing from the library. */
    val path: String,
    val albumArtUri: Uri?,
    val track: Track?,
)

/** Builds a library lookup keyed by MediaStore id with a path fallback. */
fun resolveTrack(byId: Map<Long, Track>, byPath: Map<String, Track>, mediaId: Long, path: String): Track? =
    byId[mediaId] ?: byPath[path]
