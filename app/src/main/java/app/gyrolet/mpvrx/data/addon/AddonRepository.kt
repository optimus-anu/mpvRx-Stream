/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.data.addon

import app.gyrolet.mpvrx.database.dao.StremioAddonDao
import app.gyrolet.mpvrx.database.entities.StremioAddonEntity
import kotlinx.coroutines.flow.Flow

class AddonRepository(
  private val addonDao: StremioAddonDao,
) {
  val allAddons: Flow<List<StremioAddonEntity>> = addonDao.getAllAddons()
  val enabledAddons: Flow<List<StremioAddonEntity>> = addonDao.getEnabledAddons()

  suspend fun getAddonByManifestUrl(manifestUrl: String): StremioAddonEntity? =
    addonDao.getAddonByManifestUrl(manifestUrl)

  suspend fun getAddonById(id: String): StremioAddonEntity? =
    addonDao.getAddonById(id)

  suspend fun saveAddon(addon: StremioAddonEntity) {
    addonDao.insert(addon)
  }

  suspend fun removeAddon(manifestUrl: String) {
    addonDao.deleteByManifestUrl(manifestUrl)
  }

  suspend fun setAddonEnabled(manifestUrl: String, isEnabled: Boolean) {
    addonDao.setEnabled(manifestUrl, isEnabled)
  }
}
