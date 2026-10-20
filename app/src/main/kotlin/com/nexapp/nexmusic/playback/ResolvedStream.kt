package com.nexapp.nexmusic.playback

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import com.nexapp.nexmusic.utils.InnerTubeXResolver
import com.nexapp.nexmusic.utils.PlayerClient
import com.nexapp.nexmusic.utils.YTPlayerUtils.PlaybackData

internal data class ResolvedStream(
  val url: String,
  val expiresAtMs: Long,
  val headers: Map<String, String>,
) {
  fun resolve(dataSpec: DataSpec): DataSpec =
    dataSpec.buildUpon()
      .setUri(url.toUri())
      .setHttpRequestHeaders(dataSpec.httpRequestHeaders + headers)
      .build()

  companion object {
    fun from(playbackData: PlaybackData, nowMs: Long = System.currentTimeMillis()): ResolvedStream =
      ResolvedStream(
        url = playbackData.streamUrl,
        expiresAtMs = nowMs + playbackData.streamExpiresInSeconds * 1000L,
        headers = playbackData.headers
          ?: InnerTubeXResolver.headersFor(playbackData.streamUrl)
          ?: PlayerClient.forStreamUrl(playbackData.streamUrl).mediaHeaders(),
      )
  }
}
