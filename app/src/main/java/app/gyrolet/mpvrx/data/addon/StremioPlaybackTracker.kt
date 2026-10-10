/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.data.addon

import app.gyrolet.mpvrx.database.dao.StremioPlaybackHistoryDao
import app.gyrolet.mpvrx.database.entities.StremioPlaybackHistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class StremioPlaybackTracker(
  private val historyDao: StremioPlaybackHistoryDao,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /**
   * Flow of unfinished media items for "Continue Watching".
   * For series, groups by series ID and retains only the LATEST played episode.
   */
  val continueWatching: Flow<List<StremioPlaybackHistoryEntity>> =
    historyDao.getAllHistory().map { historyList ->
      historyList
        .filter { !it.isFinished && it.lastPositionMs > 5000L }
        .groupBy { it.seriesId ?: it.mediaId }
        .map { (_, items) -> items.maxBy { it.lastWatchedTimestamp } }
        .sortedByDescending { it.lastWatchedTimestamp }
    }

  suspend fun getHistory(mediaId: String): StremioPlaybackHistoryEntity? {
    return historyDao.getHistory(mediaId)
  }

  fun getSeriesHistory(seriesId: String): Flow<List<StremioPlaybackHistoryEntity>> {
    return historyDao.getHistoryForSeries(seriesId)
  }

  fun saveProgress(
    mediaId: String,
    type: String,
    seriesId: String? = null,
    season: Int? = null,
    episode: Int? = null,
    title: String,
    episodeTitle: String? = null,
    posterUrl: String? = null,
    bannerUrl: String? = null,
    positionMs: Long,
    durationMs: Long,
  ) {
    if (mediaId.isBlank() || positionMs < 0) return

    val derivedSeriesId = seriesId
      ?: if (type == "series" && mediaId.contains(":")) mediaId.substringBefore(":") else null

    val isFinished = if (durationMs > 0) {
      (positionMs.toFloat() / durationMs.toFloat()) >= 0.90f
    } else {
      false
    }

    scope.launch {
      val existing = historyDao.getHistory(mediaId)
      val entity = StremioPlaybackHistoryEntity(
        mediaId = mediaId,
        type = type,
        seriesId = derivedSeriesId ?: existing?.seriesId,
        season = season ?: existing?.season,
        episode = episode ?: existing?.episode,
        title = title,
        episodeTitle = episodeTitle ?: existing?.episodeTitle,
        posterUrl = posterUrl ?: existing?.posterUrl,
        bannerUrl = bannerUrl ?: existing?.bannerUrl,
        lastPositionMs = positionMs,
        durationMs = if (durationMs > 0) durationMs else (existing?.durationMs ?: 0L),
        lastWatchedTimestamp = System.currentTimeMillis(),
        isFinished = isFinished,
      )
      historyDao.insertOrUpdate(entity)
    }
  }

  fun removeHistory(mediaId: String) {
    scope.launch {
      historyDao.delete(mediaId)
    }
  }
}
