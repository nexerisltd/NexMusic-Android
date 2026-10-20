package com.nexapp.nexmusic.playback

import com.nexapp.nexmusic.db.entities.SongEntity

interface ISyncUtils {
  fun likeSong(song: SongEntity)
}
