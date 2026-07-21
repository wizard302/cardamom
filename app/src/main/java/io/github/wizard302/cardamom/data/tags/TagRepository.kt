package io.github.wizard302.cardamom.data.tags

import android.app.RecoverableSecurityException
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes embedded tags through TagLib. TagLib works on a file
 * descriptor, so the file is opened via [android.content.ContentResolver] and
 * the fd is handed straight to the native binding — the file is never copied.
 *
 * Callers must obtain write consent (scoped storage) before [write]; opening a
 * non-owned file "rw" throws until the user grants it.
 */
@Singleton
class TagRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Front cover bytes and the read-back tag fields for [uri], or null on failure. */
    data class ReadResult(val tags: TrackTags, val cover: ByteArray?)

    suspend fun read(uri: Uri): ReadResult? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val metadata = TagLib.getMetadata(pfd.fd, readPictures = true) ?: return@use null
                val pm = metadata.propertyMap
                fun first(key: String) = pm[key]?.firstOrNull().orEmpty()
                val cover = metadata.pictures
                    .firstOrNull { it.pictureType == FRONT_COVER }
                    ?: metadata.pictures.firstOrNull()
                ReadResult(
                    tags = TrackTags(
                        title = first("TITLE"),
                        artist = first("ARTIST"),
                        album = first("ALBUM"),
                        albumArtist = first("ALBUMARTIST"),
                        trackNumber = first("TRACKNUMBER"),
                        discNumber = first("DISCNUMBER"),
                        year = first("DATE"),
                        genre = first("GENRE"),
                    ),
                    cover = cover?.data,
                )
            }
        }.getOrNull()
    }

    /**
     * Writes [tags] (and optional [coverEdit]) into [uri]; returns success.
     *
     * On API 29 a [RecoverableSecurityException] is rethrown so the caller can
     * launch the system's consent dialog and retry; other failures return false.
     */
    suspend fun write(uri: Uri, tags: TrackTags, coverEdit: CoverEdit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    val fd = pfd.fd
                    val metadata = TagLib.getMetadata(fd, readPictures = false) ?: return@use false
                    val pm = metadata.propertyMap
                    pm.setOrRemove("TITLE", tags.title)
                    pm.setOrRemove("ARTIST", tags.artist)
                    pm.setOrRemove("ALBUM", tags.album)
                    pm.setOrRemove("ALBUMARTIST", tags.albumArtist)
                    pm.setOrRemove("TRACKNUMBER", tags.trackNumber)
                    pm.setOrRemove("DISCNUMBER", tags.discNumber)
                    pm.setOrRemove("DATE", tags.year)
                    pm.setOrRemove("GENRE", tags.genre)
                    val propsOk = TagLib.savePropertyMap(fd, pm)

                    val coverOk = when (coverEdit) {
                        CoverEdit.Keep -> true
                        CoverEdit.Remove -> TagLib.savePictures(fd, emptyArray())
                        is CoverEdit.Replace -> TagLib.savePictures(
                            fd,
                            arrayOf(
                                Picture(
                                    data = coverEdit.data,
                                    description = "",
                                    pictureType = FRONT_COVER,
                                    mimeType = coverEdit.mimeType,
                                ),
                            ),
                        )
                    }
                    propsOk && coverOk
                } ?: false
            } catch (t: Throwable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    t is RecoverableSecurityException
                ) {
                    throw t
                }
                false
            }
        }

    /** Asks MediaStore to re-index the file so edited tags surface app-wide. */
    fun notifyFileChanged(path: String) {
        MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
    }

    private fun HashMap<String, Array<String>>.setOrRemove(key: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) remove(key) else this[key] = arrayOf(trimmed)
    }

    private companion object {
        const val FRONT_COVER = "Front Cover"
    }
}
