/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.data.debrid

import app.gyrolet.mpvrx.network.SharedHttpClient
import app.gyrolet.mpvrx.network.awaitResponse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

class AllDebridResolver(
  okHttpClient: OkHttpClient = SharedHttpClient.base,
  private val json: Json = Json { ignoreUnknownKeys = true },
) : DebridResolver {

  private val client = okHttpClient.newBuilder().build()

  @Serializable
  private data class AdFile(
    val link: String? = null,
    val filename: String? = null,
  )

  @Serializable
  private data class AdMagnet(
    val id: Long,
    val link: String? = null,
    val links: List<AdFile> = emptyList(),
  )

  @Serializable
  private data class AdUploadData(
    val magnets: List<AdMagnet> = emptyList(),
  )

  @Serializable
  private data class AdUploadResponse(
    val status: String,
    val data: AdUploadData? = null,
  )

  @Serializable
  private data class AdUnlockData(
    val link: String,
  )

  @Serializable
  private data class AdUnlockResponse(
    val status: String,
    val data: AdUnlockData? = null,
  )

  override suspend fun resolveTorrent(
    infoHash: String,
    fileIdx: Int?,
    apiKey: String,
  ): Result<String> {
    if (apiKey.isBlank()) {
      return Result.failure(IllegalArgumentException("AllDebrid API key is empty"))
    }

    val hash = infoHash.lowercase().trim()
    val magnetUri = "magnet:?xt=urn:btih:$hash"

    return try {
      val encodedMagnet = URLEncoder.encode(magnetUri, "UTF-8")
      val uploadUrl = "https://api.alldebrid.com/v4/magnet/upload?agent=mpvRx&apikey=$apiKey&magnets[]=$encodedMagnet"

      val uploadReq = Request.Builder()
        .url(uploadUrl)
        .get()
        .build()

      val uploadResp = client.newCall(uploadReq).awaitResponse()
      val unlockLink = uploadResp.use { resp ->
        if (!resp.isSuccessful) {
          throw IOException("AllDebrid Upload failed: HTTP ${resp.code}")
        }
        val body = resp.body.string()
        val parsed = json.decodeFromString<AdUploadResponse>(body)
        val magnet = parsed.data?.magnets?.firstOrNull()
          ?: throw IOException("AllDebrid no magnet data returned")

        val targetLink = if (fileIdx != null && fileIdx in magnet.links.indices) {
          magnet.links[fileIdx].link
        } else {
          magnet.link ?: magnet.links.firstOrNull()?.link
        }

        targetLink ?: throw IOException("AllDebrid no link found in magnet upload response")
      }

      val encodedLink = URLEncoder.encode(unlockLink, "UTF-8")
      val unlockUrl = "https://api.alldebrid.com/v4/link/unlock?agent=mpvRx&apikey=$apiKey&link=$encodedLink"

      val unlockReq = Request.Builder()
        .url(unlockUrl)
        .get()
        .build()

      val unlockResp = client.newCall(unlockReq).awaitResponse()
      val directUrl = unlockResp.use { resp ->
        if (!resp.isSuccessful) {
          throw IOException("AllDebrid Unlock failed: HTTP ${resp.code}")
        }
        val body = resp.body.string()
        val parsed = json.decodeFromString<AdUnlockResponse>(body)
        parsed.data?.link ?: throw IOException("AllDebrid no unlocked download URL returned")
      }

      Result.success(directUrl)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
