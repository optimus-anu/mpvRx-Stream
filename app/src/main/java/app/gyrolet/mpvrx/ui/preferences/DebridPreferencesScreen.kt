/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.preferences.DebridPreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object DebridPreferencesScreen : Screen {

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backStack = LocalBackStack.current
    val debridPreferences: DebridPreferences = koinInject()

    val realDebridKey by debridPreferences.realDebridApiKey.collectAsState()
    val torBoxKey by debridPreferences.torBoxApiKey.collectAsState()
    val allDebridKey by debridPreferences.allDebridApiKey.collectAsState()
    val tmdbKey by debridPreferences.tmdbApiKey.collectAsState()

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("Debrid & TMDB Settings") },
          navigationIcon = {
            IconButton(onClick = { backStack.popSafely() }) {
              Icon(Icons.RoundedFilled.ArrowBack, contentDescription = "Back")
            }
          },
        )
      },
    ) { padding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(16.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        // Section: TMDB API Key
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("TMDB API Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Custom API key for direct TMDB search engine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
              value = tmdbKey,
              onValueChange = { debridPreferences.tmdbApiKey.set(it) },
              modifier = Modifier.fillMaxWidth(),
              placeholder = { Text("5b589fccce5bc58d331feb8fc1ccdfb2") },
              singleLine = true,
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Real-Debrid API Key
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("Real-Debrid API Token", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("High-speed HTTPS torrent resolver token from real-debrid.com/apitoken", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
              value = realDebridKey,
              onValueChange = { debridPreferences.realDebridApiKey.set(it) },
              modifier = Modifier.fillMaxWidth(),
              placeholder = { Text("Paste Real-Debrid API Token") },
              singleLine = true,
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: TorBox API Key
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("TorBox API Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("TorBox debrid service API key from torbox.app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
              value = torBoxKey,
              onValueChange = { debridPreferences.torBoxApiKey.set(it) },
              modifier = Modifier.fillMaxWidth(),
              placeholder = { Text("Paste TorBox API Key") },
              singleLine = true,
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: AllDebrid API Key
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("AllDebrid API Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("AllDebrid service API key from alldebrid.com/apikeys", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
              value = allDebridKey,
              onValueChange = { debridPreferences.allDebridApiKey.set(it) },
              modifier = Modifier.fillMaxWidth(),
              placeholder = { Text("Paste AllDebrid API Key") },
              singleLine = true,
            )
          }
        }
      }
    }
  }
}
