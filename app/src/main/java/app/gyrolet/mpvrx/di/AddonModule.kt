/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.di

import app.gyrolet.mpvrx.data.addon.AddonClient
import app.gyrolet.mpvrx.data.addon.AddonManager
import app.gyrolet.mpvrx.data.addon.AddonRepository
import app.gyrolet.mpvrx.data.addon.StremioCatalogCache
import app.gyrolet.mpvrx.data.addon.StremioPlaybackTracker
import app.gyrolet.mpvrx.data.addon.TmdbSearchClient
import app.gyrolet.mpvrx.data.debrid.AllDebridResolver
import app.gyrolet.mpvrx.data.debrid.DebridService
import app.gyrolet.mpvrx.data.debrid.RealDebridResolver
import app.gyrolet.mpvrx.data.debrid.TorBoxResolver
import app.gyrolet.mpvrx.database.MpvRxDatabase
import app.gyrolet.mpvrx.domain.stream.StreamEngine
import app.gyrolet.mpvrx.preferences.DebridPreferences
import app.gyrolet.mpvrx.ui.addon.detail.StremioDetailViewModel
import app.gyrolet.mpvrx.ui.addon.discover.AddonDiscoverViewModel
import app.gyrolet.mpvrx.ui.addon.management.AddonManagementViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val addonModule = module {
  // Preferences
  singleOf(::DebridPreferences)

  // DAOs
  single { get<MpvRxDatabase>().stremioAddonDao() }
  single { get<MpvRxDatabase>().stremioPlaybackHistoryDao() }

  // Cache, Repositories & Services
  single { StremioCatalogCache(context = get(), json = get()) }
  single { TmdbSearchClient(debridPreferences = get(), okHttpClient = get(), json = get()) }
  single { AddonClient(okHttpClient = get(), json = get()) }
  single { AddonRepository(addonDao = get()) }
  single { AddonManager(addonClient = get(), addonRepository = get(), catalogCache = get(), json = get()) }
  single { StremioPlaybackTracker(historyDao = get()) }

  // Debrid & Stream Engine
  single { RealDebridResolver(get(), get()) }
  single { TorBoxResolver(get(), get()) }
  single { AllDebridResolver(get(), get()) }
  single { DebridService(get(), get(), get(), get()) }
  single { StreamEngine(get()) }

  // ViewModels
  viewModel { AddonManagementViewModel(get()) }
  viewModel { AddonDiscoverViewModel(get(), get(), get()) }
  viewModel { (metaId: String, type: String) ->
    StremioDetailViewModel(metaId, type, get(), get(), get(), get())
  }
}
