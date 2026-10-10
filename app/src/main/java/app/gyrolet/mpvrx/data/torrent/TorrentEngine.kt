/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.data.torrent

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class TorrentEngine private constructor() {

  companion object {
    private const val TAG = "TorrentEngine"
    private const val MAX_CACHE_SIZE_BYTES = 2_000_000_000L // 2 GB max torrent cache

    @Volatile
    private var instance: TorrentEngine? = null

    fun getInstance(): TorrentEngine =
      instance ?: synchronized(this) {
        instance ?: TorrentEngine().also {
          it.startSessionManager()
          instance = it
        }
      }
  }

  private val sessionManager = SessionManager()
  private val activeSessions = ConcurrentHashMap<String, TorrentStreamSession>()

  private fun startSessionManager() {
    try {
      sessionManager.start()
      val settings = sessionManager.settings()
      settings.setInteger(settings_pack.int_types.active_downloads.swigValue(), 10)
      settings.setInteger(settings_pack.int_types.active_seeds.swigValue(), 5)
      settings.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
      settings.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
      settings.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
      settings.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)
      sessionManager.applySettings(settings)
      Log.d(TAG, "TorrentEngine libtorrent session started successfully")
    } catch (e: Exception) {
      Log.e(TAG, "Error starting TorrentEngine libtorrent session", e)
    }
  }

  /**
   * Prepares and starts a sequential streaming session for a magnet URI or infoHash.
   */
  suspend fun prepareStreamSession(
    context: Context,
    infoHashOrMagnet: String,
    targetFileIdx: Int? = null,
  ): TorrentStreamSession = withContext(Dispatchers.IO) {
    val cleanHashOrMagnet = infoHashOrMagnet.trim()
    val magnetUri = if (cleanHashOrMagnet.startsWith("magnet:", ignoreCase = true)) {
      cleanHashOrMagnet
    } else {
      "magnet:?xt=urn:btih:$cleanHashOrMagnet"
    }

    val cacheDir = File(context.cacheDir, "torrent_stream_cache")
    if (!cacheDir.exists()) {
      cacheDir.mkdirs()
    } else {
      pruneCacheDir(cacheDir)
    }

    Log.d(TAG, "Fetching torrent metadata for $magnetUri")
    val torrentBytes = sessionManager.fetchMagnet(magnetUri, 20, cacheDir)
      ?: throw IllegalStateException("Failed to retrieve metadata for torrent within timeout")

    val torrentInfo = TorrentInfo.bdecode(torrentBytes)
    sessionManager.download(torrentInfo, cacheDir)

    val sha1 = torrentInfo.infoHash()
    val handle = sessionManager.find(sha1)
      ?: throw IllegalStateException("Torrent handle not found after download call")

    val fileStorage = torrentInfo.files()
    val numFiles = fileStorage.numFiles()

    var selectedIdx = targetFileIdx ?: 0
    if (targetFileIdx == null || targetFileIdx < 0 || targetFileIdx >= numFiles) {
      var maxSizeBytes = -1L
      for (i in 0 until numFiles) {
        val size = fileStorage.fileSize(i)
        val path = fileStorage.filePath(i).lowercase()
        if (size > maxSizeBytes && isVideoFile(path)) {
          maxSizeBytes = size
          selectedIdx = i
        }
      }
      if (maxSizeBytes == -1L) {
        for (i in 0 until numFiles) {
          val size = fileStorage.fileSize(i)
          if (size > maxSizeBytes) {
            maxSizeBytes = size
            selectedIdx = i
          }
        }
      }
    }

    val selectedFilePath = fileStorage.filePath(selectedIdx)
    val selectedFileSize = fileStorage.fileSize(selectedIdx)
    val selectedFileOffset = fileStorage.fileOffset(selectedIdx)
    val pieceLength = torrentInfo.pieceLength().toLong()

    val fileOnDisk = File(cacheDir, selectedFilePath)

    val session = TorrentStreamSession(
      infoHash = sha1.toHex(),
      torrentHandle = handle,
      torrentInfo = torrentInfo,
      selectedFileIndex = selectedIdx,
      fileSize = selectedFileSize,
      fileOffsetInTorrent = selectedFileOffset,
      pieceLength = pieceLength,
      fileOnDisk = fileOnDisk,
    )

    session.prioritizeHeaderPieces()

    activeSessions[session.infoHash.lowercase()] = session
    session
  }

  fun getSession(infoHash: String): TorrentStreamSession? {
    return activeSessions[infoHash.lowercase()]
  }

  fun stopStreamSession(infoHash: String) {
    val session = activeSessions.remove(infoHash.lowercase())
    session?.close()
  }

  fun stopAllSessions() {
    activeSessions.values.forEach { it.close() }
    activeSessions.clear()
    try {
      sessionManager.stop()
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping SessionManager", e)
    }
  }

  private fun pruneCacheDir(cacheDir: File) {
    try {
      val files = cacheDir.walkTopDown().filter { it.isFile }.toList()
      var totalSize = files.sumOf { it.length() }
      if (totalSize > MAX_CACHE_SIZE_BYTES) {
        val activeDiskFiles = activeSessions.values.map { it.fileOnDisk.absolutePath }.toSet()
        val sortedByAge = files.filter { it.absolutePath !in activeDiskFiles }
          .sortedBy { it.lastModified() }

        for (file in sortedByAge) {
          if (totalSize <= MAX_CACHE_SIZE_BYTES) break
          val length = file.length()
          if (file.delete()) {
            totalSize -= length
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error pruning torrent cache directory", e)
    }
  }

  private fun isVideoFile(filePath: String): Boolean {
    val lower = filePath.lowercase()
    return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") ||
      lower.endsWith(".mov") || lower.endsWith(".webm") || lower.endsWith(".m4v") ||
      lower.endsWith(".ts") || lower.endsWith(".flv")
  }
}
