package com.nexapp.nexmusic.musicflow

import androidx.datastore.preferences.core.Preferences
import com.nexapp.nexmusic.constants.CanvasThumbnailAnimationKey
import com.nexapp.nexmusic.constants.DataSaverEnabledKey
import com.nexapp.nexmusic.constants.MusicFlowAnimatedBackgroundKey
import com.nexapp.nexmusic.constants.MusicFlowAutoNextKey
import com.nexapp.nexmusic.constants.MusicFlowAutoplayKey
import com.nexapp.nexmusic.constants.MusicFlowCellularPrefetchKey
import com.nexapp.nexmusic.constants.MusicFlowEnabledKey
import com.nexapp.nexmusic.constants.MusicFlowHideRecentKey
import com.nexapp.nexmusic.constants.MusicFlowLyricsKey

data class MusicFlowPreferences(
  val enabled: Boolean = true,
  val autoplay: Boolean = true,
  val autoNext: Boolean = true,
  val animatedBackground: Boolean = false,
  val lyrics: Boolean = true,
  val cellularPrefetch: Boolean = true,
  val hideRecent: Boolean = true,
  val dataSaver: Boolean = false,
) {
  companion object {
    fun from(preferences: Preferences) =
      MusicFlowPreferences(
        enabled = preferences[MusicFlowEnabledKey] ?: true,
        autoplay = preferences[MusicFlowAutoplayKey] ?: true,
        autoNext = preferences[MusicFlowAutoNextKey] ?: true,
        animatedBackground =
          preferences[MusicFlowAnimatedBackgroundKey]
            ?: preferences[CanvasThumbnailAnimationKey]
            ?: false,
        lyrics = preferences[MusicFlowLyricsKey] ?: true,
        cellularPrefetch = preferences[MusicFlowCellularPrefetchKey] ?: true,
        hideRecent = preferences[MusicFlowHideRecentKey] ?: true,
        dataSaver = preferences[DataSaverEnabledKey] ?: false,
      )
  }
}
