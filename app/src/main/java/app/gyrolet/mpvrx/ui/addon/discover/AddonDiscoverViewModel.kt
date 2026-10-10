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
import app.gyrolet.mpvrx.data.addon.TmdbSearchClient
import app.gyrolet.mpvrx.database.entities.StremioPlaybackHistoryEntity
import app.gyrolet.mpvrx.domain.addon.model.StremioMeta
import kotlinx.coroutines.async
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
  private val tmdbSearchClient: TmdbSearchClient,
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

  private val _searchOtherResults = MutableStateFlow<List<StremioMeta>>(emptyList())
  val searchOtherResults: StateFlow<List<StremioMeta>> = _searchOtherResults.asStateFlow()

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
    if (query.isBlank()) {
      _searchResults.value = emptyList()
      _searchMovieResults.value = emptyList()
      _searchSeriesResults.value = emptyList()
      _searchOtherResults.value = emptyList()
      _isSearching.value = false
    }
  }

  fun performSearch() {
    val query = _searchQuery.value.trim()
    if (query.isBlank()) return

    viewModelScope.launch {
      _isSearching.value = true

      // Launch TMDB direct search and addon queries in parallel
      val tmdbJob = async { tmdbSearchClient.searchMulti(query).getOrDefault(emptyList()) }
      val addonJob = async { addonManager.querySearchAll(query) }

      // 1. Render TMDB results immediately (~120ms)
      val tmdbResults = tmdbJob.await()
      updateSearchState(tmdbResults)
      _isSearching.value = false

      // 2. Merge background addon search results as they arrive
      val addonResults = addonJob.await()
      if (addonResults.isNotEmpty()) {
        val merged = (tmdbResults + addonResults).distinctBy { it.id }
        updateSearchState(merged)
      }
    }
  }

  private fun updateSearchState(allResults: List<StremioMeta>) {
    val movieResults = allResults.filter { it.type == "movie" || it.type.contains("movie") }
    val seriesResults = allResults.filter { it.type == "series" || it.type.contains("series") || it.type == "tv" }
    val otherResults = allResults.filter { it !in movieResults && it !in seriesResults }

    _searchMovieResults.value = movieResults
    _searchSeriesResults.value = seriesResults
    _searchOtherResults.value = otherResults
    _searchResults.value = allResults
  }

  fun loadCatalogs() {
    viewModelScope.launch {
      // 1. Load cached rows immediately (<10ms)
      val cached = addonManager.getCachedCatalogGroups()
      if (cached.isNotEmpty()) {
        _catalogRows.value = cached.map { group ->
          CatalogRowState(
            addonName = group.addonName,
            catalogTitle = group.catalogTitle,
            type = group.type,
            catalogId = group.catalogId,
            items = group.items,
            isLoading = false,
          )
        }
        _isLoadingCatalogs.value = false
      } else {
        _isLoadingCatalogs.value = true
      }

      // 2. Stream fresh rows dynamically as each catalog resolves
      val currentRows = _catalogRows.value.toMutableList()

      addonManager.streamAllCatalogs().collect { newGroup ->
        val newRow = CatalogRowState(
          addonName = newGroup.addonName,
          catalogTitle = newGroup.catalogTitle,
          type = newGroup.type,
          catalogId = newGroup.catalogId,
          items = newGroup.items,
          isLoading = false,
        )

        val existingIndex = currentRows.indexOfFirst {
          it.addonName == newRow.addonName && it.catalogId == newRow.catalogId && it.type == newRow.type
        }

        if (existingIndex >= 0) {
          currentRows[existingIndex] = newRow
        } else {
          currentRows.add(newRow)
        }

        _catalogRows.value = currentRows.toList()
        _isLoadingCatalogs.value = false
      }

      _isLoadingCatalogs.value = false
    }
  }
}
