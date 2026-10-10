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
private data class DiskMetaPayload(
  val metaId: String,
  val meta: StremioMeta,
  val timestampMs: Long,
)

class StremioMetaCache(
  context: Context,
  private val json: Json = Json { ignoreUnknownKeys = true },
) {
  companion object {
    private const val TAG = "StremioMetaCache"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
  }

  private val memoryCache = ConcurrentHashMap<String, StremioMeta>()
  private val diskCacheDir = File(context.cacheDir, "stremio_meta_cache").also {
    if (!it.exists()) it.mkdirs()
  }

  fun get(metaId: String): StremioMeta? {
    val cleanId = metaId.trim().lowercase()
    if (cleanId.isBlank()) return null
    return memoryCache[cleanId] ?: loadFromDisk(cleanId)
  }

  fun put(metaId: String, meta: StremioMeta) {
    val cleanId = metaId.trim().lowercase()
    if (cleanId.isBlank()) return
    memoryCache[cleanId] = meta
    saveToDiskAsync(cleanId, meta)
  }

  private fun loadFromDisk(key: String): StremioMeta? {
    return try {
      val file = File(diskCacheDir, "$key.json")
      if (!file.exists()) return null
      val payload = json.decodeFromString<DiskMetaPayload>(file.readText())
      if (System.currentTimeMillis() - payload.timestampMs < CACHE_TTL_MS) {
        memoryCache[key] = payload.meta
        payload.meta
      } else {
        file.delete()
        null
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error loading meta cache ($key)", e)
      null
    }
  }

  private fun saveToDiskAsync(key: String, meta: StremioMeta) {
    try {
      val file = File(diskCacheDir, "$key.json")
      val payload = DiskMetaPayload(key, meta, System.currentTimeMillis())
      file.writeText(json.encodeToString(DiskMetaPayload.serializer(), payload))
    } catch (e: Exception) {
      Log.e(TAG, "Error saving meta cache ($key)", e)
    }
  }
}
