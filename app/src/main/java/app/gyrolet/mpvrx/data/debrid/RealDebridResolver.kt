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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class RealDebridResolver(
  okHttpClient: OkHttpClient = SharedHttpClient.base,
  private val json: Json = Json { ignoreUnknownKeys = true },
) : DebridResolver {

  private val client = okHttpClient.newBuilder().build()

  @Serializable
  private data class RdAddMagnetResponse(
    val id: String,
    val uri: String? = null,
  )

  @Serializable
  private data class RdTorrentInfo(
    val id: String,
    val status: String,
    val links: List<String> = emptyList(),
  )

  @Serializable
  private data class RdUnrestrictResponse(
    val id: String? = null,
    val download: String,
    val filename: String? = null,
  )

  override suspend fun resolveTorrent(
    infoHash: String,
    fileIdx: Int?,
    apiKey: String,
  ): Result<String> {
    if (apiKey.isBlank()) {
      return Result.failure(IllegalArgumentException("Real-Debrid API key is empty"))
    }

    val hash = infoHash.lowercase().trim()
    val magnetUri = "magnet:?xt=urn:btih:$hash"

    return try {
      // Step 1: Add Magnet to Real-Debrid
      val addBody = FormBody.Builder()
        .add("magnet", magnetUri)
        .build()

      val addReq = Request.Builder()
        .url("https://api.real-debrid.com/rest/1.0/torrents/addMagnet")
        .header("Authorization", "Bearer $apiKey")
        .post(addBody)
        .build()

      val addResp = client.newCall(addReq).awaitResponse()
      val torrentId = addResp.use { resp ->
        if (!resp.isSuccessful) {
          throw IOException("RD AddMagnet failed: HTTP ${resp.code}")
        }
        val body = resp.body.string()
        json.decodeFromString<RdAddMagnetResponse>(body).id
      }

      // Step 2: Select files (select all files or specific file)
      val selectFilesParam = if (fileIdx != null) "${fileIdx + 1}" else "all"
      val selectBody = FormBody.Builder()
        .add("files", selectFilesParam)
        .build()

      val selectReq = Request.Builder()
        .url("https://api.real-debrid.com/rest/1.0/torrents/selectFiles/$torrentId")
        .header("Authorization", "Bearer $apiKey")
        .post(selectBody)
        .build()

      val selectResp = client.newCall(selectReq).awaitResponse()
      selectResp.use { resp ->
        if (!resp.isSuccessful && resp.code != 204) {
          throw IOException("RD SelectFiles failed: HTTP ${resp.code}")
        }
      }

      // Step 3: Get torrent info to obtain unrestricted link
      val infoReq = Request.Builder()
        .url("https://api.real-debrid.com/rest/1.0/torrents/info/$torrentId")
        .header("Authorization", "Bearer $apiKey")
        .get()
        .build()

      val infoResp = client.newCall(infoReq).awaitResponse()
      val unrestrictedLink = infoResp.use { resp ->
        if (!resp.isSuccessful) {
          throw IOException("RD TorrentInfo failed: HTTP ${resp.code}")
        }
        val body = resp.body.string()
        val info = json.decodeFromString<RdTorrentInfo>(body)
        if (info.links.isEmpty()) {
          throw IOException("RD Torrent not instant or no links available")
        }
        info.links.first()
      }

      // Step 4: Unrestrict link to obtain high speed direct HTTPS download URL
      val unrestrictBody = FormBody.Builder()
        .add("link", unrestrictedLink)
        .build()

      val unrestrictReq = Request.Builder()
        .url("https://api.real-debrid.com/rest/1.0/unrestrict/link")
        .header("Authorization", "Bearer $apiKey")
        .post(unrestrictBody)
        .build()

      val unrestrictResp = client.newCall(unrestrictReq).awaitResponse()
      val directUrl = unrestrictResp.use { resp ->
        if (!resp.isSuccessful) {
          throw IOException("RD Unrestrict failed: HTTP ${resp.code}")
        }
        val body = resp.body.string()
        json.decodeFromString<RdUnrestrictResponse>(body).download
      }

      Result.success(directUrl)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
