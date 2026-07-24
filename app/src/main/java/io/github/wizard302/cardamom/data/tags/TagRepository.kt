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
            withTagLibFd(uri, "r") { fd ->
                val metadata = TagLib.getMetadata(fd, readPictures = true) ?: return@withTagLibFd null
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
                // Read the current property map first so unedited keys survive.
                val pm = withTagLibFd(uri, "r") { fd ->
                    TagLib.getMetadata(fd, readPictures = false)?.propertyMap
                } ?: return@withContext false

                pm.setOrRemove("TITLE", tags.title)
                pm.setOrRemove("ARTIST", tags.artist)
                pm.setOrRemove("ALBUM", tags.album)
                pm.setOrRemove("ALBUMARTIST", tags.albumArtist)
                pm.setOrRemove("TRACKNUMBER", tags.trackNumber)
                pm.setOrRemove("DISCNUMBER", tags.discNumber)
                pm.setOrRemove("DATE", tags.year)
                pm.setOrRemove("GENRE", tags.genre)

                val propsOk = withTagLibFd(uri, "rw") { fd -> TagLib.savePropertyMap(fd, pm) } ?: false

                val coverOk = when (coverEdit) {
                    CoverEdit.Keep -> true
                    CoverEdit.Remove ->
                        withTagLibFd(uri, "rw") { fd -> TagLib.savePictures(fd, emptyArray()) } ?: false
                    is CoverEdit.Replace ->
                        withTagLibFd(uri, "rw") { fd ->
                            TagLib.savePictures(
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
                        } ?: false
                }
                propsOk && coverOk
            } catch (t: Throwable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    t is RecoverableSecurityException
                ) {
                    throw t
                }
                false
            }
        }

    /**
     * Opens [uri] and hands TagLib a duplicated, ownership-detached fd. TagLib
     * `fdopen`s the descriptor and closes it itself, so a plain
     * [android.os.ParcelFileDescriptor] fd would trip fdsan's double-ownership
     * check and abort the process. Each native call needs its own fd because
     * TagLib closes the one it is given.
     */
    private inline fun <T> withTagLibFd(uri: Uri, mode: String, block: (fd: Int) -> T): T? {
        val pfd = context.contentResolver.openFileDescriptor(uri, mode) ?: return null
        return pfd.use { block(it.dup().detachFd()) }
    }

    /** Reads embedded lyrics (USLT/LYRICS) from [uri], or null when absent. */
    suspend fun readLyrics(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            withTagLibFd(uri, "r") { fd ->
                val pm = TagLib.getMetadata(fd, readPictures = false)?.propertyMap
                pm?.get("LYRICS")?.firstOrNull()
                    ?: pm?.get("UNSYNCEDLYRICS")?.firstOrNull()
            }
        }.getOrNull()
    }

    /**
     * Writes [text] into the file's lyrics tag (TagLib maps `LYRICS` to USLT for
     * ID3 and to the `LYRICS` field for Vorbis/FLAC/MP4); returns success. LRC
     * text is stored verbatim — players commonly read timestamps back out of
     * USLT. Same consent rules as [write]: API 29 rethrows
     * [RecoverableSecurityException] for the caller to handle.
     */
    suspend fun writeLyrics(uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val pm = withTagLibFd(uri, "r") { fd ->
                TagLib.getMetadata(fd, readPictures = false)?.propertyMap
            } ?: return@withContext false

            pm.setOrRemove("LYRICS", text)
            // Some writers leave a stale copy under this alias; drop it so the
            // file has exactly one lyrics field.
            pm.remove("UNSYNCEDLYRICS")

            withTagLibFd(uri, "rw") { fd -> TagLib.savePropertyMap(fd, pm) } ?: false
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
