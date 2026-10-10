/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.data.torrent

import android.util.Log
import app.gyrolet.mpvrx.data.network.proxy.HttpByteRange
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream

class TorrentServerProxy private constructor() : NanoHTTPD("127.0.0.1", 0) {

  companion object {
    private const val TAG = "TorrentServerProxy"

    @Volatile
    private var instance: TorrentServerProxy? = null

    fun getInstance(): TorrentServerProxy =
      instance ?: synchronized(this) {
        instance ?: TorrentServerProxy().also {
          it.start()
          Log.d(TAG, "TorrentServerProxy started on http://127.0.0.1:${it.listeningPort}")
          instance = it
        }
      }

    fun stopInstance() {
      synchronized(this) {
        instance?.let { proxy ->
          proxy.stop()
          instance = null
        }
      }
    }
  }

  fun getStreamUrl(infoHash: String): String {
    val cleanHash = infoHash.lowercase().trim()
    return "http://127.0.0.1:$listeningPort/stream?infoHash=$cleanHash"
  }

  override fun serve(session: IHTTPSession): Response {
    val params = session.parameters
    val infoHash = params["infoHash"]?.firstOrNull()
      ?: session.uri.substringAfterLast('/').takeIf { it.isNotBlank() && it.length == 40 }

    if (infoHash.isNullOrBlank()) {
      return newFixedLengthResponse(
        Response.Status.BAD_REQUEST,
        MIME_PLAINTEXT,
        "Missing infoHash parameter",
      )
    }

    val streamSession = TorrentEngine.getInstance().getSession(infoHash)
      ?: return newFixedLengthResponse(
        Response.Status.NOT_FOUND,
        MIME_PLAINTEXT,
        "No active torrent session found for infoHash: $infoHash",
      )

    val totalLength = streamSession.fileSize
    val mimeType = getMimeType(streamSession.fileOnDisk.name)

    val headers = session.headers
    val rangeHeader = headers["range"] ?: headers["Range"]

    return if (!rangeHeader.isNullOrBlank()) {
      val byteRange = HttpByteRange.parse(rangeHeader, totalLength)
        ?: return newFixedLengthResponse(
          Response.Status.RANGE_NOT_SATISFIABLE,
          MIME_PLAINTEXT,
          "Range Not Satisfiable",
        )

      val inputStream: InputStream = streamSession.openInputStream(byteRange.start, byteRange.length)

      val response = newFixedLengthResponse(
        Response.Status.PARTIAL_CONTENT,
        mimeType,
        inputStream,
        byteRange.length,
      )
      response.addHeader("Content-Range", "bytes ${byteRange.start}-${byteRange.endInclusive}/$totalLength")
      response.addHeader("Accept-Ranges", "bytes")
      response.addHeader("Connection", "keep-alive")
      response
    } else {
      val inputStream: InputStream = streamSession.openInputStream(0L, totalLength)

      val response = newFixedLengthResponse(
        Response.Status.OK,
        mimeType,
        inputStream,
        totalLength,
      )
      response.addHeader("Accept-Ranges", "bytes")
      response.addHeader("Connection", "keep-alive")
      response
    }
  }

  private fun getMimeType(fileName: String): String {
    val lower = fileName.lowercase()
    return when {
      lower.endsWith(".mp4") -> "video/mp4"
      lower.endsWith(".mkv") -> "video/x-matroska"
      lower.endsWith(".webm") -> "video/webm"
      lower.endsWith(".avi") -> "video/x-msvideo"
      lower.endsWith(".mov") -> "video/quicktime"
      lower.endsWith(".ts") -> "video/mp2t"
      else -> "video/mp4"
    }
  }
}
