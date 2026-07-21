package io.github.wizard302.cardamom.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A track match offered by the fetcher. */
data class TrackCandidate(
    val title: String,
    val artist: String,
    val album: String,
    val year: String,
    val releaseMbid: String?,
)

/** An album (release) match. */
data class AlbumCandidate(
    val releaseMbid: String,
    val title: String,
    val artist: String,
    val year: String,
    val trackCount: Int,
)

data class ReleaseTrack(
    val position: Int,
    val title: String,
    val artist: String,
)

data class AlbumReleaseDetail(
    val releaseMbid: String,
    val title: String,
    val artist: String,
    val year: String,
    val tracks: List<ReleaseTrack>,
)

/**
 * Online metadata lookups via MusicBrainz, with cover art from Cover Art
 * Archive and a Deezer fallback. Network calls run on IO and throw on failure
 * so callers can surface explicit error / not-found states.
 */
@Singleton
class MetadataRepository @Inject constructor(
    private val musicBrainz: MusicBrainzApi,
    private val deezer: DeezerApi,
    private val imageApi: ImageApi,
) {
    suspend fun searchTrack(artist: String, title: String): List<TrackCandidate> =
        withContext(Dispatchers.IO) {
            val query = buildString {
                append("recording:\"").append(title.lucene()).append('"')
                if (artist.isNotBlank()) {
                    append(" AND artist:\"").append(artist.lucene()).append('"')
                }
            }
            musicBrainz.searchRecordings(query).recordings.map { rec ->
                val release = rec.releases.firstOrNull()
                TrackCandidate(
                    title = rec.title,
                    artist = rec.artistCredit.displayName(),
                    album = release?.title.orEmpty(),
                    year = release?.date.toYear(),
                    releaseMbid = release?.id,
                )
            }.distinctBy { listOf(it.title, it.artist, it.album) }
        }

    suspend fun searchAlbum(artist: String, album: String): List<AlbumCandidate> =
        withContext(Dispatchers.IO) {
            val query = buildString {
                append("release:\"").append(album.lucene()).append('"')
                if (artist.isNotBlank()) {
                    append(" AND artist:\"").append(artist.lucene()).append('"')
                }
            }
            musicBrainz.searchReleases(query).releases.map { rel ->
                AlbumCandidate(
                    releaseMbid = rel.id,
                    title = rel.title,
                    artist = rel.artistCredit.displayName(),
                    year = rel.date.toYear(),
                    trackCount = rel.trackCount,
                )
            }
        }

    suspend fun getAlbumRelease(releaseMbid: String): AlbumReleaseDetail =
        withContext(Dispatchers.IO) {
            val rel = musicBrainz.getRelease(releaseMbid)
            val albumArtist = rel.artistCredit.displayName()
            val tracks = rel.media.flatMap { it.tracks }.map { track ->
                ReleaseTrack(
                    position = track.position,
                    title = track.title,
                    artist = track.artistCredit.displayName().ifBlank { albumArtist },
                )
            }.sortedBy { it.position }
            AlbumReleaseDetail(
                releaseMbid = rel.id,
                title = rel.title,
                artist = albumArtist,
                year = rel.date.toYear(),
                tracks = tracks,
            )
        }

    /**
     * Cover bytes for a release: Cover Art Archive first (by MBID), then a
     * Deezer search fallback keyed on artist/title. Null when nothing is found.
     */
    suspend fun fetchCover(releaseMbid: String?, artist: String, title: String): ByteArray? =
        withContext(Dispatchers.IO) {
            if (releaseMbid != null) {
                runCatching {
                    imageApi.fetch("https://coverartarchive.org/release/$releaseMbid/front-500")
                        .use { it.bytes() }
                }.getOrNull()?.let { return@withContext it }
            }
            val query = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
            if (query.isBlank()) return@withContext null
            runCatching {
                val coverUrl = deezer.search(query).data
                    .firstNotNullOfOrNull { it.album.coverXl?.takeIf(String::isNotBlank) }
                coverUrl?.let { imageApi.fetch(it).use { body -> body.bytes() } }
            }.getOrNull()
        }

    /** Escapes Lucene special characters that would break a MusicBrainz query. */
    private fun String.lucene(): String =
        replace("\\", "\\\\").replace("\"", "").trim()
}
