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
        try {
            write()
        } catch (e: RecoverableSecurityException) {
            val sender = e.userAction.actionIntent.intentSender
            if (requestConsent(sender)) write() else false
        }
    }

    else -> write()
}
