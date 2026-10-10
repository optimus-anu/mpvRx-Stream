/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stremio_addons")
data class StremioAddonEntity(
  @PrimaryKey
  val manifestUrl: String,
  val id: String,
  val name: String,
  val version: String,
  val description: String? = null,
  val icon: String? = null,
  val baseUrl: String,
  val types: String,
  val manifestJson: String,
  val isEnabled: Boolean = true,
  val installedAt: Long = System.currentTimeMillis(),
)
