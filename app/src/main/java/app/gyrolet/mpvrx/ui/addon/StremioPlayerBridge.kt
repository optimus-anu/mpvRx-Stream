/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.addon

import android.content.Intent
import app.gyrolet.mpvrx.data.addon.StremioPlaybackTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object StremioPlayerBridge {
  private var progressJob: Job? = null

  fun isStremioLaunch(intent: Intent): Boolean {
    return intent.getStringExtra("launch_source") == "stremio_addon"
  }

  fun startProgressLoop(
    scope: CoroutineScope,
    intent: Intent,
    stremioPlaybackTracker: StremioPlaybackTracker,
    readMpvIntSeconds: (String, Int) -> Int,
    fallbackTitle: () -> String,
  ) {
    val mediaId = intent.getStringExtra("stremio_media_id") ?: return
    val type = intent.getStringExtra("stremio_type") ?: "movie"
    val seriesId = intent.getStringExtra("stremio_series_id")
    val season = intent.getIntExtra("stremio_season", -1).takeIf { it != -1 }
    val episode = intent.getIntExtra("stremio_episode", -1).takeIf { it != -1 }
    val title = intent.getStringExtra("stremio_title") ?: fallbackTitle()
    val episodeTitle = intent.getStringExtra("stremio_episode_title")
    val posterUrl = intent.getStringExtra("stremio_poster_url")
    val bannerUrl = intent.getStringExtra("stremio_banner_url")

    progressJob?.cancel()
    progressJob = scope.launch {
      while (isActive) {
        delay(5000)
        val posMs = (readMpvIntSeconds("time-pos", 0).toLong() * 1000L).coerceAtLeast(0L)
        val durMs = (readMpvIntSeconds("duration", 0).toLong() * 1000L).coerceAtLeast(0L)

        if (posMs > 0) {
          stremioPlaybackTracker.saveProgress(
            mediaId = mediaId,
            type = type,
            seriesId = seriesId,
            season = season,
            episode = episode,
            title = title,
            episodeTitle = episodeTitle,
            posterUrl = posterUrl,
            bannerUrl = bannerUrl,
            positionMs = posMs,
            durationMs = durMs,
          )
        }
      }
    }
  }

  fun reportStop(
    scope: CoroutineScope,
    intent: Intent,
    stremioPlaybackTracker: StremioPlaybackTracker,
    readMpvIntSeconds: (String, Int) -> Int,
    fallbackTitle: () -> String,
  ) {
    progressJob?.cancel()
    progressJob = null

    val mediaId = intent.getStringExtra("stremio_media_id") ?: return
    val type = intent.getStringExtra("stremio_type") ?: "movie"
    val seriesId = intent.getStringExtra("stremio_series_id")
    val season = intent.getIntExtra("stremio_season", -1).takeIf { it != -1 }
    val episode = intent.getIntExtra("stremio_episode", -1).takeIf { it != -1 }
    val title = intent.getStringExtra("stremio_title") ?: fallbackTitle()
    val episodeTitle = intent.getStringExtra("stremio_episode_title")
    val posterUrl = intent.getStringExtra("stremio_poster_url")
    val bannerUrl = intent.getStringExtra("stremio_banner_url")

    val posMs = (readMpvIntSeconds("time-pos", 0).toLong() * 1000L).coerceAtLeast(0L)
    val durMs = (readMpvIntSeconds("duration", 0).toLong() * 1000L).coerceAtLeast(0L)

    if (posMs > 0) {
      scope.launch(Dispatchers.IO + NonCancellable) {
        runCatching {
          stremioPlaybackTracker.saveProgress(
            mediaId = mediaId,
            type = type,
            seriesId = seriesId,
            season = season,
            episode = episode,
            title = title,
            episodeTitle = episodeTitle,
            posterUrl = posterUrl,
            bannerUrl = bannerUrl,
            positionMs = posMs,
            durationMs = durMs,
          )
        }
      }
    }
  }
}
