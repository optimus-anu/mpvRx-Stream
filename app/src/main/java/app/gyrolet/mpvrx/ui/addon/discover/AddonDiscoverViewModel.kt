/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.addon.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gyrolet.mpvrx.data.addon.AddonManager
import app.gyrolet.mpvrx.data.addon.StremioPlaybackTracker
import app.gyrolet.mpvrx.database.entities.StremioPlaybackHistoryEntity
import app.gyrolet.mpvrx.domain.addon.model.StremioMeta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CatalogRowState(
  val addonName: String,
  val catalogTitle: String,
  val type: String,
  val catalogId: String,
  val items: List<StremioMeta> = emptyList(),
  val isLoading: Boolean = true,
)

class AddonDiscoverViewModel(
  private val addonManager: AddonManager,
  playbackTracker: StremioPlaybackTracker,
) : ViewModel() {

  val continueWatching: StateFlow<List<StremioPlaybackHistoryEntity>> =
    playbackTracker.continueWatching.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

  private val _searchResults = MutableStateFlow<List<StremioMeta>>(emptyList())
  val searchResults: StateFlow<List<StremioMeta>> = _searchResults.asStateFlow()

  private val _searchMovieResults = MutableStateFlow<List<StremioMeta>>(emptyList())
  val searchMovieResults: StateFlow<List<StremioMeta>> = _searchMovieResults.asStateFlow()

  private val _searchSeriesResults = MutableStateFlow<List<StremioMeta>>(emptyList())
  val searchSeriesResults: StateFlow<List<StremioMeta>> = _searchSeriesResults.asStateFlow()

  private val _catalogRows = MutableStateFlow<List<CatalogRowState>>(emptyList())
  val catalogRows: StateFlow<List<CatalogRowState>> = _catalogRows.asStateFlow()

  private val _isLoadingCatalogs = MutableStateFlow(true)
  val isLoadingCatalogs: StateFlow<Boolean> = _isLoadingCatalogs.asStateFlow()

  init {
    viewModelScope.launch {
      addonManager.enabledAddons.collectLatest {
        loadCatalogs()
      }
    }
  }

  fun onSearchQueryChanged(query: String) {
    _searchQuery.value = query
    if (query.isNotBlank()) {
      performSearch(query)
    } else {
      _searchResults.value = emptyList()
      _searchMovieResults.value = emptyList()
      _searchSeriesResults.value = emptyList()
      _isSearching.value = false
    }
  }

  fun loadCatalogs() {
    viewModelScope.launch {
      _isLoadingCatalogs.value = true
      val groups = addonManager.queryAllCatalogs()

      val rows = groups.map { group ->
        CatalogRowState(
          addonName = group.addonName,
          catalogTitle = group.catalogTitle,
          type = group.type,
          catalogId = group.catalogId,
          items = group.items,
          isLoading = false,
        )
      }

      _catalogRows.value = rows
      _isLoadingCatalogs.value = false
    }
  }

  private fun performSearch(query: String) {
    viewModelScope.launch {
      _isSearching.value = true
      val allResults = addonManager.querySearchAll(query)

      val movieResults = allResults.filter { it.type == "movie" }
      val seriesResults = allResults.filter { it.type == "series" }
      val otherResults = allResults.filter { it.type != "movie" && it.type != "series" }

      _searchMovieResults.value = movieResults
      _searchSeriesResults.value = seriesResults.ifEmpty { otherResults }
      _searchResults.value = allResults
      _isSearching.value = false
    }
  }
}
