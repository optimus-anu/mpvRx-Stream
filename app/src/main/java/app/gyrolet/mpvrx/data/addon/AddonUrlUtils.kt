/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.data.addon

import java.net.URLEncoder

object AddonUrlUtils {

  /**
   * Normalizes a raw addon manifest URL.
   * Converts `stremio://` or `stremios://` to `https://`.
   * Ensures the URL ends with `/manifest.json`.
   */
  fun normalizeManifestUrl(rawUrl: String): String {
    var url = rawUrl.trim()
    if (url.startsWith("stremio://", ignoreCase = true)) {
      url = "https://" + url.substring("stremio://".length)
    } else if (url.startsWith("stremios://", ignoreCase = true)) {
      url = "https://" + url.substring("stremios://".length)
    } else if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
      url = "https://$url"
    }

    // Remove trailing slashes
    url = url.trimEnd('/')

    if (!url.endsWith("/manifest.json", ignoreCase = true)) {
      url = "$url/manifest.json"
    }

    return url
  }

  /**
   * Derives the base URL of an addon given its manifest URL.
   * e.g., `https://v3-cinemeta.strem.io/manifest.json` -> `https://v3-cinemeta.strem.io`
   */
  fun deriveBaseUrl(manifestUrl: String): String {
    val normalized = normalizeManifestUrl(manifestUrl)
    return normalized.substringBeforeLast("/manifest.json").trimEnd('/')
  }

  /**
   * Builds the catalog query URL according to Stremio Protocol v3.
   * Format: `{baseUrl}/catalog/{type}/{id}.json` or `{baseUrl}/catalog/{type}/{id}/{extraArgs}.json`
   */
  fun buildCatalogUrl(
    baseUrl: String,
    type: String,
    catalogId: String,
    extra: Map<String, String>? = null,
  ): String {
    val cleanBase = baseUrl.trimEnd('/')
    val encodedType = encode(type)
    val encodedId = encode(catalogId)

    val extraArgs = if (!extra.isNullOrEmpty()) {
      val argsString = extra.entries.joinToString("&") { (key, value) ->
        "${encode(key)}=${encode(value)}"
      }
      "/$argsString"
    } else {
      ""
    }

    return "$cleanBase/catalog/$encodedType/$encodedId$extraArgs.json"
  }

  /**
   * Builds the metadata detail query URL according to Stremio Protocol v3.
   * Format: `{baseUrl}/meta/{type}/{id}.json`
   */
  fun buildMetaUrl(baseUrl: String, type: String, metaId: String): String {
    val cleanBase = baseUrl.trimEnd('/')
    val encodedType = encode(type)
    val encodedId = encode(metaId)
    return "$cleanBase/meta/$encodedType/$encodedId.json"
  }

  /**
   * Builds the stream resolver query URL according to Stremio Protocol v3.
   * Format: `{baseUrl}/stream/{type}/{id}.json`
   */
  fun buildStreamUrl(baseUrl: String, type: String, streamId: String): String {
    val cleanBase = baseUrl.trimEnd('/')
    val encodedType = encode(type)
    val encodedId = encode(streamId)
    return "$cleanBase/stream/$encodedType/$encodedId.json"
  }

  private fun encode(value: String): String {
    return try {
      URLEncoder.encode(value, "UTF-8")
        .replace("+", "%20")
        .replace("%3A", ":")
        .replace("%3a", ":")
    } catch (_: Exception) {
      value
    }
  }
}
