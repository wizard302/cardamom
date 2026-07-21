package io.github.wizard302.cardamom.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/** Real User-Agent required by MusicBrainz and LRCLIB etiquette. */
const val CARDAMOM_USER_AGENT = "Cardamom/1.0 (https://github.com/wizard302/cardamom)"

class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", CARDAMOM_USER_AGENT)
            .build()
        return chain.proceed(request)
    }
}

/**
 * Throttles requests to [host] to at most one per [minIntervalMs].
 * MusicBrainz requires no more than one request per second.
 */
class RateLimitInterceptor(
    private val host: String,
    private val minIntervalMs: Long = 1_000L,
) : Interceptor {
    private val lock = Any()
    private var lastRequestAt = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.request().url.host == host) {
            val wait: Long
            synchronized(lock) {
                val now = System.currentTimeMillis()
                val elapsed = now - lastRequestAt
                wait = (minIntervalMs - elapsed).coerceAtLeast(0)
                lastRequestAt = now + wait
            }
            if (wait > 0) {
                try {
                    Thread.sleep(wait)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw java.io.InterruptedIOException("Rate limiter interrupted")
                }
            }
        }
        return chain.proceed(chain.request())
    }
}
