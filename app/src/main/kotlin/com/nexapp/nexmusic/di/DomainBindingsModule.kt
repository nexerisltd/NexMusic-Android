package com.nexapp.nexmusic.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.nexapp.nexmusic.core.repository.PlaylistRepositoryImpl
import com.nexapp.nexmusic.core.repository.SongRepositoryImpl
import com.nexapp.nexmusic.domain.repositories.PlaylistRepository
import com.nexapp.nexmusic.domain.repositories.SongRepository
import com.nexapp.nexmusic.musicflow.MusicFlowFeedSource
import com.nexapp.nexmusic.musicflow.MusicFlowRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainBindingsModule {

  @Binds
  @Singleton
  abstract fun bindSongRepository(
    impl: SongRepositoryImpl
  ): SongRepository

  @Binds
  @Singleton
  abstract fun bindPlaylistRepository(
    impl: PlaylistRepositoryImpl
  ): PlaylistRepository

  @Binds
  @Singleton
  abstract fun bindMusicFlowFeedSource(
    impl: MusicFlowRepository
  ): MusicFlowFeedSource
}
