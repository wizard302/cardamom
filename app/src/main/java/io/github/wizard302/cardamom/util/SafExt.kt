package io.github.wizard302.cardamom.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Reads a SAF document's human-readable display name, or null if unavailable. */
fun Context.queryDisplayName(uri: Uri): String? =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
