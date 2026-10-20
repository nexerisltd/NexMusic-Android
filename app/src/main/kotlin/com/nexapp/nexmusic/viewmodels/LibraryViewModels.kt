@file:OptIn(ExperimentalCoroutinesApi::class)

package com.nexapp.nexmusic.viewmodels

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.nexapp.nexmusic.constants.AlbumFilter
import com.nexapp.nexmusic.constants.AlbumFilterKey
import com.nexapp.nexmusic.constants.AlbumSortDescendingKey
import com.nexapp.nexmusic.constants.AlbumSortType
import com.nexapp.nexmusic.constants.AlbumSortTypeKey
import com.nexapp.nexmusic.constants.ArtistFilter
import com.nexapp.nexmusic.constants.ArtistFilterKey
import com.nexapp.nexmusic.constants.ArtistSongSortDescendingKey
import com.nexapp.nexmusic.constants.ArtistSongSortType
import com.nexapp.nexmusic.constants.ArtistSongSortTypeKey
import com.nexapp.nexmusic.constants.ArtistSortDescendingKey
import com.nexapp.nexmusic.constants.ArtistSortType
import com.nexapp.nexmusic.constants.ArtistSortTypeKey
import com.nexapp.nexmusic.constants.ExportedSongIdsKey
import com.nexapp.nexmusic.constants.HideExplicitKey
import com.nexapp.nexmusic.constants.HideVideoSongsKey
import com.nexapp.nexmusic.constants.HideYoutubeShortsKey
import com.nexapp.nexmusic.constants.LibraryFilter
import com.nexapp.nexmusic.constants.PlaylistSortDescendingKey
import com.nexapp.nexmusic.constants.PlaylistSortType
import com.nexapp.nexmusic.constants.PlaylistSortTypeKey
import com.nexapp.nexmusic.constants.SongFilter
import com.nexapp.nexmusic.constants.SongFilterKey
import com.nexapp.nexmusic.constants.SongSortDescendingKey
import com.nexapp.nexmusic.constants.SongSortType
import com.nexapp.nexmusic.constants.SongSortTypeKey
import com.nexapp.nexmusic.constants.TopSize
import com.nexapp.nexmusic.db.MusicDatabase
import com.nexapp.nexmusic.extensions.filterExplicit
import com.nexapp.nexmusic.extensions.filterExplicitAlbums
import com.nexapp.nexmusic.extensions.filterVideoSongs
import com.nexapp.nexmusic.extensions.filterYoutubeShorts
import com.nexapp.nexmusic.extensions.toEnum
import com.nexapp.nexmusic.playback.DownloadUtil
import com.nexapp.nexmusic.utils.SyncUtils
import com.nexapp.nexmusic.utils.dataStore
import com.nexapp.nexmusic.utils.reportException
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibrarySongsViewModel
@Inject
constructor(
  @ApplicationContext context: Context,
  database: MusicDatabase,
  downloadUtil: DownloadUtil,
  private val syncUtils: SyncUtils,
) : ViewModel() {
  val allSongs =
    context.dataStore.data
      .map {
        Triple(
          Triple(
            (try {
                it[SongFilterKey]
              } catch (e: Exception) {
                null
              })
              .toEnum(SongFilter.LIKED),
            (try {
                it[SongSortTypeKey]
              } catch (e: Exception) {
                null
              })
              .toEnum(SongSortType.CREATE_DATE),
            ((try {
              it[SongSortDescendingKey]
            } catch (e: Exception) {
              null
            }) ?: true),
          ),
          (try {
            it[ExportedSongIdsKey]
          } catch (e: Exception) {
            null
          }) ?: "",
          Pair(
            (try {
              it[HideExplicitKey]
            } catch (e: Exception) {
              null
            }) ?: false,
            (try {
              it[HideVideoSongsKey]
            } catch (e: Exception) {
              null
            }) ?: false
          )
        )
      }
      .distinctUntilChanged()
      .flatMapLatest { (filterSort, exportedSongIds, hideConfig) ->
        val (filter, sortType, descending) = filterSort
        val (hideExplicit, hideVideoSongs) = hideConfig
        when (filter) {
          SongFilter.LIBRARY ->
            database.songs(sortType, descending).map {
              it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs)
            }
          SongFilter.LIKED ->
            database.likedSongs(sortType, descending).map {
              it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs)
            }
          SongFilter.DOWNLOADED ->
            database.downloadedSongs(sortType, descending).map {
              it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs)
            }
          SongFilter.UPLOADED ->
            database.uploadedSongs(sortType, descending).map {
              it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs)
            }
          SongFilter.EXPORTED -> {
            val ids = exportedSongIds.split(",").filter { it.isNotBlank() }
            database.getSongsByIdsFlow(ids).map {
              it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs)
            }
          }
        }
      }
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  fun syncLikedSongs() {
    viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
  }

  fun syncLibrarySongs() {
    viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLibrarySongs() }
  }

  fun syncUploadedSongs() {
    viewModelScope.launch(Dispatchers.IO) { syncUtils.syncUploadedSongs() }
  }
}

@HiltViewModel
class LibraryArtistsViewModel
@Inject
constructor(
  @ApplicationContext context: Context,
  database: MusicDatabase,
  private val syncUtils: SyncUtils,
) : ViewModel() {
  val allArtists =
    context.dataStore.data
      .map {
        Triple(
          (try {
              it[ArtistFilterKey]
            } catch (e: Exception) {
              null
            })
            .toEnum(ArtistFilter.LIKED),
          (try {
              it[ArtistSortTypeKey]
            } catch (e: Exception) {
              null
            })
            .toEnum(ArtistSortType.CREATE_DATE),
          (try {
            it[ArtistSortDescendingKey]
          } catch (e: Exception) {
            null
          }) ?: true,
        )
      }
      .distinctUntilChanged()
      .flatMapLatest { (filter, sortType, descending) ->
        when (filter) {
          ArtistFilter.LIKED -> database.artistsBookmarked(sortType, descending)
          ArtistFilter.LIBRARY -> database.artists(sortType, descending)
        }
      }
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  fun sync() {
    viewModelScope.launch(Dispatchers.IO) { syncUtils.syncArtistsSubscriptions() }
  }

  init {
    viewModelScope.launch(Dispatchers.IO) {
      allArtists.collect { artists ->
        artists
          .map { it.artist }
          .filter {
            !it.isLocal &&
              !it.id.startsWith("LOCAL_ARTIST_") &&
              (it.thumbnailUrl == null ||
                Duration.between(it.lastUpdateTime, LocalDateTime.now()) > Duration.ofDays(10))
          }
          .forEach { artist ->
            YouTube.artist(artist.id).onSuccess { artistPage ->
              database.query { update(artist, artistPage) }
            }
          }
      }
    }
  }
}

@HiltViewModel
class LibraryAlbumsViewModel
@Inject
constructor(
  @ApplicationContext context: Context,
  database: MusicDatabase,
  private val syncUtils: SyncUtils,
) : ViewModel() {
  val allAlbums =
    context.dataStore.data
      .map {
        Pair(
          Triple(
            (try {
                it[AlbumFilterKey]
              } catch (e: Exception) {
                null
              })
              .toEnum(AlbumFilter.LIKED),
            (try {
                it[AlbumSortTypeKey]
              } catch (e: Exception) {
                null
              })
              .toEnum(AlbumSortType.CREATE_DATE),
            (try {
              it[AlbumSortDescendingKey]
            } catch (e: Exception) {
              null
            }) ?: true,
          ),
          (try {
            it[HideExplicitKey]
          } catch (e: Exception) {
            null
          }) ?: false
        )
      }
      .distinctUntilChanged()
      .flatMapLatest { (filterSort, hideExplicit) ->
        val (filter, sortType, descending) = filterSort
        when (filter) {
          AlbumFilter.LIKED ->
            database.albumsLiked(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
          AlbumFilter.LIBRARY ->
            database.albums(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
          AlbumFilter.UPLOADED ->
            database.albumsUploaded(sortType, descending).map {
              it.filterExplicitAlbums(hideExplicit)
            }
        }
      }
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  fun sync() {
    viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedAlbums() }
  }

  init {
    viewModelScope.launch(Dispatchers.IO) {
      allAlbums.collect { albums ->
        albums
          .filter {
            !it.album.isLocal && !it.id.startsWith("LOCAL_ALBUM_") && it.album.songCount == 0
          }
          .forEach { album ->
            YouTube.album(album.id)
              .onSuccess { albumPage ->
                database.query { update(album.album, albumPage, album.artists) }
              }
              .onFailure {
                reportException(it)
                if (it.message?.contains("NOT_FOUND") == true) {
                  database.query { delete(album.album) }
                }
              }
          }
      }
    }
  }
}

@HiltViewModel
class LibraryPlaylistsViewModel
@Inject
constructor(
  @ApplicationContext context: Context,
  database: MusicDatabase,
  private val syncUtils: SyncUtils,
) : ViewModel() {
  val allPlaylists =
    context.dataStore.data
      .map {
        Triple(
          (try {
              it[PlaylistSortTypeKey]
            } catch (e: Exception) {
              null
            })
            .toEnum(PlaylistSortType.CREATE_DATE),
          (try {
            it[PlaylistSortDescendingKey]
          } catch (e: Exception) {
            null
          }) ?: true,
          (try {
            it[HideYoutubeShortsKey]
          } catch (e: Exception) {
            null
          }) ?: false
        )
      }
      .distinctUntilChanged()
      .flatMapLatest { (sortType, descending, hideYoutubeShorts) ->
        database.playlists(sortType, descending).map { it.filterYoutubeShorts(hideYoutubeShorts) }
      }
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  fun sync() {
    viewModelScope.launch(Dispatchers.IO) { syncUtils.syncSavedPlaylists() }
  }

  val topValue =
    context.dataStore.data
      .map {
        (try {
          it[TopSize]
        } catch (e: Exception) {
          null
        }) ?: "50"
      }
      .distinctUntilChanged()
}

@HiltViewModel
class ArtistSongsViewModel
@Inject
constructor(
  @ApplicationContext context: Context,
  database: MusicDatabase,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {
  private val artistId = savedStateHandle.get<String>("artistId")!!
  val artist = database.artist(artistId).stateIn(viewModelScope, SharingStarted.Lazily, null)

  val songs =
    context.dataStore.data
      .map {
        Triple(
          (try {
              it[ArtistSongSortTypeKey]
            } catch (e: Exception) {
              null
            })
            .toEnum(ArtistSongSortType.CREATE_DATE) to
            ((try {
              it[ArtistSongSortDescendingKey]
            } catch (e: Exception) {
              null
            }) ?: true),
          (try {
            it[HideExplicitKey]
          } catch (e: Exception) {
            null
          }) ?: false,
          (try {
            it[HideVideoSongsKey]
          } catch (e: Exception) {
            null
          }) ?: false
        )
      }
      .distinctUntilChanged()
      .flatMapLatest { (sortDesc, hideExplicit, hideVideoSongs) ->
        val (sortType, descending) = sortDesc
        database.artistSongs(artistId, sortType, descending).map {
          it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs)
        }
      }
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LibraryMixViewModel
@Inject
constructor(
  @ApplicationContext context: Context,
  database: MusicDatabase,
  private val syncUtils: SyncUtils,
) : ViewModel() {
  private val _isRefreshing = MutableStateFlow(false)
  val isRefreshing = _isRefreshing.asStateFlow()

  val syncAllLibrary = { viewModelScope.launch(Dispatchers.IO) { syncUtils.tryAutoSync() } }

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) {
      _isRefreshing.value = true
      syncUtils.performFullSyncSuspend()
      _isRefreshing.value = false
    }
  }

  val topValue =
    context.dataStore.data
      .map {
        (try {
          it[TopSize]
        } catch (e: Exception) {
          null
        }) ?: "50"
      }
      .distinctUntilChanged()
  var artists =
    database
      .artistsBookmarked(
        ArtistSortType.CREATE_DATE,
        true,
      )
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
  var albums =
    context.dataStore.data
      .map {
        (try {
          it[HideExplicitKey]
        } catch (e: Exception) {
          null
        }) ?: false
      }
      .distinctUntilChanged()
      .flatMapLatest { hideExplicit ->
        database.albumsLiked(AlbumSortType.CREATE_DATE, true).map {
          it.filterExplicitAlbums(hideExplicit)
        }
      }
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
  var playlists =
    context.dataStore.data
      .map {
        (try {
          it[HideYoutubeShortsKey]
        } catch (e: Exception) {
          null
        }) ?: false
      }
      .distinctUntilChanged()
      .flatMapLatest { hideYoutubeShorts ->
        database.playlists(PlaylistSortType.CREATE_DATE, true).map {
          it.filterYoutubeShorts(hideYoutubeShorts)
        }
      }
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  init {
    viewModelScope.launch(Dispatchers.IO) {
      albums.collect { albums ->
        albums
          .filter {
            !it.album.isLocal && !it.id.startsWith("LOCAL_ALBUM_") && it.album.songCount == 0
          }
          .forEach { album ->
            YouTube.album(album.id)
              .onSuccess { albumPage ->
                database.query { update(album.album, albumPage, album.artists) }
              }
              .onFailure {
                reportException(it)
                if (it.message?.contains("NOT_FOUND") == true) {
                  database.query { delete(album.album) }
                }
              }
          }
      }
    }
    viewModelScope.launch(Dispatchers.IO) {
      artists.collect { artists ->
        artists
          .map { it.artist }
          .filter {
            !it.isLocal &&
              !it.id.startsWith("LOCAL_ARTIST_") &&
              (it.thumbnailUrl == null ||
                Duration.between(
                  it.lastUpdateTime,
                  LocalDateTime.now(),
                ) > Duration.ofDays(10))
          }
          .forEach { artist ->
            YouTube.artist(artist.id).onSuccess { artistPage ->
              database.query { update(artist, artistPage) }
            }
          }
      }
    }
  }
}

@HiltViewModel
class LibraryViewModel @Inject constructor() : ViewModel() {
  private val curScreen = mutableStateOf(LibraryFilter.LIBRARY)
  val filter: MutableState<LibraryFilter> = curScreen
}
