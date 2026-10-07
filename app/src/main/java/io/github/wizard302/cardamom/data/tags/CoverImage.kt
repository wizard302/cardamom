package io.github.wizard302.cardamom.data.tags

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Longest side of a cover embedded from the picker. */
private const val MAX_COVER_DIMENSION = 1200

/** A picked image at most this large (and small enough in pixels) is embedded as-is. */
private const val MAX_ORIGINAL_BYTES = 1_000_000

private const val JPEG_QUALITY = 90

private val PASS_THROUGH_MIME_TYPES = setOf("image/jpeg", "image/png")

/**
 * Turns a user-picked image into a cover fit for embedding. Small JPEG/PNG
 * files are kept byte-for-byte; anything else (a 4000 px camera photo, HEIC,
 * WebP) is downscaled to [MAX_COVER_DIMENSION] and re-encoded as JPEG, since
 * the cover is written into every file of an album. Null when the image
 * cannot be decoded.
 */
suspend fun loadCoverForEmbedding(context: Context, uri: Uri): CoverEdit.Replace? =
    withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return@runCatching null

            val mime = resolver.getType(uri) ?: bounds.outMimeType
            if (mime != null && mime in PASS_THROUGH_MIME_TYPES && max(width, height) <= MAX_COVER_DIMENSION) {
                val original = resolver.openInputStream(uri)?.use { it.readAtMost(MAX_ORIGINAL_BYTES) }
                if (original != null) return@runCatching CoverEdit.Replace(original, mime)
            }

            var sample = 1
            while (max(width, height) / (sample * 2) >= MAX_COVER_DIMENSION) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val scale = MAX_COVER_DIMENSION.toFloat() / max(decoded.width, decoded.height)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).roundToInt().coerceAtLeast(1),
                    (decoded.height * scale).roundToInt().coerceAtLeast(1),
                    true,
                ).also { if (it !== decoded) decoded.recycle() }
            } else {
                decoded
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            scaled.recycle()
            CoverEdit.Replace(out.toByteArray(), "image/jpeg")
        }.getOrNull()
    }

/** The whole stream when it holds at most [limit] bytes, else null. */
internal fun InputStream.readAtMost(limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val n = read(buffer)
        if (n < 0) return out.toByteArray()
        if (out.size() + n > limit) return null
        out.write(buffer, 0, n)
    }
}
