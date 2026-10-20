package com.nexapp.nexmusic.core.repository

import com.nexapp.nexmusic.core.mapping.toDomain
import com.nexapp.nexmusic.domain.models.Playlist as DomainPlaylist
import com.nexapp.nexmusic.domain.models.Song as DomainSong
import com.nexapp.nexmusic.domain.repositories.PlaylistRepository
import com.nexapp.nexmusic.db.MusicDatabase
import com.nexapp.nexmusic.db.entities.PlaylistSongMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
  private val database: MusicDatabase
) : PlaylistRepository {

  override fun getAllPlaylists(): Flow<List<DomainPlaylist>> {
    return database.playlistsByNameAsc().map { list ->
      list.map { it.toDomain() }
    }
  }

  override fun getPlaylistSongs(playlistId: String): Flow<List<DomainSong>> {
    return database.playlistSongs(playlistId).map { list ->
      list.map { it.song.toDomain() }
    }
  }

  override suspend fun addSongToPlaylist(playlistId: String, songId: String) {
    database.query {
      val existingMaps = playlistSongMaps(playlistId, 0)
      val nextPos = (existingMaps.maxOfOrNull { it.position } ?: -1) + 1
      insert(PlaylistSongMap(songId = songId, playlistId = playlistId, position = nextPos))
    }
  }

  override suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
    database.query {
      val maps = playlistSongMaps(songId).filter { it.playlistId == playlistId }
      maps.forEach { delete(it) }
    }
  }
}
