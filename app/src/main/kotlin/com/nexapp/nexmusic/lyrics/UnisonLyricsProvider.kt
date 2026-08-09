package com.nexapp.nexmusic.lyrics

import android.content.Context
import com.music.echo.unison.Unison
import com.nexapp.nexmusic.constants.UnisonLyricsEnabledKey
import com.nexapp.nexmusic.utils.dataStore
import com.nexapp.nexmusic.utils.get

object UnisonLyricsProvider : LyricsProvider {
    override val name: String = "Unison"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[UnisonLyricsEnabledKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Unison.getLyrics(
        videoId = id,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = duration
    ).map { convertIfTTML(it) }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        Unison.getAllLyrics(
            videoId = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
            callback = { callback(convertIfTTML(it)) }
        )
    }

    private fun convertIfTTML(content: String): String {
        return if (content.trimStart().startsWith("<tt", ignoreCase = true)) {
            val parsedLines = com.nexapp.nexmusic.betterlyrics.TTMLParser.parseTTML(content)
            com.nexapp.nexmusic.betterlyrics.TTMLParser.toLRC(parsedLines)
        } else {
            content
        }
    }
}
