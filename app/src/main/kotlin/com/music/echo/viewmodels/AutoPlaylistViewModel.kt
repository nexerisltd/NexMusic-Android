

package com.nexapp.nexpass.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexapp.nexpass.constants.ExportedSongIdsKey
import com.nexapp.nexpass.constants.HideExplicitKey
import com.nexapp.nexpass.constants.HideVideoSongsKey
import com.nexapp.nexpass.constants.SongSortDescendingKey
import com.nexapp.nexpass.constants.SongSortType
import com.nexapp.nexpass.constants.SongSortTypeKey
import com.nexapp.nexpass.db.MusicDatabase
import com.nexapp.nexpass.extensions.filterExplicit
import com.nexapp.nexpass.extensions.filterVideoSongs
import com.nexapp.nexpass.extensions.toEnum
import com.nexapp.nexpass.utils.SyncUtils
import com.nexapp.nexpass.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoPlaylistViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val playlist = savedStateHandle.get<String>("playlist")!!

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val likedSongs =
        context.dataStore.data
            .map {
                Triple(
                    Triple(
                        it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE) to (it[SongSortDescendingKey] ?: true),
                        it[HideExplicitKey] ?: false,
                        it[HideVideoSongsKey] ?: false
                    ),
                    it[ExportedSongIdsKey] ?: "",
                    Unit
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { (triple, exportedSongIds, _) ->
                val (sortDesc, hideExplicit, hideVideoSongs) = triple
                val (sortType, descending) = sortDesc
                when (playlist) {
                    "liked" -> database.likedSongs(sortType, descending)
                        .map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }

                    "downloaded" -> database.downloadedSongs(sortType, descending)
                        .map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }

                    "uploaded" -> database.uploadedSongs(sortType, descending)
                        .map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }

                    "exported" -> {
                        val ids = exportedSongIds.split(",").filter { it.isNotBlank() }
                        database.getSongsByIdsFlow(ids)
                            .map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    }

                    else -> kotlinx.coroutines.flow.flowOf(emptyList())
                }
            }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }

    fun syncUploadedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncUploadedSongs() }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            when (playlist) {
                "liked" -> syncUtils.syncLikedSongsSuspend()
                "uploaded" -> syncUtils.syncUploadedSongsSuspend()
            }
            _isRefreshing.value = false
        }
    }
}
