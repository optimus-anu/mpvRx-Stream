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
import kotlinx.coroutines.delay
import org.libtorrent4j.Priority
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import kotlin.math.min

class TorrentStreamSession(
  val infoHash: String,
  val torrentHandle: TorrentHandle,
  val torrentInfo: TorrentInfo,
  val selectedFileIndex: Int,
  val fileSize: Long,
  val fileOffsetInTorrent: Long,
  val pieceLength: Long,
  val fileOnDisk: File,
) {
  companion object {
    private const val TAG = "TorrentStreamSession"
    private const val HEADER_PREBUFFER_BYTES = 2 * 1024 * 1024L
    private const val FOOTER_PREBUFFER_BYTES = 1 * 1024 * 1024L
  }

  init {
    try {
      torrentHandle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
    } catch (e: Exception) {
      Log.e(TAG, "Error setting sequential download flag", e)
    }
  }

  fun prioritizeHeaderPieces() {
    try {
      prioritizePieceRange(0L, min(HEADER_PREBUFFER_BYTES, fileSize), deadlineMs = 500)
      if (fileSize > FOOTER_PREBUFFER_BYTES) {
        val footerStart = fileSize - FOOTER_PREBUFFER_BYTES
        prioritizePieceRange(footerStart, FOOTER_PREBUFFER_BYTES, deadlineMs = 1000)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error prioritizing header/footer pieces", e)
    }
  }

  fun prioritizePieceRange(startOffset: Long, length: Long, deadlineMs: Int = 1000) {
    if (pieceLength <= 0) return
    val absStart = fileOffsetInTorrent + startOffset.coerceAtLeast(0L)
    val absEnd = (fileOffsetInTorrent + startOffset + length - 1L).coerceAtMost(fileOffsetInTorrent + fileSize - 1L)
    if (absEnd < absStart) return

    val firstPiece = (absStart / pieceLength).toInt()
    val lastPiece = (absEnd / pieceLength).toInt()

    for (piece in firstPiece..lastPiece) {
      try {
        torrentHandle.piecePriority(piece, Priority.fromSwig(7))
        torrentHandle.setPieceDeadline(piece, deadlineMs)
      } catch (_: Exception) {}
    }
  }

  suspend fun waitForBytes(offset: Long, length: Long, timeoutMs: Long = 15_000L): Boolean {
    if (offset >= fileSize) return true
    prioritizePieceRange(offset, length, deadlineMs = 500)

    val startTime = System.currentTimeMillis()
    while (System.currentTimeMillis() - startTime < timeoutMs) {
      if (fileOnDisk.exists() && fileOnDisk.length() >= offset + min(length, 64 * 1024L)) {
        return true
      }
      delay(100)
    }
    return fileOnDisk.exists() && fileOnDisk.length() > offset
  }

  fun openInputStream(startOffset: Long, length: Long): InputStream {
    prioritizePieceRange(startOffset, length, deadlineMs = 500)
    val raf = RandomAccessFile(fileOnDisk, "r")
    if (startOffset > 0 && startOffset < fileSize) {
      raf.seek(startOffset)
    }
    return TorrentInputStream(raf, startOffset, length, this)
  }

  fun close() {
    try {
      if (torrentHandle.isValid) {
        torrentHandle.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error closing stream session", e)
    }
  }

  private class TorrentInputStream(
    private val raf: RandomAccessFile,
    private val startOffset: Long,
    private val maxBytesToRead: Long,
    private val session: TorrentStreamSession,
  ) : InputStream() {
    private var bytesReadSoFar = 0L

    override fun read(): Int {
      if (maxBytesToRead > 0 && bytesReadSoFar >= maxBytesToRead) return -1
      val b = raf.read()
      if (b != -1) bytesReadSoFar++
      return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
      if (maxBytesToRead > 0 && bytesReadSoFar >= maxBytesToRead) return -1
      val toRead = if (maxBytesToRead > 0) {
        min(len.toLong(), maxBytesToRead - bytesReadSoFar).toInt()
      } else {
        len
      }

      val currentPos = startOffset + bytesReadSoFar
      session.prioritizePieceRange(currentPos, toRead.toLong(), deadlineMs = 1000)

      val readCount = raf.read(b, off, toRead)
      if (readCount > 0) {
        bytesReadSoFar += readCount
      }
      return readCount
    }

    override fun close() {
      try {
        raf.close()
      } catch (_: Exception) {}
    }
  }
}
