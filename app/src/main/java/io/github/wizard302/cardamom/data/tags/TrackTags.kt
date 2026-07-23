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

/**
 * Best-effort MIME detection from image magic bytes. Cover Art Archive serves
 * both JPEG and PNG, so the type must be sniffed, not assumed.
 */
fun sniffImageMime(data: ByteArray): String = when {
    data.size >= 4 &&
        data[0] == 0x89.toByte() && data[1] == 0x50.toByte() &&
        data[2] == 0x4E.toByte() && data[3] == 0x47.toByte() -> "image/png"

    data.size >= 12 &&
        data[0] == 0x52.toByte() && data[1] == 0x49.toByte() &&
        data[2] == 0x46.toByte() && data[3] == 0x46.toByte() &&
        data[8] == 0x57.toByte() && data[9] == 0x45.toByte() &&
        data[10] == 0x42.toByte() && data[11] == 0x50.toByte() -> "image/webp"

    else -> "image/jpeg"
}

/** How the embedded cover should change when saving. */
sealed interface CoverEdit {
    /** Leave the existing cover untouched. */
    data object Keep : CoverEdit

    /** Strip all embedded pictures. */
    data object Remove : CoverEdit

    /** Replace the front cover with [data] of the given [mimeType]. */
    data class Replace(val data: ByteArray, val mimeType: String) : CoverEdit
}
