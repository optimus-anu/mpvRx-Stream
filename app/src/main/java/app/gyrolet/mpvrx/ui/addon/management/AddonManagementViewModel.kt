/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.addon.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gyrolet.mpvrx.data.addon.AddonManager
import app.gyrolet.mpvrx.database.entities.StremioAddonEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AddonManagementViewModel(
  private val addonManager: AddonManager,
) : ViewModel() {

  val installedAddons: StateFlow<List<StremioAddonEntity>> = addonManager.installedAddons.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList(),
  )

  private val _inputUrl = MutableStateFlow("")
  val inputUrl: StateFlow<String> = _inputUrl.asStateFlow()

  private val _isInstalling = MutableStateFlow(false)
  val isInstalling: StateFlow<Boolean> = _isInstalling.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  fun onInputUrlChanged(url: String) {
    _inputUrl.value = url
  }

  fun installAddon() {
    val url = _inputUrl.value.trim()
    if (url.isBlank()) return

    viewModelScope.launch {
      _isInstalling.value = true
      _errorMessage.value = null

      val result = addonManager.installAddon(url)
      _isInstalling.value = false

      if (result.isSuccess) {
        _inputUrl.value = ""
      } else {
        _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to install addon"
      }
    }
  }

  fun uninstallAddon(manifestUrl: String) {
    viewModelScope.launch {
      addonManager.uninstallAddon(manifestUrl)
    }
  }

  fun toggleAddonEnabled(manifestUrl: String, enabled: Boolean) {
    viewModelScope.launch {
      addonManager.toggleAddonEnabled(manifestUrl, enabled)
    }
  }

  fun clearError() {
    _errorMessage.value = null
  }
}
