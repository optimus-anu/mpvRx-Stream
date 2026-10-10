/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.data.debrid

interface DebridResolver {
  /**
   * Attempts to resolve a torrent infoHash or magnet URI to a direct high-speed HTTPS stream URL.
   */
  suspend fun resolveTorrent(
    infoHash: String,
    fileIdx: Int? = null,
    apiKey: String,
  ): Result<String>
}
