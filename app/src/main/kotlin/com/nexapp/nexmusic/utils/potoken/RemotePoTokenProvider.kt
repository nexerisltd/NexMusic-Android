package com.nexapp.nexmusic.utils.potoken

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Optional fallback PoToken source: a self-hosted server (e.g. a deployment of
 * https://github.com/Brainicism/bgutil-ytdlp-pot-provider or any HTTP service exposing the same
 * request/response shape) that runs the real BotGuard/PoToken challenge in Node.js. This exists
 * because:
 *   - Node.js (and thus most existing PoToken solvers built for yt-dlp) can't run on-device.
 *   - The in-app WebView-based generator (`PoTokenGenerator`/`PoTokenWebView`) is the primary
 *     path and works standalone with no server required - this is a fallback ONLY, used when the
 *     WebView path is unavailable or fails, and only if the user has explicitly configured a
 *     server URL in Settings.
 *
 * SELF-HOSTING: run bgutil-ytdlp-pot-provider (or compatible) on your own VPS/desktop, e.g.:
 *   docker run -p 4416:4416 brainicism/bgutil-ytdlp-pot-provider
 * Then set the server URL in NexMusic Settings -> Playback -> "PoToken server URL" to
 * http://your-server:4416 (use https + a reverse proxy for anything reachable over the internet -
 * never expose this port directly, it has no built-in auth).
 *
 * This class deliberately has NO default/hardcoded server - a blank/unset config means "disabled"
 * and only the on-device WebView path is used.
 */
object RemotePoTokenProvider {
    private const val TAG = "RemotePoTokenProvider"
    private const val PREFS_NAME = "nexmusic_potoken_provider"
    private const val KEY_SERVER_URL = "remote_potoken_server_url"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getServerUrl(context: Context): String? =
        prefs(context).getString(KEY_SERVER_URL, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setServerUrl(context: Context, url: String?) {
        prefs(context).edit {
            if (url.isNullOrBlank()) remove(KEY_SERVER_URL) else putString(KEY_SERVER_URL, url.trim())
        }
    }

    fun isConfigured(context: Context): Boolean = getServerUrl(context) != null

    /**
     * Requests a PoToken pair from the configured remote server. Returns null (never throws) on
     * any failure - the caller (PoTokenGenerator) is expected to treat this exactly like "WebView
     * unavailable" and continue without a PoToken rather than fail playback outright.
     *
     * Expected server contract (bgutil-ytdlp-pot-provider compatible):
     *   POST {serverUrl}/get_pot
     *   body: {"content_binding": "<videoId or visitorData depending on context>"}
     *   response: {"po_token": "<player-request token>", "streaming_po_token": "<streaming token>"}
     * If your server only returns a single token field (some minimal setups do), the same value
     * is used for both - most SABR-enforcement checks only require the streaming token to be
     * present and valid, not necessarily distinct from the player-request one.
     */
    suspend fun fetchPoToken(context: Context, videoId: String, visitorData: String?): PoTokenResult? =
        withContext(Dispatchers.IO) {
            val serverUrl = getServerUrl(context) ?: return@withContext null
            runCatching {
                val body = JSONObject().apply {
                    put("content_binding", visitorData ?: videoId)
                    put("video_id", videoId)
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(serverUrl.trimEnd('/') + "/get_pot")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.tag(TAG).w("Remote PoToken server returned HTTP ${response.code}")
                        return@withContext null
                    }
                    val json = JSONObject(response.body?.string().orEmpty())
                    val playerToken = json.optString("po_token").takeIf { it.isNotBlank() }
                        ?: return@withContext null
                    val streamingToken = json.optString("streaming_po_token").takeIf { it.isNotBlank() }
                        ?: playerToken
                    Timber.tag(TAG).d("Remote PoToken fetched successfully for videoId=$videoId")
                    PoTokenResult(playerRequestPoToken = playerToken, streamingDataPoToken = streamingToken)
                }
            }.onFailure { e ->
                Timber.tag(TAG).w(e, "Remote PoToken fetch failed, falling back to no PoToken")
            }.getOrNull()
        }
}
