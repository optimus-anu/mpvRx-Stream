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

class TorBoxResolver(
  okHttpClient: OkHttpClient = SharedHttpClient.base,
  private val json: Json = Json { ignoreUnknownKeys = true },
) : DebridResolver {

  private val client = okHttpClient.newBuilder().build()

  @Serializable
  private data class TorBoxCreateData(
    val torrent_id: Long? = null,
    val torrent_hash: String? = null,
  )

  @Serializable
  private data class TorBoxCreateResponse(
    val success: Boolean = false,
    val data: TorBoxCreateData? = null,
  )

  @Serializable
  private data class TorBoxDlResponse(
    val success: Boolean = false,
    val data: String? = null,
  )

  override suspend fun resolveTorrent(
    infoHash: String,
    fileIdx: Int?,
    apiKey: String,
  ): Result<String> {
    if (apiKey.isBlank()) {
      return Result.failure(IllegalArgumentException("TorBox API key is empty"))
    }

    val hash = infoHash.lowercase().trim()
    val magnetUri = "magnet:?xt=urn:btih:$hash"

    return try {
      val createBody = FormBody.Builder()
        .add("magnet", magnetUri)
        .build()

      val createReq = Request.Builder()
        .url("https://api.torbox.app/v1/api/torrents/createtorrent")
        .header("Authorization", "Bearer $apiKey")
        .post(createBody)
        .build()

      val createResp = client.newCall(createReq).awaitResponse()
      val torrentId = createResp.use { resp ->
        if (!resp.isSuccessful) {
          throw IOException("TorBox CreateTorrent failed: HTTP ${resp.code}")
        }
        val body = resp.body.string()
        val parsed = json.decodeFromString<TorBoxCreateResponse>(body)
        parsed.data?.torrent_id ?: throw IOException("TorBox no torrent_id in response")
      }

      val fileParam = if (fileIdx != null) "&file_id=$fileIdx" else ""
      val dlUrl = "https://api.torbox.app/v1/api/torrents/requestdl?token=$apiKey&torrent_id=$torrentId$fileParam"

      val dlReq = Request.Builder()
        .url(dlUrl)
        .get()
        .build()

      val dlResp = client.newCall(dlReq).awaitResponse()
      val directUrl = dlResp.use { resp ->
        if (!resp.isSuccessful) {
          throw IOException("TorBox RequestDl failed: HTTP ${resp.code}")
        }
        val body = resp.body.string()
        val parsed = json.decodeFromString<TorBoxDlResponse>(body)
        parsed.data ?: throw IOException("TorBox no download URL in response")
      }

      Result.success(directUrl)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
