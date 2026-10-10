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
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "stremio_playback_history",
  indices = [
    Index(value = ["lastWatchedTimestamp"]),
    Index(value = ["seriesId"]),
  ],
)
data class StremioPlaybackHistoryEntity(
  @PrimaryKey
  val mediaId: String,
  val type: String,
  val seriesId: String? = null,
  val season: Int? = null,
  val episode: Int? = null,
  val title: String,
  val episodeTitle: String? = null,
  val posterUrl: String? = null,
  val bannerUrl: String? = null,
  val lastPositionMs: Long = 0L,
  val durationMs: Long = 0L,
  val lastWatchedTimestamp: Long = System.currentTimeMillis(),
  val isFinished: Boolean = false,
) {
  val progressRatio: Float
    get() = if (durationMs > 0) (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}
