package io.github.wizard302.cardamom.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import io.github.wizard302.cardamom.R
import io.github.wizard302.cardamom.data.media.Track

/**
 * Opens the system share sheet with the track's audio file. The MediaStore
 * content URI is passed with a read grant, so the receiving app can open the
 * file without any storage permission of its own.
 */
fun Context.shareTrack(track: Track) {
    val uri = track.contentUri
    val mimeType = runCatching { contentResolver.getType(uri) }.getOrNull() ?: "audio/*"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, "${track.artist} - ${track.title}")
        // ClipData makes the grant carry through the chooser to the picked app.
        clipData = ClipData.newRawUri(track.title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, getString(R.string.menu_share))
    try {
        startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        // No app can receive audio; nothing sensible to do.
    }
}
