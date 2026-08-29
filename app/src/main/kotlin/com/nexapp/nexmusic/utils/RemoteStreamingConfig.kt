

package com.nexapp.nexmusic.utils

import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.MOBILE
import com.music.innertube.models.YouTubeClient.Companion.MWEB
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.music.innertube.models.YouTubeClient.Companion.WEB
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import timber.log.Timber

/**
 * Hotfix hook for which YouTube InnerTube client(s) are used to resolve playback.
 *
 * WHY THIS EXISTS
 * ----------------
 * YouTube periodically breaks specific client types without warning (2026-08: guest-only
 * ANDROID_VR clients started returning LOGIN_REQUIRED almost universally, see
 * TROUBLESHOOTING_YT_STREAMING.md). Previously, fixing this required shipping a new app
 * version. This object centralizes the client selection/ordering so it can be hotfixed by:
 *   1. Editing the defaults below and shipping a build (today), or
 *   2. Wiring `applyRemoteOverrides()` to a remote JSON config (Firebase Remote Config /
 *      a small static JSON on any CDN) so the ordering can change with NO app release.
 *      This is intentionally left as a documented extension point rather than wired to a
 *      live service in this change, to keep this fix isolated to the token/client layer.
 *
 * All lookups are by clientName + clientVersion string so overrides stay simple (a name
 * lookup against the same fixed catalog in YouTubeClient.kt) rather than requiring the
 * remote config to describe entire client objects (user agents, ids, etc).
 */
object RemoteStreamingConfig {
    private const val TAG = "RemoteStreamingConfig"

    private val catalog: Map<String, YouTubeClient> = listOf(
        WEB_REMIX, WEB, WEB_CREATOR, TVHTML5, TVHTML5_SIMPLY_EMBEDDED_PLAYER, MWEB,
        ANDROID_CREATOR, IPADOS, MOBILE, IOS,
        ANDROID_VR_1_43_32, ANDROID_VR_1_61_48, ANDROID_VR_NO_AUTH,
    ).associateBy { it.clientName + "_" + it.clientVersion }

    // ---- Defaults (2026-08 hotfix v2, based on real device logcat evidence) ----
    // NOTE ON THIS CHANGE: after the LOGIN_REQUIRED fix, live logcat testing showed
    // TVHTML5/MWEB/IOS now return UNPLAYABLE for nearly every video - only WEB_CREATOR
    // resolves successfully (see TROUBLESHOOTING_YT_STREAMING.md). Previously WEB_CREATOR
    // was placed 4th in the fallback chain, so every track spent 20-40s failing through
    // 3 dead clients before reaching the one that works - and some requests gave up /
    // looped before ever reaching it. WEB_CREATOR is now MAIN_CLIENT so it's tried first.
    //
    // Logged-in users: MAIN_CLIENT must support login (loginSupported = true) or the
    // user's cookie/dataSyncId is silently dropped and every request is anonymous.
    // WEB_CREATOR has loginSupported = true, so this preserves that invariant too.
    @Volatile var mainClientLoggedIn: YouTubeClient = WEB_CREATOR
        private set

    // Guests: the VR trick client is still fine to try first for guests since it was
    // always guest-only by design; only logged-in behavior was broken.
    @Volatile var mainClientGuest: YouTubeClient = ANDROID_VR_1_43_32
        private set

    // WEB_CREATOR is already tried as MAIN_CLIENT above, so the fallback chain starts
    // from the next-most-likely-to-work client rather than duplicating that attempt.
    //
    // 2026-08 update: live logcat evidence (device reports) shows TVHTML5/MWEB now fail
    // with UNPLAYABLE and IOS fails with HTTP 403 for nearly every video - consistent with
    // this file's own "hotfix v2" note above - yet they were still ordered *before*
    // WEB_REMIX, which the same evidence shows resolves successfully. That meant every
    // WEB_CREATOR miss burned 3 guaranteed-dead round trips (each potentially including a
    // PoToken WebView spin-up) before ever reaching a client that works, which is the
    // multi-second-to-a-minute stall users were seeing. WEB_REMIX now goes first.
    @Volatile private var fallbackLoggedIn: Array<YouTubeClient> = arrayOf(
        WEB_REMIX, TVHTML5, MWEB, IOS, TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        ANDROID_CREATOR, IPADOS, MOBILE, WEB, ANDROID_VR_1_61_48, ANDROID_VR_NO_AUTH,
    )

    @Volatile private var fallbackGuest: Array<YouTubeClient> = arrayOf(
        ANDROID_VR_1_61_48, WEB_CREATOR, WEB_REMIX, TVHTML5, MWEB, IOS,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER, ANDROID_CREATOR, IPADOS, ANDROID_VR_NO_AUTH, MOBILE, WEB,
    )

    fun fallbackClientsFor(isLoggedIn: Boolean): Array<YouTubeClient> =
        if (isLoggedIn) fallbackLoggedIn else fallbackGuest

    /**
     * Apply a hotfix config without an app release. Expected JSON shape:
     * {
     *   "mainClientLoggedIn": "WEB_REMIX_1.20260213.01.00",
     *   "mainClientGuest": "ANDROID_VR_1.43.32",
     *   "fallbackLoggedIn": ["WEB_REMIX_...", "TVHTML5_...", ...],
     *   "fallbackGuest": ["ANDROID_VR_...", ...]
     * }
     * Keys are "<clientName>_<clientVersion>" matching the `catalog` map above. Unknown
     * keys are ignored; malformed/empty config leaves current values untouched (fails safe).
     * NOT wired to a network fetch yet - call this from wherever the app's existing
     * remote-config/feature-flag fetch (if any) lands, or add a periodic fetch in
     * Application.onCreate(). See TROUBLESHOOTING_YT_STREAMING.md.
     */
    fun applyRemoteOverrides(json: org.json.JSONObject) {
        runCatching {
            json.optString("mainClientLoggedIn").takeIf { it.isNotBlank() }
                ?.let { catalog[it] }?.let { mainClientLoggedIn = it }
            json.optString("mainClientGuest").takeIf { it.isNotBlank() }
                ?.let { catalog[it] }?.let { mainClientGuest = it }
            json.optJSONArray("fallbackLoggedIn")?.let { arr ->
                val resolved = (0 until arr.length()).mapNotNull { catalog[arr.getString(it)] }
                if (resolved.isNotEmpty()) fallbackLoggedIn = resolved.toTypedArray()
            }
            json.optJSONArray("fallbackGuest")?.let { arr ->
                val resolved = (0 until arr.length()).mapNotNull { catalog[arr.getString(it)] }
                if (resolved.isNotEmpty()) fallbackGuest = resolved.toTypedArray()
            }
            Timber.tag(TAG).i("Applied remote streaming config override")
        }.onFailure { Timber.tag(TAG).e(it, "Failed to apply remote streaming config, keeping current values") }
    }
}
