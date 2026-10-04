package com.kirin.bilitv.ui.player

import com.kirin.bilitv.core.model.VideoSummary
import com.kirin.bilitv.core.player.CollectionPlaybackContext
import com.kirin.bilitv.core.player.CollectionPlaybackItem
import com.kirin.bilitv.core.player.PlaybackRequest
import com.kirin.bilitv.core.player.PlaybackVideoMetadata

internal data class PlayerNextEpisodeCompletion(
  val request: PlaybackRequest,
  val title: String,
)

internal fun PlaybackRequest.nextEpisodeCompletion(
  metadata: PlaybackVideoMetadata?,
  selectedQualityId: Int?,
): PlayerNextEpisodeCompletion? {
  val pages = metadata?.pages.orEmpty()
  val currentIndex = pages.indexOfFirst { episode ->
    episode.cid == cid || (historyPage > 0 && episode.page == historyPage)
  }
  val nextEpisode = pages.getOrNull(currentIndex + 1) ?: return null
  val nextRequest = copy(
    cid = nextEpisode.cid,
    startPositionMs = 0L,
    preferredQualityId = selectedQualityId,
    forceStartPosition = true,
    historyPage = nextEpisode.page,
    advanceToNextHistoryEpisode = false,
  )
  return PlayerNextEpisodeCompletion(
    request = nextRequest,
    title = nextEpisode.title.ifBlank { nextRequest.title },
  )
}

internal fun PlaybackRequest.nextLoadedCollectionRequest(selectedQualityId: Int?): PlaybackRequest? {
  val context = collectionPlayback ?: return null
  val item = context.items.getOrNull(context.index + 1) ?: return null
  return copy(
    bvid = item.bvid,
    cid = item.cid,
    title = item.title,
    startPositionMs = 0L,
    preferredQualityId = selectedQualityId,
    forceStartPosition = true,
    ownerName = item.ownerName,
    ownerFace = item.ownerFace,
    ownerMid = item.ownerMid,
    historyPage = 0,
    advanceToNextHistoryEpisode = false,
    collectionPlayback = context.copy(index = context.index + 1),
  )
}

internal fun PlaybackRequest.withCollectionPage(
  mediaId: Long,
  page: Int,
  videos: List<VideoSummary>,
  hasMore: Boolean,
  selectedQualityId: Int?,
): PlaybackRequest? {
  val first = videos.firstOrNull() ?: return null
  return copy(
    bvid = first.bvid,
    cid = first.cid,
    title = first.title,
    startPositionMs = 0L,
    preferredQualityId = selectedQualityId,
    forceStartPosition = true,
    ownerName = first.ownerName,
    ownerFace = first.ownerFace,
    ownerMid = first.ownerMid,
    historyPage = 0,
    advanceToNextHistoryEpisode = false,
    collectionPlayback = CollectionPlaybackContext(
      mediaId = mediaId,
      page = page,
      index = 0,
      hasMore = hasMore,
      ownerMid = collectionPlayback?.ownerMid ?: 0L,
      items = videos.map { video ->
        CollectionPlaybackItem(
          bvid = video.bvid,
          cid = video.cid,
          title = video.title,
          ownerName = video.ownerName,
          ownerFace = video.ownerFace,
          ownerMid = video.ownerMid,
        )
      },
    ),
  )
}

internal fun List<VideoSummary>.firstCompletionRelatedVideo(currentBvid: String): VideoSummary? {
  return firstOrNull { video -> !video.bvid.equals(currentBvid, ignoreCase = true) }
}
