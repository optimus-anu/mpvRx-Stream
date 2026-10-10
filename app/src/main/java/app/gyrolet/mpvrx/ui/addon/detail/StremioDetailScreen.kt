/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.addon.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gyrolet.mpvrx.domain.addon.model.StremioVideo
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.presentation.components.RemoteImage
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
data class StremioDetailScreen(
  val metaId: String,
  val type: String,
) : Screen {

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val viewModel: StremioDetailViewModel = koinViewModel(
      key = "${metaId}_${type}",
      parameters = { parametersOf(metaId, type) },
    )
    val backStack = LocalBackStack.current
    val context = LocalContext.current

    val meta by viewModel.metaState.collectAsState()
    val isLoadingMeta by viewModel.isLoadingMeta.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val streams by viewModel.streamsState.collectAsState()
    val isLoadingStreams by viewModel.isLoadingStreams.collectAsState()
    val showStreamSheet by viewModel.showStreamSheet.collectAsState()
    val currentStreamTitle by viewModel.currentStreamTitle.collectAsState()
    val seriesHistory by viewModel.seriesHistory.collectAsState()

    if (showStreamSheet) {
      StreamSelectionSheet(
        mediaTitle = currentStreamTitle,
        streams = streams,
        isLoading = isLoadingStreams,
        onStreamSelected = { stream ->
          viewModel.playStream(context, stream, currentStreamTitle)
        },
        onDismiss = viewModel::closeStreamSheet,
      )
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(meta?.name ?: "") },
          navigationIcon = {
            IconButton(onClick = { backStack.popSafely() }) {
              Icon(Icons.RoundedFilled.ArrowBack, contentDescription = "Back")
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
          ),
        )
      },
    ) { padding ->
      if (isLoadingMeta) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      } else if (meta == null) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentAlignment = Alignment.Center,
        ) {
          Text("Failed to load metadata.")
        }
      } else {
        val currentMeta = meta!!
        val actualType = if (currentMeta.type.isNotBlank()) currentMeta.type else type

        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        ) {
          // Banner & Poster Header
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
              val bannerUrl = currentMeta.background ?: currentMeta.banner ?: currentMeta.poster
              if (!bannerUrl.isNullOrBlank()) {
                RemoteImage(
                  url = bannerUrl,
                  contentDescription = currentMeta.name,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize(),
                )
              }

              // Gradient Overlay
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(
                    Brush.verticalGradient(
                      colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        MaterialTheme.colorScheme.surface,
                      ),
                    ),
                  ),
              )

              Row(
                modifier = Modifier
                  .align(Alignment.BottomStart)
                  .padding(16.dp),
                verticalAlignment = Alignment.Bottom,
              ) {
                val posterUrl = currentMeta.poster ?: currentMeta.logo
                if (!posterUrl.isNullOrBlank()) {
                  Card(
                    modifier = Modifier
                      .width(100.dp)
                      .aspectRatio(2f / 3f),
                    shape = RoundedCornerShape(8.dp),
                  ) {
                    RemoteImage(
                      url = posterUrl,
                      contentDescription = currentMeta.name,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize(),
                    )
                  }
                  Spacer(modifier = Modifier.width(16.dp))
                }

                Column {
                  Text(
                    text = currentMeta.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )

                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    val subInfo = listOfNotNull(currentMeta.releaseInfo ?: currentMeta.year).firstOrNull()
                    if (!subInfo.isNullOrBlank()) {
                      Text(
                        text = subInfo,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }

                    if (!currentMeta.imdbRating.isNullOrBlank()) {
                      Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                      ) {
                        Text(
                          text = "⭐ ${currentMeta.imdbRating}",
                          fontSize = 11.sp,
                          color = Color(0xFFFFD700),
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          // Genres
          val genresList = currentMeta.genres
          if (!genresList.isNullOrEmpty()) {
            item {
              LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                items(genresList) { genre ->
                  Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                  ) {
                    Text(
                      text = genre,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                  }
                }
              }
            }
          }

          // Movie Play Button (only if not series)
          if (actualType == "movie" && currentMeta.videos.isNullOrEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 12.dp),
              ) {
                Button(
                  onClick = {
                    viewModel.openStreamsForMedia(currentMeta.name, currentMeta.id)
                  },
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(12.dp),
                ) {
                  Icon(Icons.RoundedFilled.PlayArrow, contentDescription = null)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Play Movie", fontWeight = FontWeight.Bold)
                }
              }
            }
          }

          // Synopsis
          if (!currentMeta.description.isNullOrBlank()) {
            item {
              Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                  text = "Synopsis",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = currentMeta.description,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          // Cast & Director
          val castList = currentMeta.cast
          if (!castList.isNullOrEmpty()) {
            item {
              Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                  text = "Cast: ${castList.joinToString(", ")}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          // Series Season & Episode Picker
          val videosList = currentMeta.videos
          if ((actualType == "series" || !videosList.isNullOrEmpty()) && !videosList.isNullOrEmpty()) {
            val seasons = videosList
              .mapNotNull { it.season }
              .distinct()
              .sorted()

            item {
              Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                  text = "Episodes",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 16.dp),
                )

                if (seasons.isNotEmpty()) {
                  Spacer(modifier = Modifier.height(8.dp))
                  LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    items(seasons) { seasonNum ->
                      FilterChip(
                        selected = selectedSeason == seasonNum,
                        onClick = { viewModel.selectSeason(seasonNum) },
                        label = { Text("Season $seasonNum") },
                      )
                    }
                  }
                }
              }
            }

            val seasonVideos = videosList
              .filter { (it.season ?: 1) == selectedSeason }
              .sortedBy { it.episode ?: 0 }

            items(seasonVideos, key = { it.id }) { video ->
              val epHistory = seriesHistory.find { history ->
                history.mediaId == video.id ||
                  (history.season == video.season && history.episode == video.episode)
              }
              val progressRatio = epHistory?.progressRatio ?: 0f
              val isWatched = epHistory?.isFinished == true || progressRatio >= 0.9f

              EpisodeItemCard(
                video = video,
                progressRatio = progressRatio,
                isWatched = isWatched,
                onClick = {
                  val epTitle = "${currentMeta.name} S${video.season ?: 1}E${video.episode ?: 1} - ${video.title ?: ""}".trim()
                  viewModel.openStreamsForMedia(epTitle, video.id, video = video)
                },
              )
            }
          }

          item {
            Spacer(modifier = Modifier.height(32.dp))
          }
        }
      }
    }
  }
}

private fun formatReleaseDate(rawDate: String?): String? {
  if (rawDate.isNullOrBlank()) return null
  val datePart = rawDate.substringBefore("T").trim()
  return try {
    val parts = datePart.split("-")
    if (parts.size == 3) {
      val year = parts[0]
      val monthInt = parts[1].toIntOrNull() ?: 1
      val day = parts[2].toIntOrNull() ?: 1
      val monthNames = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
      val monthStr = monthNames.getOrElse(monthInt) { "" }
      if (monthStr.isNotBlank()) "$day $monthStr $year" else datePart
    } else {
      datePart
    }
  } catch (_: Exception) {
    datePart
  }
}

@Composable
private fun EpisodeItemCard(
  video: StremioVideo,
  progressRatio: Float = 0f,
  isWatched: Boolean = false,
  onClick: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 6.dp)
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .width(100.dp)
          .aspectRatio(16f / 9f)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
      ) {
        if (!video.thumbnail.isNullOrBlank()) {
          RemoteImage(
            url = video.thumbnail,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }
        Icon(
          Icons.RoundedFilled.PlayArrow,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(24.dp),
        )

        // Watched Eye Badge (top-right)
        if (isWatched) {
          Surface(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(4.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFFFFC107),
          ) {
            Text(
              text = "👁",
              fontSize = 10.sp,
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
          }
        }

        // Progress Bar (bottom)
        if (progressRatio > 0f) {
          LinearProgressIndicator(
            progress = { progressRatio },
            modifier = Modifier
              .fillMaxWidth()
              .height(3.dp)
              .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Black.copy(alpha = 0.5f),
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "E${video.episode ?: 1}: ${video.title ?: "Episode ${video.episode ?: 1}"}",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        val formattedDate = formatReleaseDate(video.released)
        if (!formattedDate.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = formattedDate,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }

        if (!video.overview.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = video.overview,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
