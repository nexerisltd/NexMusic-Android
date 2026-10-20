package com.nexapp.nexmusic.musicflow

import androidx.media3.common.Player
import com.nexapp.nexmusic.extensions.toMediaItem
import com.nexapp.nexmusic.playback.MusicService
import com.nexapp.nexmusic.playback.PlayerConnection
import com.nexapp.nexmusic.playback.queues.Queue

class PlayerConnectionMusicFlowPlayer(
  private val connection: PlayerConnection,
  private val session: MusicFlowSession,
  private val queueTitle: String,
) : MusicFlowPlayer {
  private val service: MusicService
    get() = connection.service

  override fun currentMediaId(): String? = connection.player.currentMediaItem?.mediaId

  override fun queueMediaIds(): List<String> {
    val player = connection.player
    return List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId }
  }

  override fun ownsQueue(handle: Any): Boolean = service.activeQueue.value === handle

  override fun isAnyFlowQueueActive(): Boolean = service.activeQueue.value is MusicFlowQueue

  override fun takeoverBlock(): TakeoverBlock? =
    when {
      connection.shouldBlockPlaybackChanges?.invoke() == true -> TakeoverBlock.LISTEN_TOGETHER_GUEST
      service.castConnectionHandler?.isCasting?.value == true -> TakeoverBlock.CASTING
      else -> null
    }

  override fun seekTo(queueIndex: Int, playWhenReady: Boolean) {
    val player = connection.player
    player.seekTo(queueIndex, 0L)
    if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
      player.prepare()
    }
    player.playWhenReady = playWhenReady
  }

  override fun startQueue(items: List<MusicFlowItem>, startIndex: Int, playWhenReady: Boolean): Any {
    val queue = MusicFlowQueue(queueTitle, items, startIndex, session)
    service.playQueue(queue, playWhenReady)
    return queue
  }

  override fun append(handle: Any, items: List<MusicFlowItem>) {
    if (!ownsQueue(handle)) return
    service.addToQueue(items.map { it.song.toMediaItem() })
  }

  override fun setPauseAtEnd(handle: Any, pause: Boolean) {
    if (handle is Queue) service.setPauseAtEndOfItems(handle, pause)
  }

  override fun snapshot(): Any? = service.snapshotQueue()

  override fun restore(snapshot: Any, playWhenReady: Boolean) {
    if (snapshot is MusicService.QueueSnapshot) service.restoreQueueSnapshot(snapshot, playWhenReady)
  }

  override fun isPlaying(): Boolean = connection.isPlaying.value
}
