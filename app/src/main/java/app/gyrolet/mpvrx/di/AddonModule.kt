/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.di

import app.gyrolet.mpvrx.ui.addon.detail.StremioDetailViewModel
import app.gyrolet.mpvrx.ui.addon.discover.AddonDiscoverViewModel
import app.gyrolet.mpvrx.ui.addon.management.AddonManagementViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val addonModule = module {
  viewModel { AddonManagementViewModel(get()) }
  viewModel { AddonDiscoverViewModel(get(), get()) }
  viewModel { (metaId: String, type: String) ->
    StremioDetailViewModel(metaId, type, get(), get(), get())
  }
}
