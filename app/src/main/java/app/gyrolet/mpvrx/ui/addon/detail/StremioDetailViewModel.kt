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
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gyrolet.mpvrx.data.addon.AddonManager
import app.gyrolet.mpvrx.domain.addon.model.StremioMeta
import app.gyrolet.mpvrx.domain.addon.model.StremioStream
import app.gyrolet.mpvrx.domain.stream.StreamEngine
import app.gyrolet.mpvrx.ui.player.PlaybackItem
import app.gyrolet.mpvrx.ui.player.PlayerActivity
import app.gyrolet.mpvrx.ui.player.PreparedPlaybackLaunchStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StremioDetailViewModel(
  val metaId: String,
  val type: String,
  private val addonManager: AddonManager,
  private val streamEngine: StreamEngine,
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

  init {
    loadMeta()
  }

  fun loadMeta() {
    viewModelScope.launch {
      _isLoadingMeta.value = true
      val meta = addonManager.queryMeta(type, metaId)
      _metaState.value = meta
      _isLoadingMeta.value = false
    }
  }

  fun selectSeason(season: Int) {
    _selectedSeason.value = season
  }

  fun openStreamsForMedia(title: String, targetStreamId: String) {
    _currentStreamTitle.value = title
    _showStreamSheet.value = true
    viewModelScope.launch {
      _isLoadingStreams.value = true
      _streamsState.value = emptyList()
      val streams = addonManager.queryStreams(type, targetStreamId)
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

        val playbackItem = PlaybackItem(
          stableId = "stremio_${metaId}_${stream.name ?: "stream"}",
          originalUri = playableUrl,
          playableUri = playableUrl,
          title = mediaTitle,
          artworkUri = meta?.poster ?: meta?.banner,
          headers = handle.proxyHeaders ?: emptyMap(),
        )

        val launchToken = PreparedPlaybackLaunchStore.stage(
          items = listOf(playbackItem),
          currentIndex = 0,
          isExplicitQueue = true,
        )

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(playableUrl)).apply {
          setClass(context, PlayerActivity::class.java)
          putExtra("internal_launch", true)
          putExtra("launch_source", "stremio_addon")
          putExtra(PlayerActivity.EXTRA_PREPARED_PLAYBACK_QUEUE, true)
          putExtra(PlayerActivity.EXTRA_PREPARED_PLAYBACK_TOKEN, launchToken)
          putExtra("title", mediaTitle)
          putExtra("filename", mediaTitle)
          setDataAndType(Uri.parse(playableUrl), "video/*")
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        closeStreamSheet()
      }
    }
  }
}
