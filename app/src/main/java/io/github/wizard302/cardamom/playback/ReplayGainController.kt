package io.github.wizard302.cardamom.playback

import android.content.Context
import android.net.Uri
import com.kyant.taglib.TagLib
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.wizard302.cardamom.data.settings.ReplayGainMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/** Track and album ReplayGain values in dB, either of which may be absent. */
data class ReplayGain(val trackDb: Float?, val albumDb: Float?)

/**
 * Parses a ReplayGain tag value into decibels.
 *
 * Real-world files are messy: "-6.50 dB", "+2dB", "3,1 dB" (comma decimal),
 * bare numbers, and the occasional Unicode minus. Anything else is null, which
 * callers treat as "no gain information".
 */
fun parseGainDb(raw: String?): Float? {
    if (raw.isNullOrBlank()) return null
    val normalized = raw.trim()
        .removeSuffix("dB").removeSuffix("DB").removeSuffix("db")
        .trim()
        .replace('−', '-') // Unicode minus
        .replace(',', '.')
        .replace(" ", "")
        .removePrefix("+")
    val value = normalized.toFloatOrNull() ?: return null
    return if (value.isFinite()) value else null
}

/**
 * Reads ReplayGain tags and turns them into a linear player volume.
 *
 * Attenuation only: ExoPlayer's volume caps at 1.0, so a positive net gain
 * cannot amplify without an audio processor — deliberately out of scope, and
 * stated in the Settings subtitle.
 */
@Singleton
class ReplayGainController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Access is confined to the IO reads below, but the map is touched from
    // whichever dispatcher thread runs them, so guard it.
    private val cache = object : LinkedHashMap<String, ReplayGain>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ReplayGain>) =
            size > MAX_CACHE_ENTRIES
    }

    /** ReplayGain tags for [uri], cached by [key] (the file path or media id). */
    suspend fun gainFor(uri: Uri, key: String): ReplayGain = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[key] }?.let { return@withContext it }
        val gain = readGain(uri)
        synchronized(cache) { cache[key] = gain }
        gain
    }

    private fun readGain(uri: Uri): ReplayGain = runCatching {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@runCatching null
        val properties = pfd.use {
            TagLib.getMetadata(it.dup().detachFd(), readPictures = false)?.propertyMap
        } ?: return@runCatching null
        // TagLib surfaces the ID3 TXXX frames and the Vorbis comments under the
        // same names, but capitalisation varies between taggers.
        val byUpperKey = properties.entries.associate { (k, v) -> k.uppercase() to v }
        ReplayGain(
            trackDb = parseGainDb(byUpperKey["REPLAYGAIN_TRACK_GAIN"]?.firstOrNull()),
            albumDb = parseGainDb(byUpperKey["REPLAYGAIN_ALBUM_GAIN"]?.firstOrNull()),
        )
    }.getOrNull() ?: ReplayGain(null, null)

    private companion object {
        const val MAX_CACHE_ENTRIES = 256
    }
}

/**
 * Linear volume for [gain] under [mode] with [preampDb] applied. Falls back to
 * 1.0 whenever the mode is OFF or the requested tag is missing, so a previous
 * track's attenuation is never left in place. Album mode falls back to the
 * track gain (and vice versa) rather than playing a file un-leveled.
 */
fun replayGainVolume(gain: ReplayGain?, mode: ReplayGainMode, preampDb: Float): Float {
    val db = when (mode) {
        ReplayGainMode.OFF -> null
        ReplayGainMode.TRACK -> gain?.trackDb ?: gain?.albumDb
        ReplayGainMode.ALBUM -> gain?.albumDb ?: gain?.trackDb
    } ?: return 1f
    return 10f.pow((db + preampDb) / 20f).coerceIn(0f, 1f)
}
