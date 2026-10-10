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
import app.gyrolet.mpvrx.domain.addon.model.StremioManifest
import app.gyrolet.mpvrx.domain.addon.model.StremioMeta
import app.gyrolet.mpvrx.domain.addon.model.StremioMetaDetailResponse
import app.gyrolet.mpvrx.domain.addon.model.StremioMetaResponse
import app.gyrolet.mpvrx.domain.addon.model.StremioStream
import app.gyrolet.mpvrx.domain.addon.model.StremioStreamResponse
import app.gyrolet.mpvrx.network.SharedHttpClient
import app.gyrolet.mpvrx.network.awaitResponse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

open class AddonClient(
  okHttpClient: OkHttpClient = SharedHttpClient.base,
  private val json: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
  },
) {
  private val client: OkHttpClient = okHttpClient.newBuilder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  companion object {
    private const val TAG = "AddonClient"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 Stremio/4.4"
  }

  /**
   * Fetches and parses an addon manifest from a URL.
   */
  open suspend fun fetchManifest(rawUrl: String): Result<StremioManifest> {
    val manifestUrl = AddonUrlUtils.normalizeManifestUrl(rawUrl)
    return try {
      val request = Request.Builder()
        .url(manifestUrl)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/json")
        .get()
        .build()

      val response = client.newCall(request).awaitResponse()
      response.use { resp ->
        if (!resp.isSuccessful) {
          Log.e(TAG, "HTTP error fetching manifest ($manifestUrl): ${resp.code} ${resp.message}")
          return Result.failure(IOException("Failed to fetch manifest: HTTP ${resp.code} ${resp.message}"))
        }
        val body = resp.body.string()
        if (body.isBlank()) {
          return Result.failure(IOException("Empty response body from manifest ($manifestUrl)"))
        }
        val manifest = json.decodeFromString<StremioManifest>(body)
        Result.success(manifest)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Error fetching/parsing manifest from $manifestUrl", e)
      Result.failure(e)
    }
  }

  /**
   * Queries a catalog from an addon (`/catalog/{type}/{id}.json` or `/catalog/{type}/{id}/{extraArgs}.json`).
   */
  open suspend fun fetchCatalog(
    baseUrl: String,
    type: String,
    catalogId: String,
    extra: Map<String, String>? = null,
  ): Result<List<StremioMeta>> {
    val catalogUrl = AddonUrlUtils.buildCatalogUrl(baseUrl, type, catalogId, extra)
    return try {
      val request = Request.Builder()
        .url(catalogUrl)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/json")
        .get()
        .build()

      val response = client.newCall(request).awaitResponse()
      response.use { resp ->
        if (!resp.isSuccessful) {
          return Result.failure(IOException("Failed to fetch catalog ($catalogUrl): HTTP ${resp.code} ${resp.message}"))
        }
        val body = resp.body.string()
        if (body.isBlank()) {
          return Result.failure(IOException("Empty response body from catalog ($catalogUrl)"))
        }
        val responseData = json.decodeFromString<StremioMetaResponse>(body)
        Result.success(responseData.metas)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Error fetching catalog from $catalogUrl", e)
      Result.failure(e)
    }
  }

  /**
   * Queries metadata details for a media item from an addon (`/meta/{type}/{id}.json`).
   */
  open suspend fun fetchMeta(
    baseUrl: String,
    type: String,
    metaId: String,
  ): Result<StremioMeta> {
    val metaUrl = AddonUrlUtils.buildMetaUrl(baseUrl, type, metaId)
    return try {
      val request = Request.Builder()
        .url(metaUrl)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/json")
        .get()
        .build()

      val response = client.newCall(request).awaitResponse()
      response.use { resp ->
        if (!resp.isSuccessful) {
          return Result.failure(IOException("Failed to fetch meta ($metaUrl): HTTP ${resp.code} ${resp.message}"))
        }
        val body = resp.body.string()
        if (body.isBlank()) {
          return Result.failure(IOException("Empty response body from meta ($metaUrl)"))
        }
        val responseData = json.decodeFromString<StremioMetaDetailResponse>(body)
        val meta = responseData.meta
          ?: return Result.failure(IOException("No meta found in response from ($metaUrl)"))
        Result.success(meta)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Error fetching meta from $metaUrl", e)
      Result.failure(e)
    }
  }

  /**
   * Resolves stream descriptors for a media item from an addon (`/stream/{type}/{id}.json`).
   */
  open suspend fun fetchStreams(
    baseUrl: String,
    type: String,
    streamId: String,
  ): Result<List<StremioStream>> {
    val streamUrl = AddonUrlUtils.buildStreamUrl(baseUrl, type, streamId)
    return try {
      val request = Request.Builder()
        .url(streamUrl)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/json")
        .get()
        .build()

      val response = client.newCall(request).awaitResponse()
      response.use { resp ->
        if (!resp.isSuccessful) {
          return Result.failure(IOException("Failed to fetch streams ($streamUrl): HTTP ${resp.code} ${resp.message}"))
        }
        val body = resp.body.string()
        if (body.isBlank()) {
          return Result.failure(IOException("Empty response body from streams ($streamUrl)"))
        }
        val responseData = json.decodeFromString<StremioStreamResponse>(body)
        Result.success(responseData.streams)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Error fetching streams from $streamUrl", e)
      Result.failure(e)
    }
  }
}
