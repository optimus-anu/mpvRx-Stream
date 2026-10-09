/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.domain.stream

import android.content.Context
import app.gyrolet.mpvrx.data.debrid.DebridService
import app.gyrolet.mpvrx.data.torrent.TorrentEngine
import app.gyrolet.mpvrx.data.torrent.TorrentServerProxy
import app.gyrolet.mpvrx.domain.addon.model.StremioStream
import app.gyrolet.mpvrx.domain.addon.model.StremioSubtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class StreamType {
  DIRECT_HTTP,
  DEBRID_HTTPS,
  TORRENT_LOCAL_HTTP,
  YOUTUBE,
  EXTERNAL,
}

data class StreamSessionHandle(
  val resolvedUrl: String,
  val streamType: StreamType,
  val subtitles: List<StremioSubtitle> = emptyList(),
  val proxyHeaders: Map<String, String>? = null,
  val infoHash: String? = null,
  private val onClose: () -> Unit,
) : AutoCloseable {
  override fun close() {
    onClose()
  }
}

class StreamEngine(
  private val debridService: DebridService,
  private val torrentEngine: TorrentEngine = TorrentEngine.getInstance(),
  private val torrentServerProxy: TorrentServerProxy = TorrentServerProxy.getInstance(),
) {

  /**
   * Resolves a [StremioStream] into a playable direct URL or local HTTP proxy URL.
   * Handles stream lifecycle, pre-buffering, Debrid fallback, and local torrent proxying.
   */
  suspend fun startStreamSession(
    context: Context,
    stream: StremioStream,
  ): Result<StreamSessionHandle> = withContext(Dispatchers.IO) {
    try {
      val headers = stream.behaviorHints?.proxyHeaders?.request

      // Case 1: Direct HTTP / HTTPS stream
      if (stream.isDirectHttpUrl) {
        val handle = StreamSessionHandle(
          resolvedUrl = stream.url!!,
          streamType = StreamType.DIRECT_HTTP,
          subtitles = stream.subtitles ?: emptyList(),
          proxyHeaders = headers,
          onClose = {},
        )
        return@withContext Result.success(handle)
      }

      // Case 2: Torrent / InfoHash stream
      if (stream.isTorrent) {
        val infoHash = stream.infoHash!!.trim()
        val fileIdx = stream.fileIdx

        // Step 2a: Try Debrid resolution if configured
        if (debridService.isConfigured()) {
          val debridResult = debridService.resolveTorrent(infoHash, fileIdx)
          if (debridResult.isSuccess) {
            val directDebridUrl = debridResult.getOrThrow()
            val handle = StreamSessionHandle(
              resolvedUrl = directDebridUrl,
              streamType = StreamType.DEBRID_HTTPS,
              subtitles = stream.subtitles ?: emptyList(),
              proxyHeaders = headers,
              infoHash = infoHash,
              onClose = {},
            )
            return@withContext Result.success(handle)
          }
        }

        // Step 2b: Fallback to embedded TorrentEngine & local NanoHTTPD proxy
        val torrentSession = torrentEngine.prepareStreamSession(context, infoHash, fileIdx)
        val localProxyUrl = torrentServerProxy.getStreamUrl(torrentSession.infoHash)

        val handle = StreamSessionHandle(
          resolvedUrl = localProxyUrl,
          streamType = StreamType.TORRENT_LOCAL_HTTP,
          subtitles = stream.subtitles ?: emptyList(),
          proxyHeaders = headers,
          infoHash = torrentSession.infoHash,
          onClose = {
            torrentEngine.stopStreamSession(torrentSession.infoHash)
          },
        )
        return@withContext Result.success(handle)
      }

      // Case 3: External URL
      if (stream.isExternal) {
        val handle = StreamSessionHandle(
          resolvedUrl = stream.externalUrl!!,
          streamType = StreamType.EXTERNAL,
          subtitles = stream.subtitles ?: emptyList(),
          proxyHeaders = headers,
          onClose = {},
        )
        return@withContext Result.success(handle)
      }

      // Case 4: YouTube stream
      if (stream.isYouTube) {
        val ytUrl = "https://www.youtube.com/watch?v=${stream.ytId}"
        val handle = StreamSessionHandle(
          resolvedUrl = ytUrl,
          streamType = StreamType.YOUTUBE,
          subtitles = stream.subtitles ?: emptyList(),
          proxyHeaders = headers,
          onClose = {},
        )
        return@withContext Result.success(handle)
      }

      Result.failure(IllegalArgumentException("Unsupported stream descriptor: $stream"))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
