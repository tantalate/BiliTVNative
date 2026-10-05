package com.kirin.bilitv.core.network

import com.kirin.bilitv.core.model.VideoSummary
import com.kirin.bilitv.core.storage.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

internal class KidsContentRepository(
  private val apiClient: BiliApiClient,
  private val sessionStore: SessionStore,
  private val userFeedRepository: UserFeedRepository,
  private val spaceVideoRepository: SpaceVideoRepository,
) {
  private var cachedTagNames: Map<Long, String>? = null

  suspend fun getCreatedFolders(page: Int): KidsEntryPage {
    val session = sessionStore.session.first()
    val mid = session.mid ?: return KidsEntryPage(entries = emptyList(), hasMore = false)
    if (session.sessData.isNullOrBlank()) {
      return KidsEntryPage(entries = emptyList(), hasMore = false)
    }
    val root = apiClient.getJson(
      url = BiliApiEndpoints.CreatedFavoriteFolders,
      params = mapOf(
        "up_mid" to mid.toString(),
        "pn" to page.toString(),
        "ps" to PageSize.toString(),
      ),
      sessData = session.sessData,
      biliJct = session.biliJct,
    ).rootObject()
    root.requireBiliCodeOk("created favorite folders")
    return parseEntryPage(root, page)
  }

  suspend fun getFavoriteVideos(mediaId: Long, page: Int): KidsMediaPage {
    val session = sessionStore.session.first()
    if (session.sessData.isNullOrBlank() || mediaId <= 0L) {
      return KidsMediaPage(videos = emptyList(), hasMore = false)
    }
    val root = apiClient.getJson(
      url = BiliApiEndpoints.FavoriteResources,
      params = mapOf(
        "media_id" to mediaId.toString(),
        "pn" to page.toString(),
        "ps" to PageSize.toString(),
        "platform" to "web",
      ),
      sessData = session.sessData,
      biliJct = session.biliJct,
    ).rootObject()
    root.requireBiliCodeOk("favorite resources")
    return parseMediaPage(root.obj("data"), page)
  }

  suspend fun getSubscribedCollections(page: Int): KidsEntryPage {
    val session = sessionStore.session.first()
    val mid = session.mid ?: return KidsEntryPage(entries = emptyList(), hasMore = false)
    if (session.sessData.isNullOrBlank()) {
      return KidsEntryPage(entries = emptyList(), hasMore = false)
    }
    val root = apiClient.getJson(
      url = BiliApiEndpoints.SubscribedCollections,
      params = mapOf(
        "up_mid" to mid.toString(),
        "pn" to page.toString(),
        "ps" to PageSize.toString(),
        "platform" to "web",
      ),
      sessData = session.sessData,
      biliJct = session.biliJct,
    ).rootObject()
    root.requireBiliCodeOk("subscribed collections")
    val data = root.obj("data")
    val entries = data.entryArray().mapNotNull { item -> item.toSubscribedCollection() }
    val count = data?.int("count") ?: 0
    val hasMore = data?.boolean("has_more") == true || (count > page * PageSize)
    return KidsEntryPage(entries = entries, hasMore = hasMore)
  }

  suspend fun getCollectionVideos(mediaId: Long, ownerMid: Long, page: Int): KidsMediaPage {
    val session = sessionStore.session.first()
    if (mediaId <= 0L || ownerMid <= 0L) {
      return KidsMediaPage(videos = emptyList(), hasMore = false)
    }
    val root = apiClient.getJson(
      url = BiliApiEndpoints.CollectionResources,
      params = mapOf(
        "mid" to ownerMid.toString(),
        "season_id" to mediaId.toString(),
        "page_num" to page.toString(),
        "page_size" to PageSize.toString(),
      ),
      sessData = session.sessData,
      biliJct = session.biliJct,
    ).rootObject()
    root.requireBiliCodeOk("collection resources")
    return parseSeasonPage(root.obj("data"), page)
  }

  suspend fun getRelationGroups(): List<RelationGroup> {
    val session = sessionStore.session.first()
    if (session.sessData.isNullOrBlank()) {
      return emptyList()
    }
    val root = apiClient.getJson(
      url = BiliApiEndpoints.RelationTags,
      params = mapOf("only_master" to "false"),
      sessData = session.sessData,
      biliJct = session.biliJct,
    ).rootObject()
    root.requireBiliCodeOk("relation tags")
    val data = root["data"] as? JsonArray ?: return emptyList()
    return data.mapNotNull { element ->
      val item = element.asObjectOrNull() ?: return@mapNotNull null
      val name = item.string("name")
      if (name.isBlank()) {
        null
      } else {
        RelationGroup(
          id = item.long("tagid"),
          name = name,
          count = item.int("count"),
        )
      }
    }
  }

  suspend fun getUsersInGroups(groups: List<RelationGroup>): List<KidsEntry> {
    if (groups.isEmpty()) {
      return emptyList()
    }
    val session = sessionStore.session.first()
    val mid = session.mid ?: return emptyList()
    if (session.sessData.isNullOrBlank()) {
      return emptyList()
    }
    val users = linkedMapOf<Long, KidsEntry>()
    for (group in groups) {
      if (group.count <= 0) {
        continue
      }
      val maxPages = ((group.count + PageSize - 1) / PageSize).coerceAtLeast(1)
      var page = 1
      var hasMore = true
      while (hasMore && page <= maxPages) {
        val result = getRelationGroupUsers(
          ownerMid = mid,
          sessData = session.sessData,
          biliJct = session.biliJct,
          group = group,
          page = page,
        )
        result.entries.forEach { entry ->
          val existing = users[entry.id]
          users[entry.id] = if (existing == null) {
            entry
          } else {
            existing.copy(groupNames = (existing.groupNames + entry.groupNames).distinct())
          }
        }
        hasMore = result.hasMore
        page += 1
      }
    }
    return users.values.toList()
  }

  suspend fun getFollowings(page: Int): KidsEntryPage {
    val session = sessionStore.session.first()
    val mid = session.mid ?: return KidsEntryPage(entries = emptyList(), hasMore = false)
    if (session.sessData.isNullOrBlank()) {
      return KidsEntryPage(entries = emptyList(), hasMore = false)
    }
    val root = apiClient.getJson(
      url = BiliApiEndpoints.RelationFollowings,
      params = mapOf(
        "vmid" to mid.toString(),
        "pn" to page.toString(),
        "ps" to PageSize.toString(),
        "order_type" to "attention",
      ),
      sessData = session.sessData,
      biliJct = session.biliJct,
    ).rootObject()
    root.requireBiliCodeOk("followings")
    val data = root.obj("data")
    val list = data.entryArray()
    val tagNames = runCatching {
      relationTagNames(session.sessData, session.biliJct)
    }.getOrDefault(emptyMap())
    val entries = list.mapNotNull { item -> item.toFollowingEntry(tagNames) }
    val total = data?.int("total") ?: 0
    return KidsEntryPage(
      entries = entries,
      hasMore = page * PageSize < total && entries.isNotEmpty(),
    )
  }

  suspend fun getFollowingVideos(offset: String): DynamicFeedPage {
    return userFeedRepository.getDynamicFeed(offset = offset, type = DynamicTypeVideo)
  }

  suspend fun getUserVideos(mid: Long, page: Int): List<VideoSummary> {
    return spaceVideoRepository.getSpaceVideos(mid = mid, page = page)
  }

  private fun parseEntryPage(root: JsonObject, page: Int): KidsEntryPage {
    val data = root.obj("data")
    val list = data.entryArray().ifEmpty { root.entryArray("data") }
    val entries = list.mapNotNull { item -> item.toLibraryEntry() }
    val total = data?.int("count")?.takeIf { it > 0 } ?: data?.int("total") ?: 0
    val hasMore = when {
      data?.boolean("has_more") == true -> true
      total > 0 -> page * PageSize < total && entries.isNotEmpty()
      else -> entries.size >= PageSize
    }
    return KidsEntryPage(entries = entries, hasMore = hasMore && entries.isNotEmpty())
  }

  private fun parseMediaPage(data: JsonObject?, page: Int): KidsMediaPage {
    val medias = (data?.get("medias") as? JsonArray)
      ?: (data?.get("media_list") as? JsonArray)
      ?: (data?.get("archives") as? JsonArray)
      ?: JsonArray(emptyList())
    val videos = medias.mapNotNull { element ->
      element.asObjectOrNull()?.toKidsVideo()
    }.filter { video -> video.bvid.isNotBlank() }
    val total = data?.int("media_count")?.takeIf { it > 0 } ?: data?.int("total") ?: 0
    val hasMore = when {
      data?.boolean("has_more") == true -> true
      total > 0 -> page * PageSize < total && videos.isNotEmpty()
      else -> videos.size >= PageSize
    }
    return KidsMediaPage(videos = videos, hasMore = hasMore && videos.isNotEmpty())
  }

  private fun JsonObject?.entryArray(name: String = "list"): List<JsonObject> {
    val array = this?.get(name) as? JsonArray
      ?: this?.get("items") as? JsonArray
      ?: this?.get("media_list") as? JsonArray
      ?: return emptyList()
    return array.mapNotNull { element -> element.asObjectOrNull() }
  }

  private fun parseSeasonPage(data: JsonObject?, page: Int): KidsMediaPage {
    val archives = (data?.get("archives") as? JsonArray) ?: JsonArray(emptyList())
    val videos = archives.mapNotNull { element ->
      element.asObjectOrNull()?.toKidsVideo()
    }.filter { video -> video.bvid.isNotBlank() }
    val total = data?.obj("page")?.int("total")?.takeIf { it > 0 }
      ?: data?.obj("meta")?.int("total")
      ?: 0
    val hasMore = when {
      total > 0 -> page * PageSize < total && videos.isNotEmpty()
      else -> videos.size >= PageSize
    }
    return KidsMediaPage(videos = videos, hasMore = hasMore && videos.isNotEmpty())
  }

  private fun JsonObject.toSubscribedCollection(): KidsEntry? {
    if (int("type") != SeasonCollectionType) return null
    val id = long("id").takeIf { it > 0L } ?: return null
    val title = string("title").ifBlank { return null }
    val count = int("media_count")
    return KidsEntry(
      id = id,
      title = title,
      subtitle = count.takeIf { it > 0 }?.toString().orEmpty(),
      cover = string("cover"),
      ownerMid = obj("upper")?.long("mid") ?: 0L,
    )
  }

  private fun JsonObject.toLibraryEntry(): KidsEntry? {
    val id = long("id").takeIf { it > 0L } ?: long("media_id").takeIf { it > 0L } ?: return null
    val title = string("title").ifBlank { string("name") }
    if (title.isBlank()) return null
    val count = int("media_count").takeIf { it > 0 } ?: int("count")
    return KidsEntry(
      id = id,
      title = title,
      subtitle = count.takeIf { it > 0 }?.toString().orEmpty(),
      cover = string("cover").ifBlank { string("face") },
    )
  }

  private suspend fun getRelationGroupUsers(
    ownerMid: Long,
    sessData: String,
    biliJct: String?,
    group: RelationGroup,
    page: Int,
  ): KidsEntryPage {
    val root = apiClient.getJson(
      url = BiliApiEndpoints.RelationTag,
      params = mapOf(
        "tagid" to group.id.toString(),
        "pn" to page.toString(),
        "ps" to PageSize.toString(),
        "mid" to ownerMid.toString(),
      ),
      sessData = sessData,
      biliJct = biliJct,
    ).rootObject()
    root.requireBiliCodeOk("relation tag")
    val data = root["data"]
    val list = when (data) {
      is JsonArray -> data.mapNotNull { element -> element.asObjectOrNull() }
      is JsonObject -> data.entryArray()
      else -> emptyList()
    }
    val entries = list.mapNotNull { item -> item.toGroupUser(group.name) }
    return KidsEntryPage(
      entries = entries,
      hasMore = entries.size >= PageSize,
    )
  }

  private fun JsonObject.toGroupUser(groupName: String): KidsEntry? {
    val id = long("mid").takeIf { it > 0L } ?: return null
    val title = string("uname").ifBlank { return null }
    return KidsEntry(
      id = id,
      title = title,
      subtitle = string("sign"),
      cover = string("face"),
      groupNames = listOf(groupName),
    )
  }

  private suspend fun relationTagNames(sessData: String, biliJct: String?): Map<Long, String> {
    cachedTagNames?.let { return it }
    val root = apiClient.getJson(
      url = BiliApiEndpoints.RelationTags,
      sessData = sessData,
      biliJct = biliJct,
    ).rootObject()
    root.requireBiliCodeOk("relation tags")
    val data = root["data"] as? JsonArray ?: return emptyMap()
    val names = data.mapNotNull { element ->
      val item = element.asObjectOrNull() ?: return@mapNotNull null
      val name = item.string("name")
      if (name.isBlank()) {
        null
      } else {
        item.long("tagid") to name
      }
    }.toMap()
    cachedTagNames = names
    return names
  }

  private fun JsonObject.toFollowingEntry(tagNames: Map<Long, String>): KidsEntry? {
    val id = long("mid").takeIf { it > 0L } ?: return null
    val title = string("uname").ifBlank { return null }
    val tagIds = tagIds()
    val groupNames = if (tagIds.isEmpty()) {
      listOfNotNull(tagNames[DefaultFollowTagId])
    } else {
      tagIds.mapNotNull { tagId -> tagNames[tagId] }
    }
    return KidsEntry(
      id = id,
      title = title,
      subtitle = string("sign"),
      cover = string("face"),
      groupNames = groupNames,
    )
  }

  private fun JsonObject.tagIds(): List<Long> {
    val array = (get("tag") as? JsonArray) ?: return emptyList()
    return array.mapNotNull { element ->
      when (element) {
        is JsonPrimitive -> element.longOrNull
        is JsonObject -> element["tagid"]?.let { element.long("tagid") }
        else -> null
      }
    }
  }

  private fun JsonObject.toKidsVideo(): VideoSummary? {
    val bvid = string("bvid").ifBlank { return null }
    val upper = obj("upper") ?: obj("owner")
    val stat = obj("cnt_info") ?: obj("stat")
    return VideoSummary(
      bvid = bvid,
      title = string("title"),
      pic = string("cover").ifBlank { string("pic") },
      ownerName = upper?.string("name").orEmpty().ifBlank { upper?.string("uname").orEmpty() },
      ownerFace = upper?.string("face").orEmpty(),
      ownerMid = upper?.long("mid") ?: 0L,
      view = BiliNumberParser.toInt(stat?.get("play") ?: stat?.get("view")),
      danmaku = BiliNumberParser.toInt(stat?.get("danmaku")),
      duration = BiliNumberParser.parseDuration(get("duration")),
      pubdate = long("pubdate").takeIf { it > 0L } ?: long("fav_time"),
      badge = "",
      cid = long("cid"),
    )
  }

  private companion object {
    const val PageSize = 20
    const val SeasonCollectionType = 21
    const val DefaultFollowTagId = 0L
  }
}

data class RelationGroup(
  val id: Long,
  val name: String,
  val count: Int,
)

data class KidsEntry(
  val id: Long,
  val title: String,
  val subtitle: String,
  val cover: String,
  val ownerMid: Long = 0L,
  val groupNames: List<String> = emptyList(),
)

data class KidsEntryPage(
  val entries: List<KidsEntry>,
  val hasMore: Boolean,
)

data class KidsMediaPage(
  val videos: List<VideoSummary>,
  val hasMore: Boolean,
)
