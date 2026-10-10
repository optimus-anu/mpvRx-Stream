/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.addon.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gyrolet.mpvrx.database.entities.StremioAddonEntity
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.presentation.components.RemoteImage
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

data class QuickAddonPreset(
  val name: String,
  val url: String,
  val category: String,
)

@Serializable
object AddonManagementScreen : Screen {

  private val popularPresets = listOf(
    QuickAddonPreset("AIOMetadata", "https://aiometadata.org/manifest.json", "Catalog"),
    QuickAddonPreset("CyberFlix", "https://cyberflix.strem.fun/manifest.json", "Catalog"),
    QuickAddonPreset("TMDB Addon", "https://tmdb-addon.strem.fun/manifest.json", "Catalog"),
    QuickAddonPreset("Anime Kitsu", "https://v3-kitsu.strem.io/manifest.json", "Anime"),
    QuickAddonPreset("Torrentio", "https://torrentio.strem.fun/manifest.json", "Streams"),
    QuickAddonPreset("Cinemeta", "https://v3-cinemeta.strem.io/manifest.json", "Catalog"),
  )

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val viewModel: AddonManagementViewModel = koinViewModel()
    val backStack = LocalBackStack.current
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }

    val installedAddons by viewModel.installedAddons.collectAsState()
    val inputUrl by viewModel.inputUrl.collectAsState()
    val isInstalling by viewModel.isInstalling.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(errorMessage) {
      errorMessage?.let { error ->
        snackbarHostState.showSnackbar(error)
        viewModel.clearError()
      }
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("Stremio Addons") },
          navigationIcon = {
            IconButton(onClick = { backStack.popSafely() }) {
              Icon(
                Icons.RoundedFilled.ArrowBack,
                contentDescription = "Back",
              )
            }
          },
        )
      },
      snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Section 1: Add New Addon
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
            ) {
              Text(
                text = "Install New Addon",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Enter or paste a Stremio manifest URL (e.g., https://.../manifest.json or stremio://...)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.height(12.dp))

              val scope = rememberCoroutineScope()
              OutlinedTextField(
                value = inputUrl,
                onValueChange = viewModel::onInputUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://addon.com/manifest.json") },
                singleLine = true,
                trailingIcon = {
                  IconButton(
                    onClick = {
                      scope.launch {
                        val clip = clipboard.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()
                        if (!clip.isNullOrBlank()) {
                          viewModel.onInputUrlChanged(clip)
                        }
                      }
                    },
                  ) {
                    Icon(Icons.RoundedFilled.ContentPaste, contentDescription = "Paste")
                  }
                },
              )

              Spacer(modifier = Modifier.height(12.dp))

              Button(
                onClick = viewModel::installAddon,
                modifier = Modifier.fillMaxWidth(),
                enabled = inputUrl.isNotBlank() && !isInstalling,
              ) {
                if (isInstalling) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Installing...")
                } else {
                  Icon(Icons.RoundedFilled.Add, contentDescription = null)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Install Addon")
                }
              }
            }
          }
        }

        // Quick Install Presets
        item {
          Column {
            Text(
              text = "Popular Addons Quick-Install",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(popularPresets) { preset ->
                FilterChip(
                  selected = false,
                  onClick = {
                    viewModel.onInputUrlChanged(preset.url)
                    viewModel.installAddon()
                  },
                  label = { Text("+ ${preset.name}") },
                )
              }
            }
          }
        }

        // Section 2: Installed Addons Header
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Installed Addons (${installedAddons.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        // Installed Addons List
        if (installedAddons.isEmpty()) {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "No addons installed yet. Tap a popular addon above or paste a manifest URL.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          items(installedAddons, key = { it.manifestUrl }) { addon ->
            AddonItemCard(
              addon = addon,
              onToggleEnabled = { enabled ->
                viewModel.toggleAddonEnabled(addon.manifestUrl, enabled)
              },
              onUninstall = {
                viewModel.uninstallAddon(addon.manifestUrl)
              },
            )
          }
        }

        item {
          Spacer(modifier = Modifier.height(24.dp))
        }
      }
    }
  }
}

@Composable
private fun AddonItemCard(
  addon: StremioAddonEntity,
  onToggleEnabled: (Boolean) -> Unit,
  onUninstall: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        if (!addon.icon.isNullOrBlank()) {
          RemoteImage(
            url = addon.icon,
            contentDescription = addon.name,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Icon(
            Icons.RoundedFilled.Extension,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }

      Spacer(modifier = Modifier.width(16.dp))

      Column(
        modifier = Modifier.weight(1f),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = addon.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
          ) {
            Text(
              text = "v${addon.version}",
              fontSize = 10.sp,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
          }
        }

        if (!addon.description.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = addon.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }

        if (addon.types.isNotBlank()) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Types: ${addon.types}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(
        horizontalAlignment = Alignment.End,
      ) {
        Switch(
          checked = addon.isEnabled,
          onCheckedChange = onToggleEnabled,
        )

        IconButton(onClick = onUninstall) {
          Icon(
            Icons.RoundedFilled.Delete,
            contentDescription = "Uninstall Addon",
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}
