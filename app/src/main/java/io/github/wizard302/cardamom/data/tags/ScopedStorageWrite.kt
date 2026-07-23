package io.github.wizard302.cardamom.data.tags

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Runs [write] with scoped-storage consent for the given [uris]:
 * - API 30+: a single batch [MediaStore.createWriteRequest] consent dialog.
 * - API 29: run, and on [RecoverableSecurityException] request consent and retry.
 * - API ≤ 28: relies on the WRITE_EXTERNAL_STORAGE runtime permission.
 *
 * [requestConsent] must surface the [IntentSender] to the UI and return whether
 * the user granted it. [write] performs the actual file writes and returns
 * whether they all succeeded.
 */
suspend fun writeWithScopedConsent(
    context: Context,
    uris: List<Uri>,
    requestConsent: suspend (IntentSender) -> Boolean,
    write: suspend () -> Boolean,
): Boolean = when {
    uris.isEmpty() -> false

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
        val request = MediaStore.createWriteRequest(context.contentResolver, uris)
        if (requestConsent(request.intentSender)) write() else false
    }

    Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
        // On Q consent is granted per URI, so a multi-file batch can throw once
        // per file. Keep retrying until the batch completes, the user declines,
        // or every URI has had its chance (guards against a pathological loop).
        var result: Boolean? = null
        var consentsLeft = uris.size
        while (result == null) {
            result = try {
                write()
            } catch (e: RecoverableSecurityException) {
                consentsLeft--
                val sender = e.userAction.actionIntent.intentSender
                when {
                    consentsLeft < 0 -> false
                    requestConsent(sender) -> null // consent granted — retry
                    else -> false
                }
            }
        }
        result
    }

    else -> write()
}
