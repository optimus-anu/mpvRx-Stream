/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.data.addon

import app.gyrolet.mpvrx.database.entities.StremioAddonEntity
import app.gyrolet.mpvrx.domain.addon.model.StremioManifest
import app.gyrolet.mpvrx.domain.addon.model.StremioMeta
import app.gyrolet.mpvrx.domain.addon.model.StremioStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class AddonManager(
  private val addonClient: AddonClient,
  private val addonRepository: AddonRepository,
  private val json: Json = Json { ignoreUnknownKeys = true },
) {
  val installedAddons: Flow<List<StremioAddonEntity>> = addonRepository.allAddons
  val enabledAddons: Flow<List<StremioAddonEntity>> = addonRepository.enabledAddons

  /**
   * Installs an addon by its manifest URL.
   * Fetches and validates the manifest, then persists the installed addon in storage.
   */
  suspend fun installAddon(rawManifestUrl: String): Result<StremioManifest> {
    val normalizedUrl = AddonUrlUtils.normalizeManifestUrl(rawManifestUrl)
    val manifestResult = addonClient.fetchManifest(normalizedUrl)
    if (manifestResult.isFailure) {
      return manifestResult
    }

    val manifest = manifestResult.getOrThrow()
    val baseUrl = AddonUrlUtils.deriveBaseUrl(normalizedUrl)

    val entity = StremioAddonEntity(
      manifestUrl = normalizedUrl,
      id = manifest.id,
      name = manifest.name,
      version = manifest.version,
      description = manifest.description,
      icon = manifest.logo ?: manifest.background,
      baseUrl = baseUrl,
      types = manifest.types.joinToString(","),
      manifestJson = json.encodeToString(StremioManifest.serializer(), manifest),
      isEnabled = true,
      installedAt = System.currentTimeMillis(),
    )

    addonRepository.saveAddon(entity)
    return Result.success(manifest)
  }

  /**
   * Uninstalls an addon by its manifest URL.
   */
  suspend fun uninstallAddon(rawManifestUrl: String) {
    val normalizedUrl = AddonUrlUtils.normalizeManifestUrl(rawManifestUrl)
    addonRepository.removeAddon(normalizedUrl)
  }

  /**
   * Enables or disables an installed addon.
   */
  suspend fun toggleAddonEnabled(rawManifestUrl: String, isEnabled: Boolean) {
    val normalizedUrl = AddonUrlUtils.normalizeManifestUrl(rawManifestUrl)
    addonRepository.setAddonEnabled(normalizedUrl, isEnabled)
  }

  /**
   * Parses the cached manifest stored inside a [StremioAddonEntity].
   */
  fun getParsedManifest(addon: StremioAddonEntity): StremioManifest? {
    return try {
      json.decodeFromString<StremioManifest>(addon.manifestJson)
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Queries catalogs from all enabled installed addons supporting the requested catalog type and ID.
   */
  suspend fun queryCatalogs(
    type: String,
    catalogId: String,
    extra: Map<String, String>? = null,
  ): List<StremioMeta> = coroutineScope {
    val enabledList = addonRepository.enabledAddons.first()
    val eligibleAddons = enabledList.filter { addonEntity ->
      val manifest = getParsedManifest(addonEntity) ?: return@filter false
      val hasCatalogResource = manifest.resources.any { res ->
        res.name == "catalog" && res.supportsType(type)
      }
      val hasCatalogId = manifest.catalogs.any { cat ->
        cat.type == type && cat.id == catalogId
      }
      hasCatalogResource && hasCatalogId
    }

    val deferreds = eligibleAddons.map { addon ->
      async {
        val result = addonClient.fetchCatalog(addon.baseUrl, type, catalogId, extra)
        result.getOrDefault(emptyList())
      }
    }

    deferreds.awaitAll().flatten()
  }

  /**
   * Queries metadata details for a given media item ID from enabled addons.
   */
  suspend fun queryMeta(type: String, metaId: String): StremioMeta? = coroutineScope {
    val enabledList = addonRepository.enabledAddons.first()
    val eligibleAddons = enabledList.filter { addonEntity ->
      val manifest = getParsedManifest(addonEntity) ?: return@filter false
      manifest.resources.any { res ->
        res.name == "meta" && res.supportsType(type) && res.supportsId(metaId)
      }
    }

    val deferreds = eligibleAddons.map { addon ->
      async {
        val result = addonClient.fetchMeta(addon.baseUrl, type, metaId)
        result.getOrNull()
      }
    }

    val results = deferreds.awaitAll().filterNotNull()
    results.firstOrNull()
  }

  /**
   * Queries stream resolvers across all eligible enabled stream addons in parallel.
   * Merges and annotates stream items with addon details.
   */
  suspend fun queryStreams(type: String, streamId: String): List<StremioStream> = coroutineScope {
    val enabledList = addonRepository.enabledAddons.first()
    val eligibleAddons = enabledList.filter { addonEntity ->
      val manifest = getParsedManifest(addonEntity) ?: return@filter false
      manifest.resources.any { res ->
        res.name == "stream" && res.supportsType(type) && res.supportsId(streamId)
      }
    }

    val deferreds = eligibleAddons.map { addon ->
      async {
        val result = addonClient.fetchStreams(addon.baseUrl, type, streamId)
        val streams = result.getOrDefault(emptyList())
        streams.map { stream ->
          if (stream.addonName == null) stream.copy(addonName = addon.name) else stream
        }
      }
    }

    deferreds.awaitAll().flatten()
  }
}
