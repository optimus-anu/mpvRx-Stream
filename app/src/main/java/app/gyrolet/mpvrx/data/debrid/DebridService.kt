/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.data.debrid

import app.gyrolet.mpvrx.preferences.DebridPreferences
import app.gyrolet.mpvrx.preferences.DebridProviderType

class DebridService(
  private val debridPreferences: DebridPreferences,
  private val realDebridResolver: RealDebridResolver,
  private val torBoxResolver: TorBoxResolver,
  private val allDebridResolver: AllDebridResolver,
) {

  fun isConfigured(): Boolean {
    if (!debridPreferences.enableDebrid.get()) return false
    return when (debridPreferences.provider.get()) {
      DebridProviderType.REAL_DEBRID -> debridPreferences.realDebridApiKey.get().isNotBlank()
      DebridProviderType.TORBOX -> debridPreferences.torBoxApiKey.get().isNotBlank()
      DebridProviderType.ALL_DEBRID -> debridPreferences.allDebridApiKey.get().isNotBlank()
      DebridProviderType.NONE -> false
    }
  }

  suspend fun resolveTorrent(
    infoHash: String,
    fileIdx: Int? = null,
  ): Result<String> {
    if (!debridPreferences.enableDebrid.get()) {
      return Result.failure(IllegalStateException("Debrid service is disabled"))
    }

    return when (val provider = debridPreferences.provider.get()) {
      DebridProviderType.REAL_DEBRID -> {
        val apiKey = debridPreferences.realDebridApiKey.get()
        realDebridResolver.resolveTorrent(infoHash, fileIdx, apiKey)
      }
      DebridProviderType.TORBOX -> {
        val apiKey = debridPreferences.torBoxApiKey.get()
        torBoxResolver.resolveTorrent(infoHash, fileIdx, apiKey)
      }
      DebridProviderType.ALL_DEBRID -> {
        val apiKey = debridPreferences.allDebridApiKey.get()
        allDebridResolver.resolveTorrent(infoHash, fileIdx, apiKey)
      }
      DebridProviderType.NONE -> {
        Result.failure(IllegalStateException("No Debrid provider selected"))
      }
    }
  }
}
