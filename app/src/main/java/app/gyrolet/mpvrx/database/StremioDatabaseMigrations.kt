/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_31_32 =
  object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `stremio_addons` (
          `manifestUrl` TEXT NOT NULL,
          `id` TEXT NOT NULL,
          `name` TEXT NOT NULL,
          `version` TEXT NOT NULL,
          `description` TEXT,
          `icon` TEXT,
          `baseUrl` TEXT NOT NULL,
          `types` TEXT NOT NULL,
          `manifestJson` TEXT NOT NULL,
          `isEnabled` INTEGER NOT NULL DEFAULT 1,
          `installedAt` INTEGER NOT NULL,
          PRIMARY KEY(`manifestUrl`)
        )
        """.trimIndent(),
      )
    }
  }

val MIGRATION_32_33 =
  object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `stremio_playback_history` (
          `mediaId` TEXT NOT NULL,
          `type` TEXT NOT NULL,
          `seriesId` TEXT,
          `season` INTEGER,
          `episode` INTEGER,
          `title` TEXT NOT NULL,
          `episodeTitle` TEXT,
          `posterUrl` TEXT,
          `bannerUrl` TEXT,
          `lastPositionMs` INTEGER NOT NULL,
          `durationMs` INTEGER NOT NULL,
          `lastWatchedTimestamp` INTEGER NOT NULL,
          `isFinished` INTEGER NOT NULL DEFAULT 0,
          PRIMARY KEY(`mediaId`)
        )
        """.trimIndent(),
      )
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_stremio_playback_history_lastWatchedTimestamp` ON `stremio_playback_history` (`lastWatchedTimestamp`)")
      db.execSQL("CREATE INDEX IF NOT EXISTS `index_stremio_playback_history_seriesId` ON `stremio_playback_history` (`seriesId`)")
    }
  }
