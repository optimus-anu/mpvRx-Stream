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
import app.gyrolet.mpvrx.domain.addon.model.StremioMeta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
) : ViewModel() {

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

  private val _searchResults = MutableStateFlow<List<StremioMeta>>(emptyList())
  val searchResults: StateFlow<List<StremioMeta>> = _searchResults.asStateFlow()

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
      _isSearching.value = false
    }
  }

  fun loadCatalogs() {
    viewModelScope.launch {
      _isLoadingCatalogs.value = true
      addonManager.ensureDefaultAddonsInstalled()

      val defaultCatalogs = listOf(
        Pair("movie", "top"),
        Pair("series", "top"),
      )

      val rows = mutableListOf<CatalogRowState>()

      for ((type, catalogId) in defaultCatalogs) {
        val items = addonManager.queryCatalogs(type, catalogId)
        if (items.isNotEmpty()) {
          val title = if (type == "movie") "Popular Movies" else "Popular TV Shows"
          rows.add(
            CatalogRowState(
              addonName = "Cinemeta",
              catalogTitle = title,
              type = type,
              catalogId = catalogId,
              items = items,
              isLoading = false,
            )
          )
        }
      }

      _catalogRows.value = rows
      _isLoadingCatalogs.value = false
    }
  }

  private fun performSearch(query: String) {
    viewModelScope.launch {
      _isSearching.value = true
      val movieResults = addonManager.queryCatalogs("movie", "top", mapOf("search" to query))
      val seriesResults = addonManager.queryCatalogs("series", "top", mapOf("search" to query))
      val combined = (movieResults + seriesResults).distinctBy { it.id }
      _searchResults.value = combined
      _isSearching.value = false
    }
  }
}
