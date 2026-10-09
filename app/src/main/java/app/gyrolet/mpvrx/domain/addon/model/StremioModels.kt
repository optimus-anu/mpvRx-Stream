/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.domain.addon.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * Flexible serializer for strings that may be numbers, strings, or null in JSON.
 */
object FlexibleStringSerializer : KSerializer<String?> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): String? {
    val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
    val element = jsonDecoder.decodeJsonElement()
    return when (element) {
      is JsonNull -> null
      is JsonPrimitive -> element.content
      else -> element.toString()
    }
  }

  @OptIn(ExperimentalSerializationApi::class)
  override fun serialize(encoder: Encoder, value: String?) {
    if (value == null) {
      encoder.encodeNull()
    } else {
      encoder.encodeString(value)
    }
  }
}

/**
 * Custom serializer for [StremioResource] that handles both string representations
 * (e.g. `"stream"`) and object representations (e.g. `{"name": "stream", "types": ["movie"]}`).
 */
object StremioResourceSerializer : KSerializer<StremioResource> {
  override val descriptor: SerialDescriptor =
    buildClassSerialDescriptor("StremioResource") {
      element<String>("name")
      element<List<String>?>("types", isOptional = true)
      element<List<String>?>("idPrefixes", isOptional = true)
    }

  override fun deserialize(decoder: Decoder): StremioResource {
    val jsonDecoder = decoder as? JsonDecoder
      ?: throw SerializationException("Expected JsonDecoder for StremioResource")
    val element = jsonDecoder.decodeJsonElement()
    return when (element) {
      is JsonPrimitive -> StremioResource(name = element.content)
      is JsonObject -> {
        val name = element["name"]?.jsonPrimitive?.content ?: "unknown"
        val types = element["types"]?.jsonArray?.map { it.jsonPrimitive.content }
        val idPrefixes = element["idPrefixes"]?.jsonArray?.map { it.jsonPrimitive.content }
        StremioResource(name = name, types = types, idPrefixes = idPrefixes)
      }
      else -> StremioResource(name = "unknown")
    }
  }

  override fun serialize(encoder: Encoder, value: StremioResource) {
    val jsonEncoder = encoder as? JsonEncoder
      ?: throw SerializationException("Expected JsonEncoder for StremioResource")
    if (value.types == null && value.idPrefixes == null) {
      jsonEncoder.encodeJsonElement(JsonPrimitive(value.name))
    } else {
      val obj = buildJsonObject {
        put("name", JsonPrimitive(value.name))
        value.types?.let { types ->
          put("types", buildJsonArray { types.forEach { add(JsonPrimitive(it)) } })
        }
        value.idPrefixes?.let { prefixes ->
          put("idPrefixes", buildJsonArray { prefixes.forEach { add(JsonPrimitive(it)) } })
        }
      }
      jsonEncoder.encodeJsonElement(obj)
    }
  }
}

/**
 * Serializer for list of [StremioResource] items.
 */
object StremioResourceListSerializer : KSerializer<List<StremioResource>> {
  private val delegate = ListSerializer(StremioResourceSerializer)
  override val descriptor: SerialDescriptor = delegate.descriptor

  override fun deserialize(decoder: Decoder): List<StremioResource> = delegate.deserialize(decoder)

  override fun serialize(encoder: Encoder, value: List<StremioResource>) = delegate.serialize(encoder, value)
}

@Immutable
@Serializable(with = StremioResourceSerializer::class)
data class StremioResource(
  val name: String,
  val types: List<String>? = null,
  val idPrefixes: List<String>? = null,
) {
  fun supportsType(type: String): Boolean {
    return types == null || types.isEmpty() || types.contains(type)
  }

  fun supportsId(id: String): Boolean {
    if (idPrefixes == null || idPrefixes.isEmpty()) return true
    return idPrefixes.any { prefix -> id.startsWith(prefix) }
  }
}

@Immutable
@Serializable
data class StremioCatalog(
  val type: String,
  val id: String,
  val name: String? = null,
  val extra: List<StremioExtra> = emptyList(),
  val extraSupported: List<String>? = null,
  val extraRequired: List<String>? = null,
)

@Immutable
@Serializable
data class StremioExtra(
  val name: String,
  val isRequired: Boolean? = false,
  val options: List<String>? = null,
  val optionsLimit: Int? = null,
)

@Immutable
@Serializable
data class StremioManifestBehaviorHints(
  val configurable: Boolean? = false,
  val configurationRequired: Boolean? = false,
  val adult: Boolean? = false,
  val p2p: Boolean? = false,
)

@Immutable
@Serializable
data class StremioManifest(
  val id: String,
  val version: String,
  val name: String,
  val description: String? = null,
  @Serializable(with = StremioResourceListSerializer::class)
  val resources: List<StremioResource> = emptyList(),
  val types: List<String> = emptyList(),
  val catalogs: List<StremioCatalog> = emptyList(),
  val idPrefixes: List<String>? = null,
  val background: String? = null,
  val logo: String? = null,
  val contactEmail: String? = null,
  val behaviorHints: StremioManifestBehaviorHints? = null,
)

@Serializable
data class StremioMetaResponse(
  val metas: List<StremioMeta> = emptyList(),
)

@Serializable
data class StremioMetaDetailResponse(
  val meta: StremioMeta? = null,
)

@Immutable
@Serializable
data class StremioMeta(
  val id: String,
  val type: String,
  val name: String,
  val poster: String? = null,
  val posterShape: String? = null,
  val banner: String? = null,
  val logo: String? = null,
  val description: String? = null,
  val releaseInfo: String? = null,
  @Serializable(with = FlexibleStringSerializer::class)
  val imdbRating: String? = null,
  val genres: List<String>? = null,
  val background: String? = null,
  val runtime: String? = null,
  val cast: List<String>? = null,
  val director: List<String>? = null,
  val writer: List<String>? = null,
  @Serializable(with = FlexibleStringSerializer::class)
  val year: String? = null,
  val trailers: List<StremioTrailer>? = null,
  val videos: List<StremioVideo>? = null,
  val links: List<StremioMetaLink>? = null,
  val behaviorHints: StremioMetaBehaviorHints? = null,
)

@Immutable
@Serializable
data class StremioVideo(
  val id: String,
  val title: String? = null,
  val released: String? = null,
  val thumbnail: String? = null,
  val episode: Int? = null,
  val season: Int? = null,
  val overview: String? = null,
  val streams: List<StremioStream>? = null,
)

@Immutable
@Serializable
data class StremioTrailer(
  val source: String,
  val type: String? = "Trailer",
)

@Immutable
@Serializable
data class StremioMetaLink(
  val name: String,
  val category: String,
  val url: String,
)

@Immutable
@Serializable
data class StremioMetaBehaviorHints(
  val defaultVideoId: String? = null,
  val hasScheduledVideos: Boolean? = false,
)

@Serializable
data class StremioStreamResponse(
  val streams: List<StremioStream> = emptyList(),
)

@Immutable
@Serializable
data class StremioStream(
  val url: String? = null,
  val infoHash: String? = null,
  val fileIdx: Int? = null,
  val externalUrl: String? = null,
  val ytId: String? = null,
  val title: String? = null,
  val name: String? = null,
  val description: String? = null,
  val subtitles: List<StremioSubtitle>? = null,
  val behaviorHints: StremioStreamBehaviorHints? = null,
  val addonName: String? = null,
) {
  val isDirectHttpUrl: Boolean
    get() = !url.isNullOrBlank() &&
      (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))

  val isTorrent: Boolean
    get() = !infoHash.isNullOrBlank()

  val isExternal: Boolean
    get() = !externalUrl.isNullOrBlank()

  val isYouTube: Boolean
    get() = !ytId.isNullOrBlank()

  fun getMagnetUri(): String? {
    val hash = infoHash?.trim() ?: return null
    if (hash.isEmpty()) return null
    val idxParam = fileIdx?.let { "&f=$it" } ?: ""
    val nameParam = name?.let {
      try {
        "&dn=${URLEncoder.encode(it, "UTF-8")}"
      } catch (_: Exception) {
        ""
      }
    } ?: ""
    return "magnet:?xt=urn:btih:$hash$idxParam$nameParam"
  }
}

@Immutable
@Serializable
data class StremioSubtitle(
  val id: String? = null,
  val url: String,
  val lang: String,
)

@Immutable
@Serializable
data class StremioStreamBehaviorHints(
  val notSupported: Boolean? = false,
  val proxyHeaders: StremioProxyHeaders? = null,
  val bingeGroup: String? = null,
  val filename: String? = null,
  val videoHash: String? = null,
  val videoSize: Long? = null,
)

@Immutable
@Serializable
data class StremioProxyHeaders(
  val request: Map<String, String>? = null,
  val response: Map<String, String>? = null,
)
