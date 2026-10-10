/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.data.addon

import android.util.Log
import app.gyrolet.mpvrx.domain.addon.model.StremioMeta
import app.gyrolet.mpvrx.domain.addon.model.StremioVideo
import app.gyrolet.mpvrx.network.SharedHttpClient
import app.gyrolet.mpvrx.network.awaitResponse
import app.gyrolet.mpvrx.preferences.DebridPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale

@Serializable
private data class TmdbSearchResponse(
  val results: List<TmdbItem> = emptyList(),
)

@Serializable
private data class TmdbItem(
  val id: Long,
  val media_type: String? = null,
  val title: String? = null,
  val name: String? = null,
  val poster_path: String? = null,
  val backdrop_path: String? = null,
  val overview: String? = null,
  val vote_average: Double? = null,
  val release_date: String? = null,
  val first_air_date: String? = null,
)

@Serializable
private data class TmdbExternalIdsResponse(
  val id: Long? = null,
  val imdb_id: String? = null,
)

@Serializable
private data class TmdbTvDetailResponse(
  val id: Long,
  val name: String? = null,
  val overview: String? = null,
  val poster_path: String? = null,
  val backdrop_path: String? = null,
  val vote_average: Double? = null,
  val first_air_date: String? = null,
  val genres: List<TmdbGenre>? = null,
  val number_of_seasons: Int? = null,
  val seasons: List<TmdbSeasonInfo>? = null,
  val external_ids: TmdbExternalIdsResponse? = null,
)

@Serializable
private data class TmdbMovieDetailResponse(
  val id: Long,
  val title: String? = null,
  val overview: String? = null,
  val poster_path: String? = null,
  val backdrop_path: String? = null,
  val vote_average: Double? = null,
  val release_date: String? = null,
  val genres: List<TmdbGenre>? = null,
  val external_ids: TmdbExternalIdsResponse? = null,
)

@Serializable
private data class TmdbGenre(
  val id: Int,
  val name: String,
)

@Serializable
private data class TmdbSeasonInfo(
  val season_number: Int,
  val episode_count: Int? = null,
  val name: String? = null,
)

@Serializable
private data class TmdbSeasonDetailResponse(
  val season_number: Int,
  val episodes: List<TmdbEpisodeItem> = emptyList(),
)

@Serializable
private data class TmdbEpisodeItem(
  val id: Long,
  val episode_number: Int,
  val season_number: Int,
  val name: String? = null,
  val overview: String? = null,
  val still_path: String? = null,
  val air_date: String? = null,
)

class TmdbSearchClient(
  private val debridPreferences: DebridPreferences,
  private val searchCache: StremioSearchCache? = null,
  private val metaCache: StremioMetaCache? = null,
  okHttpClient: OkHttpClient = SharedHttpClient.base,
  private val json: Json = Json { ignoreUnknownKeys = true },
) {
  companion object {
    private const val TAG = "TmdbSearchClient"
    private const val DEFAULT_API_KEY = "5b589fccce5bc58d331feb8fc1ccdfb2"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
  }

  private val client: OkHttpClient = okHttpClient.newBuilder().build()

  private fun getEffectiveApiKey(): String {
    val configuredKey = debridPreferences.tmdbApiKey.get().trim()
    return if (configuredKey.isNotBlank()) configuredKey else DEFAULT_API_KEY
  }

  /**
   * Direct multi-search against official TMDB API with memory & disk caching.
   */
  suspend fun searchMulti(query: String): Result<List<StremioMeta>> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return Result.success(emptyList())

    // Check cache first (<5ms)
    val cached = searchCache?.get(trimmed)
    if (!cached.isNullOrEmpty()) {
      return Result.success(cached)
    }

    return try {
      val apiKey = getEffectiveApiKey()
      val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
      val url = "https://api.themoviedb.org/3/search/multi?api_key=$apiKey&query=$encodedQuery&include_adult=false&language=en-US"

      val request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/json")
        .get()
        .build()

      val response = client.newCall(request).awaitResponse()
      response.use { resp ->
        if (!resp.isSuccessful) {
          Log.e(TAG, "TMDB search error: HTTP ${resp.code}")
          return Result.failure(IOException("TMDB search failed with HTTP ${resp.code}"))
        }

        val body = resp.body.string()
        if (body.isBlank()) return Result.success(emptyList())

        val data = json.decodeFromString<TmdbSearchResponse>(body)
        val metas = data.results.mapNotNull { item ->
          val mediaType = item.media_type ?: "movie"
          if (mediaType != "movie" && mediaType != "tv") return@mapNotNull null

          val titleText = item.title ?: item.name ?: return@mapNotNull null
          val posterUrl = item.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
          val backdropUrl = item.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" }
          val releaseDate = item.release_date ?: item.first_air_date
          val yearStr = releaseDate?.substringBefore("-")

          StremioMeta(
            id = "tmdb:${item.id}",
            type = if (mediaType == "tv") "series" else "movie",
            name = titleText,
            poster = posterUrl,
            background = backdropUrl,
            banner = backdropUrl,
            description = item.overview,
            imdbRating = item.vote_average?.let { String.format(Locale.US, "%.1f", it) },
            year = yearStr,
            releaseInfo = yearStr,
          )
        }

        if (metas.isNotEmpty()) {
          searchCache?.put(trimmed, metas)
        }

        Result.success(metas)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Error executing direct TMDB search", e)
      Result.failure(e)
    }
  }

  /**
   * Fetches full metadata details directly from official TMDB API in parallel with caching.
   */
  suspend fun getMetaDetails(rawId: String, type: String): StremioMeta? = coroutineScope {
    val cleanId = rawId.removePrefix("tmdb:").trim()
    if (cleanId.isBlank()) return@coroutineScope null

    // Check cache first (<5ms)
    val cached = metaCache?.get(rawId)
    if (cached != null) {
      return@coroutineScope cached
    }

    val apiKey = getEffectiveApiKey()

    try {
      if (type == "series" || type == "tv") {
        val url = "https://api.themoviedb.org/3/tv/$cleanId?api_key=$apiKey&append_to_response=external_ids&language=en-US"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
        val response = client.newCall(request).awaitResponse()
        response.use { resp ->
          if (!resp.isSuccessful) return@coroutineScope null
          val data = json.decodeFromString<TmdbTvDetailResponse>(resp.body.string())
          val posterUrl = data.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
          val backdropUrl = data.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" }
          val yearStr = data.first_air_date?.substringBefore("-")
          val imdbId = data.external_ids?.imdb_id

          val seasonList = data.seasons?.filter { it.season_number > 0 } ?: emptyList()

          // Parallel season fetching
          val seasonJobs = seasonList.map { s ->
            async {
              val seasonUrl = "https://api.themoviedb.org/3/tv/$cleanId/season/${s.season_number}?api_key=$apiKey&language=en-US"
              val sReq = Request.Builder().url(seasonUrl).header("User-Agent", USER_AGENT).get().build()
              val sResp = client.newCall(sReq).awaitResponse()
              sResp.use { sRes ->
                if (sRes.isSuccessful) {
                  val sData = json.decodeFromString<TmdbSeasonDetailResponse>(sRes.body.string())
                  sData.episodes.map { ep ->
                    val epStreamId = if (!imdbId.isNullOrBlank()) {
                      "$imdbId:${ep.season_number}:${ep.episode_number}"
                    } else {
                      "tmdb:$cleanId:${ep.season_number}:${ep.episode_number}"
                    }
                    StremioVideo(
                      id = epStreamId,
                      title = ep.name,
                      season = ep.season_number,
                      episode = ep.episode_number,
                      released = ep.air_date,
                      overview = ep.overview,
                      thumbnail = ep.still_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                    )
                  }
                } else {
                  emptyList()
                }
              }
            }
          }

          val allVideos = seasonJobs.awaitAll().flatten()

          val metaResult = StremioMeta(
            id = imdbId ?: rawId,
            type = "series",
            name = data.name ?: "Unknown Series",
            poster = posterUrl,
            background = backdropUrl,
            banner = backdropUrl,
            description = data.overview,
            imdbRating = data.vote_average?.let { String.format(Locale.US, "%.1f", it) },
            year = yearStr,
            releaseInfo = yearStr,
            genres = data.genres?.map { it.name },
            videos = allVideos,
          )

          metaCache?.put(rawId, metaResult)
          metaResult
        }
      } else {
        val url = "https://api.themoviedb.org/3/movie/$cleanId?api_key=$apiKey&append_to_response=external_ids&language=en-US"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
        val response = client.newCall(request).awaitResponse()
        response.use { resp ->
          if (!resp.isSuccessful) return@coroutineScope null
          val data = json.decodeFromString<TmdbMovieDetailResponse>(resp.body.string())
          val posterUrl = data.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
          val backdropUrl = data.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" }
          val yearStr = data.release_date?.substringBefore("-")
          val imdbId = data.external_ids?.imdb_id

          val metaResult = StremioMeta(
            id = imdbId ?: rawId,
            type = "movie",
            name = data.title ?: "Unknown Movie",
            poster = posterUrl,
            background = backdropUrl,
            banner = backdropUrl,
            description = data.overview,
            imdbRating = data.vote_average?.let { String.format(Locale.US, "%.1f", it) },
            year = yearStr,
            releaseInfo = yearStr,
            genres = data.genres?.map { it.name },
          )

          metaCache?.put(rawId, metaResult)
          metaResult
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error fetching TMDB meta details for $rawId", e)
      null
    }
  }

  /**
   * Resolves official IMDb ID (`tt0903747`) for a TMDB media ID.
   */
  suspend fun resolveImdbId(tmdbId: String, type: String): String? {
    val cleanId = tmdbId.removePrefix("tmdb:").trim()
    if (cleanId.isBlank()) return null

    return try {
      val apiKey = getEffectiveApiKey()
      val endpoint = if (type == "series" || type == "tv") "tv" else "movie"
      val url = "https://api.themoviedb.org/3/$endpoint/$cleanId/external_ids?api_key=$apiKey"

      val request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/json")
        .get()
        .build()

      val response = client.newCall(request).awaitResponse()
      response.use { resp ->
        if (!resp.isSuccessful) return null
        val body = resp.body.string()
        if (body.isBlank()) return null
        val data = json.decodeFromString<TmdbExternalIdsResponse>(body)
        data.imdb_id?.takeIf { it.isNotBlank() }
      }
    } catch (_: Exception) {
      null
    }
  }
}
