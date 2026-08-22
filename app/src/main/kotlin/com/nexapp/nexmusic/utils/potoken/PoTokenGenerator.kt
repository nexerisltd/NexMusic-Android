package com.nexapp.nexmusic.utils.potoken

import android.webkit.CookieManager
import com.nexapp.nexmusic.utils.cipher.CipherDeobfuscator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

class PoTokenGenerator {
    private val TAG = "PoTokenGenerator"

    private val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    private var webViewBadImpl = false 

    private val webPoTokenGenLock = Mutex()
    private var webPoTokenSessionId: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null

    // Optional fallback: set by callers that have an android.content.Context (e.g.
    // YTPlayerUtils) so we can consult RemotePoTokenProvider when the on-device WebView path
    // is unavailable/broken. Left null means "remote fallback disabled" - safe default.
    var remoteFallbackContext: android.content.Context? = null

    fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        Timber.tag(TAG).d("getWebClientPoToken called: videoId=$videoId, sessionId=$sessionId")
        Timber.tag(TAG).d("WebView state: supported=$webViewSupported, badImpl=$webViewBadImpl")
        if (!webViewSupported || webViewBadImpl) {
            Timber.tag(TAG).d("WebView not available: supported=$webViewSupported, badImpl=$webViewBadImpl")
            return tryRemoteFallback(videoId, sessionId)
        }

        return try {
            Timber.tag(TAG).d("Calling runBlocking to generate poToken...")
            runBlocking { getWebClientPoToken(videoId, sessionId, forceRecreate = false) }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "poToken generation exception: ${e.javaClass.simpleName}: ${e.message}")
            when (e) {
                is BadWebViewException -> {
                    Timber.tag(TAG).e(e, "Could not obtain poToken because WebView is broken")
                    webViewBadImpl = true
                    tryRemoteFallback(videoId, sessionId)
                }
                else -> throw e 
            }
        }
    }

    private fun tryRemoteFallback(videoId: String, sessionId: String): PoTokenResult? {
        val context = remoteFallbackContext ?: return null
        if (!com.nexapp.nexmusic.utils.potoken.RemotePoTokenProvider.isConfigured(context)) return null
        Timber.tag(TAG).d("On-device PoToken unavailable, trying configured remote PoToken server")
        return runCatching {
            runBlocking {
                com.nexapp.nexmusic.utils.potoken.RemotePoTokenProvider.fetchPoToken(context, videoId, sessionId)
            }
        }.onFailure { Timber.tag(TAG).w(it, "Remote PoToken fallback also failed") }.getOrNull()
    }


    
    private suspend fun getWebClientPoToken(videoId: String, sessionId: String, forceRecreate: Boolean): PoTokenResult {
        Timber.tag(TAG).d("Web poToken requested: videoId=$videoId, sessionId=$sessionId")

        val (poTokenGenerator, streamingPot, hasBeenRecreated) =
            webPoTokenGenLock.withLock {
                val shouldRecreate =
                    forceRecreate || webPoTokenGenerator == null || webPoTokenGenerator!!.isExpired ||
                        webPoTokenGenerator!!.isDead ||
                        webPoTokenSessionId != sessionId

                if (shouldRecreate) {
                    Timber.tag(TAG).d("Creating new PoTokenWebView (forceRecreate=$forceRecreate)")

                    withContext(Dispatchers.Main) {
                        webPoTokenGenerator?.close()
                    }

                    
                    val newGenerator = PoTokenWebView.getNewPoTokenGenerator(CipherDeobfuscator.appContext)

                    
                    val newStreamingPot = try {
                        newGenerator.generatePoToken(sessionId)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { newGenerator.close() }
                        throw e
                    }
                    
                    webPoTokenSessionId = sessionId
                    webPoTokenGenerator = newGenerator
                    webPoTokenStreamingPot = newStreamingPot
                    Timber.tag(TAG).d("Streaming poToken generated for sessionId=${webPoTokenSessionId?.take(20)}...")
                }

                Triple(webPoTokenGenerator!!, webPoTokenStreamingPot!!, shouldRecreate)
            }

        val playerPot = try {
            poTokenGenerator.generatePoToken(videoId)
        } catch (throwable: Throwable) {
            if (hasBeenRecreated) {
                
                
                throw throwable
            } else {
                
                
                
                Timber.tag(TAG).e(throwable, "Failed to obtain poToken, retrying")
                return getWebClientPoToken(videoId = videoId, sessionId = sessionId, forceRecreate = true)
            }
        }

        Timber.tag(TAG).d("poToken generated successfully: player=${playerPot.take(20)}..., streaming=${streamingPot.take(20)}...")

        return PoTokenResult(playerPot, streamingPot)
    }
}
