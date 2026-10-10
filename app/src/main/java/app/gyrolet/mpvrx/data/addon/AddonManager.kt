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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

data class DynamicCatalogGroup(
  val addonName: String,
  val catalogTitle: String,
  val type: String,
  val catalogId: String,
  val items: List<StremioMeta>,
)

class AddonManager(
  private val addonClient: AddonClient,
  private val addonRepository: AddonRepository,
  private val catalogCache: StremioCatalogCache? = null,
  private val json: Json = Json { ignoreUnknownKeys = true },
) {
  companion object {
    private const val ADDON_QUERY_TIMEOUT_MS = 3_500L
  }

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

  fun getCachedCatalogGroups(): List<DynamicCatalogGroup> {
    return catalogCache?.getAllCachedGroups() ?: emptyList()
  }

  /**
   * Progressive Flow stream that emits catalog rows immediately as each addon request resolves.
   */
  fun streamAllCatalogs(): Flow<DynamicCatalogGroup> = flow {
    val enabledList = addonRepository.enabledAddons.first()

    for (addonEntity in enabledList) {
      val manifest = getParsedManifest(addonEntity) ?: continue
      val hasCatalogResource = manifest.resources.any { res -> res.name == "catalog" }
      if (!hasCatalogResource) continue

      coroutineScope {
        val jobs = manifest.catalogs.map { catalog ->
          async {
            val rawTitle = catalog.name ?: catalog.id.replace("_", " ").replace("-", " ")
            val formattedTitle = "${addonEntity.name} - ${rawTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"

            // Check cache first
            val cachedItems = catalogCache?.get(addonEntity.name, catalog.id, catalog.type)
            if (!cachedItems.isNullOrEmpty()) {
              return@async DynamicCatalogGroup(
                addonName = addonEntity.name,
                catalogTitle = formattedTitle,
                type = catalog.type,
                catalogId = catalog.id,
                items = cachedItems,
              )
            }

            val items = withTimeoutOrNull(ADDON_QUERY_TIMEOUT_MS) {
              val res = addonClient.fetchCatalog(addonEntity.baseUrl, catalog.type, catalog.id)
              res.getOrDefault(emptyList())
            } ?: emptyList()

            if (items.isNotEmpty()) {
              catalogCache?.put(addonEntity.name, formattedTitle, catalog.id, catalog.type, items)
              DynamicCatalogGroup(
                addonName = addonEntity.name,
                catalogTitle = formattedTitle,
                type = catalog.type,
                catalogId = catalog.id,
                items = items,
              )
            } else {
              null
            }
          }
        }

        for (job in jobs) {
          val result = job.await()
          if (result != null) {
            emit(result)
          }
        }
      }
    }
  }

  /**
   * Queries catalog items for a search query across all enabled installed addons.
   * Automatically falls back to Cinemeta search if installed addons return zero search results or time out.
   */
  suspend fun querySearchAll(query: String): List<StremioMeta> = coroutineScope {
    val enabledList = addonRepository.enabledAddons.first()
    val searchExtra = mapOf("search" to query)

    val deferreds = enabledList.flatMap { addon ->
      val manifest = getParsedManifest(addon) ?: return@flatMap emptyList()
      val hasCatalogResource = manifest.resources.any { res -> res.name == "catalog" }
      if (!hasCatalogResource) return@flatMap emptyList()

      val searchCatalogs = manifest.catalogs.filter { cat ->
        cat.extra?.any { it.name == "search" } == true ||
          cat.extraSupported?.contains("search") == true ||
          cat.extraRequired?.contains("search") == true ||
          cat.id == "top" ||
          cat.id == "search" ||
          cat.id.contains("popular") ||
          cat.id.contains("movie") ||
          cat.id.contains("series") ||
          cat.id.contains("aio") ||
          cat.id.contains("tmdb") ||
          cat.id.contains("cyberflix")
      }.ifEmpty { manifest.catalogs }

      searchCatalogs.map { catalog ->
        async {
          withTimeoutOrNull(ADDON_QUERY_TIMEOUT_MS) {
            val res = addonClient.fetchCatalog(addon.baseUrl, catalog.type, catalog.id, searchExtra)
            res.getOrDefault(emptyList())
          } ?: emptyList()
        }
      }
    }

    deferreds.awaitAll().flatten().distinctBy { it.id }
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
        withTimeoutOrNull(ADDON_QUERY_TIMEOUT_MS) {
          val result = addonClient.fetchMeta(addon.baseUrl, type, metaId)
          result.getOrNull()
        }
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
        withTimeoutOrNull(ADDON_QUERY_TIMEOUT_MS) {
          val result = addonClient.fetchStreams(addon.baseUrl, type, streamId)
          val streams = result.getOrDefault(emptyList())
          streams.map { stream ->
            if (stream.addonName == null) stream.copy(addonName = addon.name) else stream
          }
        } ?: emptyList()
      }
    }

    deferreds.awaitAll().flatten()
  }
}
