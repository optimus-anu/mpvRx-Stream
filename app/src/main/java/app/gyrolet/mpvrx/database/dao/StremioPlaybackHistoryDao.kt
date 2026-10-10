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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.gyrolet.mpvrx.database.entities.StremioPlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StremioPlaybackHistoryDao {
  @Query("SELECT * FROM stremio_playback_history WHERE isFinished = 0 ORDER BY lastWatchedTimestamp DESC LIMIT :limit")
  fun getContinueWatching(limit: Int = 50): Flow<List<StremioPlaybackHistoryEntity>>

  @Query("SELECT * FROM stremio_playback_history ORDER BY lastWatchedTimestamp DESC")
  fun getAllHistory(): Flow<List<StremioPlaybackHistoryEntity>>

  @Query("SELECT * FROM stremio_playback_history WHERE mediaId = :mediaId LIMIT 1")
  suspend fun getHistory(mediaId: String): StremioPlaybackHistoryEntity?

  @Query("SELECT * FROM stremio_playback_history WHERE seriesId = :seriesId OR mediaId = :seriesId OR mediaId LIKE :seriesId || ':%'")
  fun getHistoryForSeries(seriesId: String): Flow<List<StremioPlaybackHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdate(history: StremioPlaybackHistoryEntity)

  @Query("DELETE FROM stremio_playback_history WHERE mediaId = :mediaId")
  suspend fun delete(mediaId: String)

  @Query("DELETE FROM stremio_playback_history")
  suspend fun clearAll()
}
