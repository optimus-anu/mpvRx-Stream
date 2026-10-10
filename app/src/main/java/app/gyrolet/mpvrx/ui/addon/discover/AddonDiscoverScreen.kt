/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.addon.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.database.entities.StremioPlaybackHistoryEntity
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.presentation.components.RemoteImage
import app.gyrolet.mpvrx.ui.addon.components.StremioCard
import app.gyrolet.mpvrx.ui.addon.detail.StremioDetailScreen
import app.gyrolet.mpvrx.ui.addon.management.AddonManagementScreen
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.navigateTo
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object AddonDiscoverScreen : Screen {

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val viewModel: AddonDiscoverViewModel = koinViewModel()
    val backStack = LocalBackStack.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchMovieResults by viewModel.searchMovieResults.collectAsState()
    val searchSeriesResults by viewModel.searchSeriesResults.collectAsState()
    val searchOtherResults by viewModel.searchOtherResults.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val catalogRows by viewModel.catalogRows.collectAsState()
    val isLoadingCatalogs by viewModel.isLoadingCatalogs.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("Discover & Addons") },
          actions = {
            IconButton(onClick = { backStack.navigateTo(AddonManagementScreen) }) {
              Icon(
                Icons.RoundedFilled.Extension,
                contentDescription = "Manage Addons",
              )
            }
          },
        )
      },
    ) { padding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
      ) {
        // Search bar
        OutlinedTextField(
          value = searchQuery,
          onValueChange = viewModel::onSearchQueryChanged,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          placeholder = { Text("Type query and press Search...") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(
            onSearch = {
              keyboardController?.hide()
              viewModel.performSearch()
            },
          ),
          leadingIcon = {
            IconButton(
              onClick = {
                keyboardController?.hide()
                viewModel.performSearch()
              },
            ) {
              Icon(Icons.RoundedFilled.Search, contentDescription = "Search")
            }
          },
          trailingIcon = {
            if (searchQuery.isNotBlank()) {
              Row {
                IconButton(
                  onClick = {
                    keyboardController?.hide()
                    viewModel.performSearch()
                  },
                ) {
                  Icon(Icons.RoundedFilled.ArrowForward, contentDescription = "Perform Search")
                }
                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                  Icon(Icons.RoundedFilled.Close, contentDescription = "Clear")
                }
              }
            }
          },
        )

        if (searchQuery.isNotBlank()) {
          // Categorized Search Results View
          if (isSearching) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Searching TMDB & catalogs...")
              }
            }
          } else if (searchResults.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "No results found for '$searchQuery'. Press Search on keyboard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            LazyColumn(
              contentPadding = PaddingValues(bottom = 24.dp),
              verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
              // Movies Search Row
              if (searchMovieResults.isNotEmpty()) {
                item {
                  Column {
                    Text(
                      text = "Popular - Movie",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    LazyRow(
                      contentPadding = PaddingValues(horizontal = 16.dp),
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      items(searchMovieResults, key = { it.id }) { item ->
                        StremioCard(
                          meta = item,
                          onClick = {
                            backStack.navigateTo(StremioDetailScreen(metaId = item.id, type = item.type))
                          },
                        )
                      }
                    }
                  }
                }
              }

              // Series Search Row
              if (searchSeriesResults.isNotEmpty()) {
                item {
                  Column {
                    Text(
                      text = "Popular - Series",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    LazyRow(
                      contentPadding = PaddingValues(horizontal = 16.dp),
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      items(searchSeriesResults, key = { it.id }) { item ->
                        StremioCard(
                          meta = item,
                          onClick = {
                            backStack.navigateTo(StremioDetailScreen(metaId = item.id, type = item.type))
                          },
                        )
                      }
                    }
                  }
                }
              }

              // Other Results Row
              if (searchOtherResults.isNotEmpty()) {
                item {
                  Column {
                    Text(
                      text = "Other Results",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    LazyRow(
                      contentPadding = PaddingValues(horizontal = 16.dp),
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      items(searchOtherResults, key = { it.id }) { item ->
                        StremioCard(
                          meta = item,
                          onClick = {
                            backStack.navigateTo(StremioDetailScreen(metaId = item.id, type = item.type))
                          },
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        } else {
          // Main Catalogs View
          if (isLoadingCatalogs && continueWatching.isEmpty() && catalogRows.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                  text = "Loading catalog rows...",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          } else if (continueWatching.isEmpty() && catalogRows.isEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  text = "No catalog rows found.",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "Install Cinemeta, AIOMetadata, TMDB, or other catalog addons to display movies & shows.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                  onClick = { viewModel.loadCatalogs() },
                ) {
                  Text("Retry / Load Catalogs")
                }
              }
            }
          } else {
            LazyColumn(
              contentPadding = PaddingValues(bottom = 24.dp),
              verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
              // Continue Watching Section
              if (continueWatching.isNotEmpty()) {
                item {
                  Column {
                    Text(
                      text = "Continue Watching",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    LazyRow(
                      contentPadding = PaddingValues(horizontal = 16.dp),
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      items(continueWatching, key = { it.mediaId }) { historyItem ->
                        ContinueWatchingCard(
                          item = historyItem,
                          onClick = {
                            val targetId = historyItem.seriesId ?: historyItem.mediaId
                            backStack.navigateTo(
                              StremioDetailScreen(metaId = targetId, type = historyItem.type),
                            )
                          },
                        )
                      }
                    }
                  }
                }
              }

              // Catalog Rows
              items(catalogRows, key = { "${it.type}_${it.catalogId}" }) { row ->
                Column {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = row.catalogTitle,
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                    )
                  }

                  LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    items(row.items, key = { it.id }) { item ->
                      StremioCard(
                        meta = item,
                        onClick = {
                          backStack.navigateTo(StremioDetailScreen(metaId = item.id, type = item.type))
                        },
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ContinueWatchingCard(
  item: StremioPlaybackHistoryEntity,
  onClick: () -> Unit,
) {
  Card(
    modifier = Modifier
      .width(160.dp)
      .height(145.dp)
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ),
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(90.dp)
          .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
      ) {
        val imageUrl = item.bannerUrl ?: item.posterUrl
        if (!imageUrl.isNullOrBlank()) {
          RemoteImage(
            url = imageUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }

        Icon(
          Icons.RoundedFilled.PlayArrow,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier
            .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(20.dp))
            .padding(6.dp),
        )

        // Progress Bar
        if (item.progressRatio > 0f) {
          LinearProgressIndicator(
            progress = { item.progressRatio },
            modifier = Modifier
              .fillMaxWidth()
              .height(4.dp)
              .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Black.copy(alpha = 0.5f),
          )
        }
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(8.dp),
      ) {
        Text(
          text = item.title,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        val subTitleText = item.episodeTitle ?: if (item.type == "series" && item.season != null && item.episode != null) {
          "S${item.season}:E${item.episode}"
        } else ""

        Text(
          text = subTitleText,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
