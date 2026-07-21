package io.github.wizard302.cardamom.data.tags

/** Editable tag fields for a single track, read from / written to the file. */
data class TrackTags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
    val year: String = "",
    val genre: String = "",
)

/** How the embedded cover should change when saving. */
sealed interface CoverEdit {
    /** Leave the existing cover untouched. */
    data object Keep : CoverEdit

    /** Strip all embedded pictures. */
    data object Remove : CoverEdit

    /** Replace the front cover with [data] of the given [mimeType]. */
    data class Replace(val data: ByteArray, val mimeType: String) : CoverEdit
}
