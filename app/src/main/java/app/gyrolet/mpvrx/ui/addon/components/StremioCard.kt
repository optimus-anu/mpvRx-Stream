/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.addon.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gyrolet.mpvrx.domain.addon.model.StremioMeta
import app.gyrolet.mpvrx.presentation.components.RemoteImage

@Composable
fun StremioCard(
  meta: StremioMeta,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier
      .width(140.dp)
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(2f / 3f)
          .background(MaterialTheme.colorScheme.surfaceVariant),
      ) {
        val posterUrl = meta.poster ?: meta.banner ?: meta.logo
        if (!posterUrl.isNullOrBlank()) {
          RemoteImage(
            url = posterUrl,
            contentDescription = meta.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }

        // Rating Badge
        if (!meta.imdbRating.isNullOrBlank()) {
          Surface(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(6.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.75f),
          ) {
            Text(
              text = "⭐ ${meta.imdbRating}",
              style = MaterialTheme.typography.labelSmall,
              color = Color(0xFFFFD700),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }

        // Type Tag Badge
        Surface(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(6.dp),
          shape = RoundedCornerShape(4.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
        ) {
          Text(
            text = meta.type.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
          )
        }
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(8.dp),
      ) {
        Text(
          text = meta.name,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        val subInfo = listOfNotNull(meta.releaseInfo ?: meta.year).firstOrNull()
        if (!subInfo.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = subInfo,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
      }
    }
  }
}
