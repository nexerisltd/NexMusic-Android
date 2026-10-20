package com.nexapp.nexmusic.core.mapping

import com.nexapp.nexmusic.domain.models.AlbumRef
import com.nexapp.nexmusic.domain.models.ArtistRef
import com.nexapp.nexmusic.domain.models.Playlist as DomainPlaylist
import com.nexapp.nexmusic.domain.models.Song as DomainSong
import com.nexapp.nexmusic.db.entities.Playlist as DbPlaylist
import com.nexapp.nexmusic.db.entities.PlaylistEntity
import com.nexapp.nexmusic.db.entities.Song as DbSong
import com.nexapp.nexmusic.db.entities.SongEntity

fun DbSong.toDomain(): DomainSong {
  return DomainSong(
    id = song.id,
    title = song.title,
    artists = artists.map { ArtistRef(id = it.id, name = it.name) },
    album = album?.let { AlbumRef(id = it.id, title = it.title) }
      ?: song.albumId?.let { AlbumRef(id = it, title = song.albumName.orEmpty()) },
    durationSeconds = song.duration,
    thumbnailUrl = thumbnailUrl,
    isExplicit = song.explicit,
    isLocal = song.isLocal,
    liked = song.liked,
    totalPlayTimeMs = song.totalPlayTime
  )
}

fun SongEntity.toDomain(): DomainSong {
  val resolvedThumbnailUrl = if (thumbnailUrl != null) {
    thumbnailUrl
  } else if (isLocal) {
    val mediaStoreAlbumId = albumId?.removePrefix("LOCAL_ALBUM_")?.toLongOrNull()
    if (mediaStoreAlbumId != null && mediaStoreAlbumId > 0) {
      "content://media/external/audio/albumart/$mediaStoreAlbumId"
    } else {
      id
    }
  } else {
    null
  }

  return DomainSong(
    id = id,
    title = title,
    artists = emptyList(),
    album = albumId?.let { AlbumRef(id = it, title = albumName.orEmpty()) },
    durationSeconds = duration,
    thumbnailUrl = resolvedThumbnailUrl,
    isExplicit = explicit,
    isLocal = isLocal,
    liked = liked,
    totalPlayTimeMs = totalPlayTime
  )
}

fun DbPlaylist.toDomain(): DomainPlaylist {
  return DomainPlaylist(
    id = playlist.id,
    name = playlist.name,
    songCount = songCount,
    thumbnailUrl = thumbnails.firstOrNull() ?: playlist.thumbnailUrl
  )
}

fun PlaylistEntity.toDomain(songCount: Int = 0): DomainPlaylist {
  return DomainPlaylist(
    id = id,
    name = name,
    songCount = songCount,
    thumbnailUrl = thumbnailUrl
  )
}
