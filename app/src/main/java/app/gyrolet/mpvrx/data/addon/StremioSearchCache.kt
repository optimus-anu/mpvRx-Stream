/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.data.addon

import android.content.Context
import android.util.Log
import app.gyrolet.mpvrx.domain.addon.model.StremioMeta
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Serializable
private data class DiskSearchPayload(
  val query: String,
  val items: List<StremioMeta>,
  val timestampMs: Long,
)

class StremioSearchCache(
  context: Context,
  private val json: Json = Json { ignoreUnknownKeys = true },
) {
  companion object {
    private const val TAG = "StremioSearchCache"
    private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
  }

  private val memoryCache = ConcurrentHashMap<String, List<StremioMeta>>()
  private val diskCacheDir = File(context.cacheDir, "stremio_search_cache").also {
    if (!it.exists()) it.mkdirs()
  }

  fun get(query: String): List<StremioMeta>? {
    val cleanQuery = query.trim().lowercase()
    if (cleanQuery.isBlank()) return null
    val items = memoryCache[cleanQuery] ?: loadFromDisk(cleanQuery)
    return items
  }

  fun put(query: String, items: List<StremioMeta>) {
    val cleanQuery = query.trim().lowercase()
    if (cleanQuery.isBlank() || items.isEmpty()) return
    memoryCache[cleanQuery] = items
    saveToDiskAsync(cleanQuery, items)
  }

  private fun loadFromDisk(key: String): List<StremioMeta>? {
    return try {
      val file = File(diskCacheDir, "$key.json")
      if (!file.exists()) return null
      val payload = json.decodeFromString<DiskSearchPayload>(file.readText())
      if (System.currentTimeMillis() - payload.timestampMs < CACHE_TTL_MS) {
        memoryCache[key] = payload.items
        payload.items
      } else {
        file.delete()
        null
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error loading search cache ($key)", e)
      null
    }
  }

  private fun saveToDiskAsync(key: String, items: List<StremioMeta>) {
    try {
      val file = File(diskCacheDir, "$key.json")
      val payload = DiskSearchPayload(key, items, System.currentTimeMillis())
      file.writeText(json.encodeToString(DiskSearchPayload.serializer(), payload))
    } catch (e: Exception) {
      Log.e(TAG, "Error saving search cache ($key)", e)
    }
  }
}
