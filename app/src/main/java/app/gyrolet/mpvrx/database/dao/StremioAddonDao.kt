/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.gyrolet.mpvrx.database.entities.StremioAddonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StremioAddonDao {
  @Query("SELECT * FROM stremio_addons ORDER BY installedAt ASC")
  fun getAllAddons(): Flow<List<StremioAddonEntity>>

  @Query("SELECT * FROM stremio_addons WHERE isEnabled = 1 ORDER BY installedAt ASC")
  fun getEnabledAddons(): Flow<List<StremioAddonEntity>>

  @Query("SELECT * FROM stremio_addons WHERE manifestUrl = :manifestUrl LIMIT 1")
  suspend fun getAddonByManifestUrl(manifestUrl: String): StremioAddonEntity?

  @Query("SELECT * FROM stremio_addons WHERE id = :id LIMIT 1")
  suspend fun getAddonById(id: String): StremioAddonEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(addon: StremioAddonEntity)

  @Update
  suspend fun update(addon: StremioAddonEntity)

  @Delete
  suspend fun delete(addon: StremioAddonEntity)

  @Query("DELETE FROM stremio_addons WHERE manifestUrl = :manifestUrl")
  suspend fun deleteByManifestUrl(manifestUrl: String)

  @Query("UPDATE stremio_addons SET isEnabled = :isEnabled WHERE manifestUrl = :manifestUrl")
  suspend fun setEnabled(manifestUrl: String, isEnabled: Boolean)
}
