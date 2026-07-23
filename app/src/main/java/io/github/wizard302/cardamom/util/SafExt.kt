package io.github.wizard302.cardamom.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns

/** Reads a SAF document's human-readable display name, or null if unavailable. */
fun Context.queryDisplayName(uri: Uri): String? =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

/**
 * Best-effort real file path for a SAF document [uri]. Only the platform's
 * external-storage provider is mapped (`primary:` → the shared storage root,
 * other volumes → `/storage/<volume>`); for any other provider this returns
 * null and callers fall back to absolute paths / suffix matching.
 */
fun documentUriToFilePath(uri: Uri): String? = runCatching {
    if (uri.authority != "com.android.externalstorage.documents") return null
    val docId = DocumentsContract.getDocumentId(uri)
    val split = docId.split(':', limit = 2)
    val volume = split[0]
    val relative = split.getOrElse(1) { "" }
    val root = if (volume.equals("primary", ignoreCase = true)) {
        @Suppress("DEPRECATION")
        Environment.getExternalStorageDirectory().absolutePath
    } else {
        "/storage/$volume"
    }
    if (relative.isEmpty()) root else "$root/$relative"
}.getOrNull()
