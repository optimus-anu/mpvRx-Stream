/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.preferences

import app.gyrolet.mpvrx.preferences.preference.PreferenceStore
import app.gyrolet.mpvrx.preferences.preference.getEnum

enum class DebridProviderType(val displayName: String) {
  NONE("Disabled"),
  REAL_DEBRID("Real-Debrid"),
  TORBOX("TorBox"),
  ALL_DEBRID("AllDebrid"),
}

class DebridPreferences(
  preferenceStore: PreferenceStore,
) {
  val provider = preferenceStore.getEnum("debrid_provider", DebridProviderType.NONE)
  val realDebridApiKey = preferenceStore.getString("debrid_realdebrid_api_key", "")
  val torBoxApiKey = preferenceStore.getString("debrid_torbox_api_key", "")
  val allDebridApiKey = preferenceStore.getString("debrid_alldebrid_api_key", "")
  val enableDebrid = preferenceStore.getBoolean("debrid_enabled", true)
}
