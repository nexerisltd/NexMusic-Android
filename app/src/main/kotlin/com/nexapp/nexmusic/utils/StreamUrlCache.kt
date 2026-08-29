package com.nexapp.nexmusic.utils

import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber

/**
 * Short-TTL cache for resolved playback data (stream URL + format), keyed by videoId+quality.
 *
 * WHY: resolving a stream URL walks the whole client fallback chain (§YTPlayerUtils) which is
 * expensive (multiple network round-trips, possibly PoToken generation via WebView). If the same
 * track is requested again shortly after (re-entering a track, queue re-shuffle touching the same
 * song, widget/notification re-binding), we should reuse the already-resolved URL rather than
 * re-resolving from scratch.
 *
 * TTL SAFETY: the cache entry's own TTL is capped to the actual `streamExpiresInSeconds` YouTube
 * returned for that URL (typically a few hours), MINUS a safety margin, so we never hand out a
 * URL that's about to expire or (worse) already has. PoToken-bound URLs expire with the token, so
 * respecting the server-provided expiry - not inventing our own longer TTL - is what makes this
 * safe to use unconditionally.
 */
object StreamUrlCache {
    private const val TAG = "StreamUrlCache"

    // Don't trust a URL in its last 60s of life - by the time playback actually starts,
    // buffers, or a download picks it up, it could already be expired server-side.
    private const val SAFETY_MARGIN_SECONDS = 60L

    // Hard ceiling regardless of what the server claims, so a single bad response can't pin a
    // stale entry in memory indefinitely.
    private const val MAX_TTL_SECONDS = 6 * 60 * 60L // 6 hours

    private data class Entry(
        val data: YTPlayerUtils.PlaybackData,
        val expiresAtMillis: Long,
    )

    private val cache = ConcurrentHashMap<String, Entry>()

    private fun key(videoId: String, audioQuality: String) = "$videoId|$audioQuality"

    fun get(videoId: String, audioQuality: String): YTPlayerUtils.PlaybackData? {
        val k = key(videoId, audioQuality)
        val entry = cache[k] ?: return null
        if (System.currentTimeMillis() >= entry.expiresAtMillis) {
            cache.remove(k)
            Timber.tag(TAG).d("Cache expired for $videoId, evicting")
            return null
        }
        Timber.tag(TAG).d("Cache hit for $videoId")
        return entry.data
    }

    fun put(videoId: String, audioQuality: String, data: YTPlayerUtils.PlaybackData) {
        val safeTtlSeconds = data.streamExpiresInSeconds.toLong()
            .minus(SAFETY_MARGIN_SECONDS)
            .coerceIn(0L, MAX_TTL_SECONDS)
        if (safeTtlSeconds <= 0L) {
            // Already effectively expired or expiry window too tight to be worth caching.
            return
        }
        val k = key(videoId, audioQuality)
        cache[k] = Entry(data, System.currentTimeMillis() + safeTtlSeconds * 1000L)
        Timber.tag(TAG).d("Cached stream for $videoId, ttl=${safeTtlSeconds}s")
    }

    fun invalidate(videoId: String, audioQuality: String) {
        cache.remove(key(videoId, audioQuality))
    }

    fun clear() = cache.clear()
}
