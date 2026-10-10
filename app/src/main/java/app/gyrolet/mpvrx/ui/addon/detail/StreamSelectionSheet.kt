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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.domain.addon.model.StremioStream
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSelectionSheet(
  mediaTitle: String,
  streams: List<StremioStream>,
  isLoading: Boolean,
  onStreamSelected: (StremioStream) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var selectedAddonFilter by remember { mutableStateOf("All Addons") }
  var selectedQualityFilter by remember { mutableStateOf("All") }

  val addonNames = remember(streams) {
    val names = streams.mapNotNull { it.addonName?.takeIf { n -> n.isNotBlank() } }.distinct().sorted()
    listOf("All Addons") + names
  }

  val filteredStreams = remember(streams, selectedAddonFilter, selectedQualityFilter) {
    streams.filter { stream ->
      val addonMatches = if (selectedAddonFilter == "All Addons") true else stream.addonName == selectedAddonFilter
      val qualityMatches = stream.matchesQualityFilter(selectedQualityFilter)
      addonMatches && qualityMatches
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .padding(bottom = 32.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Streams",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = mediaTitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        IconButton(onClick = onDismiss) {
          Icon(Icons.RoundedFilled.Close, contentDescription = "Close")
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Addon Tabs (e.g., All Addons, Torrentio RD, AIOStreams)
      if (addonNames.size > 2) {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(addonNames) { addon ->
            FilterChip(
              selected = selectedAddonFilter == addon,
              onClick = { selectedAddonFilter = addon },
              label = { Text(addon, fontWeight = FontWeight.SemiBold) },
            )
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
      }

      // Quality Filter Chips
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val filters = listOf("All", "4K", "1080p", "720p", "Torrent", "Direct")
        items(filters) { filter ->
          FilterChip(
            selected = selectedQualityFilter == filter,
            onClick = { selectedQualityFilter = filter },
            label = { Text(filter) },
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      if (isLoading) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Resolving streams from active addons...", style = MaterialTheme.typography.bodyMedium)
          }
        }
      } else if (filteredStreams.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "No stream links found for selected filters.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.height(420.dp),
        ) {
          items(filteredStreams) { stream ->
            RichStreamItemCard(
              stream = stream,
              onClick = { onStreamSelected(stream) },
            )
          }
        }
      }
    }
  }
}

private fun StremioStream.matchesQualityFilter(filter: String): Boolean {
  if (filter == "All") return true
  val fullText = listOfNotNull(
    name,
    title,
    description,
    behaviorHints?.filename,
  ).joinToString(" ").lowercase()

  val is4k = fullText.contains("4k") || fullText.contains("2160p") || fullText.contains("2160") || fullText.contains("uhd")
  val is1080p = fullText.contains("1080p") || fullText.contains("1080") || fullText.contains("fhd")
  val is720p = fullText.contains("720p") || fullText.contains("720")

  return when (filter) {
    "4K" -> is4k
    "1080p" -> is1080p && !is4k
    "720p" -> is720p && !is4k && !is1080p
    "Torrent" -> isTorrent
    "Direct" -> isDirectHttpUrl
    else -> true
  }
}

@Composable
private fun RichStreamItemCard(
  stream: StremioStream,
  onClick: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.Top,
    ) {
      // Play Button Circle
      Box(
        modifier = Modifier
          .size(42.dp)
          .clip(CircleShape)
          .background(Color(0xFF2E7D32)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.RoundedFilled.PlayArrow,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(24.dp),
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(
        modifier = Modifier.weight(1f),
      ) {
        // Stream Header / Addon Title
        val streamHeader = buildString {
          if (!stream.addonName.isNullOrBlank()) {
            append("[${stream.addonName}] ")
          }
          val mainName = stream.name?.replace("\n", " ")?.trim()
          if (!mainName.isNullOrBlank()) {
            append(mainName)
          } else if (stream.isTorrent) {
            append("Torrent P2P Stream")
          } else {
            append("Direct Video Stream")
          }
        }

        Text(
          text = streamHeader,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Render multi-line stream title / details cleanly
        val titleText = stream.title
        if (!titleText.isNullOrBlank()) {
          val lines = titleText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
          for (line in lines) {
            Text(
              text = line,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        val descText = stream.description
        if (!descText.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = descText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
