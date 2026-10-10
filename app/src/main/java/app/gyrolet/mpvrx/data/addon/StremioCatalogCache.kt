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

data class CachedCatalogEntry(
  val addonName: String,
  val catalogTitle: String,
  val type: String,
  val catalogId: String,
  val items: List<StremioMeta>,
  val timestampMs: Long,
)

@Serializable
private data class DiskCatalogPayload(
  val addonName: String,
  val catalogTitle: String,
  val type: String,
  val catalogId: String,
  val items: List<StremioMeta>,
  val timestampMs: Long,
)

class StremioCatalogCache(
  context: Context,
  private val json: Json = Json { ignoreUnknownKeys = true },
) {
  companion object {
    private const val TAG = "StremioCatalogCache"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
  }

  private val memoryCache = ConcurrentHashMap<String, CachedCatalogEntry>()
  private val diskCacheDir = File(context.cacheDir, "stremio_catalog_cache").also {
    if (!it.exists()) it.mkdirs()
  }

  fun get(addonName: String, catalogId: String, type: String): List<StremioMeta>? {
    val key = cacheKey(addonName, catalogId, type)
    val entry = memoryCache[key] ?: loadFromDisk(key)
    if (entry != null && System.currentTimeMillis() - entry.timestampMs < CACHE_TTL_MS) {
      return entry.items
    }
    return null
  }

  fun put(addonName: String, catalogTitle: String, catalogId: String, type: String, items: List<StremioMeta>) {
    if (items.isEmpty()) return
    val key = cacheKey(addonName, catalogId, type)
    val entry = CachedCatalogEntry(addonName, catalogTitle, type, catalogId, items, System.currentTimeMillis())
    memoryCache[key] = entry
    saveToDiskAsync(key, entry)
  }

  fun getAllCachedGroups(): List<DynamicCatalogGroup> {
    val results = mutableListOf<DynamicCatalogGroup>()
    val keys = diskCacheDir.list()?.map { it.removeSuffix(".json") } ?: emptyList()
    for (key in keys) {
      val entry = memoryCache[key] ?: loadFromDisk(key)
      if (entry != null && System.currentTimeMillis() - entry.timestampMs < CACHE_TTL_MS) {
        results.add(
          DynamicCatalogGroup(
            addonName = entry.addonName,
            catalogTitle = entry.catalogTitle,
            type = entry.type,
            catalogId = entry.catalogId,
            items = entry.items,
          )
        )
      }
    }
    return results
  }

  private fun cacheKey(addonName: String, catalogId: String, type: String): String {
    return "${addonName.lowercase().replace(" ", "_")}_${type}_$catalogId"
  }

  private fun loadFromDisk(key: String): CachedCatalogEntry? {
    return try {
      val file = File(diskCacheDir, "$key.json")
      if (!file.exists()) return null
      val text = file.readText()
      val payload = json.decodeFromString<DiskCatalogPayload>(text)
      CachedCatalogEntry(
        addonName = payload.addonName,
        catalogTitle = payload.catalogTitle,
        type = payload.type,
        catalogId = payload.catalogId,
        items = payload.items,
        timestampMs = payload.timestampMs,
      ).also { memoryCache[key] = it }
    } catch (e: Exception) {
      Log.e(TAG, "Error loading catalog from disk cache ($key)", e)
      null
    }
  }

  private fun saveToDiskAsync(key: String, entry: CachedCatalogEntry) {
    try {
      val file = File(diskCacheDir, "$key.json")
      val payload = DiskCatalogPayload(
        addonName = entry.addonName,
        catalogTitle = entry.catalogTitle,
        type = entry.type,
        catalogId = entry.catalogId,
        items = entry.items,
        timestampMs = entry.timestampMs,
      )
      val text = json.encodeToString(DiskCatalogPayload.serializer(), payload)
      file.writeText(text)
    } catch (e: Exception) {
      Log.e(TAG, "Error saving catalog to disk cache ($key)", e)
    }
  }
}
