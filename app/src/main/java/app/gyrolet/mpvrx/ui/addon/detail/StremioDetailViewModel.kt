/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.addon.detail

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gyrolet.mpvrx.data.addon.AddonManager
import app.gyrolet.mpvrx.data.addon.StremioPlaybackTracker
import app.gyrolet.mpvrx.data.addon.TmdbSearchClient
import app.gyrolet.mpvrx.database.entities.StremioPlaybackHistoryEntity
import app.gyrolet.mpvrx.domain.addon.model.StremioMeta
import app.gyrolet.mpvrx.domain.addon.model.StremioStream
import app.gyrolet.mpvrx.domain.addon.model.StremioVideo
import app.gyrolet.mpvrx.domain.stream.StreamEngine
import app.gyrolet.mpvrx.ui.player.PlaybackItem
import app.gyrolet.mpvrx.ui.player.PlayerActivity
import app.gyrolet.mpvrx.ui.player.PreparedPlaybackLaunchStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StremioDetailViewModel(
  val metaId: String,
  val type: String,
  private val addonManager: AddonManager,
  private val streamEngine: StreamEngine,
  private val playbackTracker: StremioPlaybackTracker,
  private val tmdbSearchClient: TmdbSearchClient,
) : ViewModel() {

  private val _metaState = MutableStateFlow<StremioMeta?>(null)
  val metaState: StateFlow<StremioMeta?> = _metaState.asStateFlow()

  private val _isLoadingMeta = MutableStateFlow(true)
  val isLoadingMeta: StateFlow<Boolean> = _isLoadingMeta.asStateFlow()

  private val _selectedSeason = MutableStateFlow(1)
  val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

  private val _streamsState = MutableStateFlow<List<StremioStream>>(emptyList())
  val streamsState: StateFlow<List<StremioStream>> = _streamsState.asStateFlow()

  private val _isLoadingStreams = MutableStateFlow(false)
  val isLoadingStreams: StateFlow<Boolean> = _isLoadingStreams.asStateFlow()

  private val _showStreamSheet = MutableStateFlow(false)
  val showStreamSheet: StateFlow<Boolean> = _showStreamSheet.asStateFlow()

  private val _currentStreamTitle = MutableStateFlow("")
  val currentStreamTitle: StateFlow<String> = _currentStreamTitle.asStateFlow()

  private val _currentStreamId = MutableStateFlow("")
  private val _selectedVideo = MutableStateFlow<StremioVideo?>(null)

  val seriesHistory: StateFlow<List<StremioPlaybackHistoryEntity>> =
    playbackTracker.getSeriesHistory(metaId).stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  init {
    loadMeta()
  }

  fun loadMeta() {
    viewModelScope.launch {
      _isLoadingMeta.value = true
      val resolvedMetaId = if (metaId.startsWith("tmdb:")) {
        tmdbSearchClient.resolveImdbId(metaId, type) ?: metaId
      } else {
        metaId
      }

      var meta = addonManager.queryMeta(type, resolvedMetaId)
      if (meta == null && resolvedMetaId != metaId) {
        meta = addonManager.queryMeta(type, metaId)
      }

      // If installed addons didn't return metadata or if item is from TMDB search, query TMDB direct API
      if (meta == null || metaId.startsWith("tmdb:")) {
        val tmdbMeta = tmdbSearchClient.getMetaDetails(metaId, type)
        if (tmdbMeta != null) {
          meta = if (meta != null) {
            meta.copy(
              videos = if (!tmdbMeta.videos.isNullOrEmpty()) tmdbMeta.videos else meta.videos,
              description = if (meta.description.isNullOrBlank()) tmdbMeta.description else meta.description,
              genres = if (!tmdbMeta.genres.isNullOrEmpty()) tmdbMeta.genres else meta.genres,
            )
          } else {
            tmdbMeta
          }
        }
      }

      _metaState.value = meta
      _isLoadingMeta.value = false
    }
  }

  fun selectSeason(season: Int) {
    _selectedSeason.value = season
  }

  fun openStreamsForMedia(title: String, targetStreamId: String, video: StremioVideo? = null) {
    _currentStreamTitle.value = title
    _currentStreamId.value = targetStreamId
    _selectedVideo.value = video
    _showStreamSheet.value = true
    viewModelScope.launch {
      _isLoadingStreams.value = true
      _streamsState.value = emptyList()
      val actualType = _metaState.value?.type?.ifBlank { type } ?: type
      val resolvedStreamId = if (targetStreamId.startsWith("tmdb:")) {
        tmdbSearchClient.resolveImdbId(targetStreamId, actualType) ?: targetStreamId
      } else {
        targetStreamId
      }
      val streams = addonManager.queryStreams(actualType, resolvedStreamId)
      _streamsState.value = streams
      _isLoadingStreams.value = false
    }
  }

  fun closeStreamSheet() {
    _showStreamSheet.value = false
  }

  fun playStream(context: Context, stream: StremioStream, mediaTitle: String) {
    viewModelScope.launch {
      val meta = _metaState.value
      val result = streamEngine.startStreamSession(context, stream)
      if (result.isSuccess) {
        val handle = result.getOrThrow()
        val playableUrl = handle.resolvedUrl
        val targetMediaId = if (_currentStreamId.value.isNotBlank()) _currentStreamId.value else metaId
        val actualType = meta?.type?.ifBlank { type } ?: type
        val video = _selectedVideo.value

        // Auto-resume lookup
        val existingHistory = playbackTracker.getHistory(targetMediaId)
        val startPosSeconds = if (existingHistory != null && !existingHistory.isFinished && existingHistory.lastPositionMs > 5000L) {
          (existingHistory.lastPositionMs / 1000L).toInt()
        } else {
          0
        }

        val launchToken = PreparedPlaybackLaunchStore.stage(
          items = listOf(
            PlaybackItem(
              stableId = targetMediaId,
              originalUri = playableUrl,
              title = mediaTitle,
            )
          ),
          currentIndex = 0,
        )

        val intent = Intent(context, PlayerActivity::class.java).apply {
          putExtra("launch_token", launchToken)
          putExtra("launch_source", "stremio_addon")
          putExtra("stremio_media_id", targetMediaId)
          putExtra("stremio_type", actualType)
          if (actualType == "series") {
            putExtra("stremio_series_id", metaId)
            putExtra("stremio_season", video?.season ?: 1)
            putExtra("stremio_episode", video?.episode ?: 1)
            putExtra("stremio_episode_title", mediaTitle)
          }
          putExtra("stremio_title", meta?.name ?: mediaTitle)
          putExtra("stremio_poster_url", meta?.poster)
          putExtra("stremio_banner_url", meta?.banner ?: meta?.background)
          if (startPosSeconds > 0) {
            putExtra(PlayerActivity.EXTRA_START_POSITION_SECONDS, startPosSeconds)
          }
        }
        context.startActivity(intent)
      }
    }
  }
}
