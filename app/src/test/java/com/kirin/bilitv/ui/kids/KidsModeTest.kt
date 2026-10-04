package com.kirin.bilitv.ui.kids

import com.kirin.bilitv.core.model.VideoSummary
import com.kirin.bilitv.core.player.CollectionPlaybackContext
import com.kirin.bilitv.core.player.CollectionPlaybackItem
import com.kirin.bilitv.core.player.PlaybackRequest
import com.kirin.bilitv.core.settings.KidsPin
import com.kirin.bilitv.ui.player.nextLoadedCollectionRequest
import com.kirin.bilitv.ui.player.withCollectionPage
import com.kirin.bilitv.ui.shell.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsModeTest {
  @Test
  fun pinMatchesOnlyTheHashedValue() {
    val salt = KidsPin.newSalt()
    val hash = KidsPin.hash("2468", salt)

    assertFalse(hash.contains("2468"))
    assertTrue(KidsPin.matches("2468", salt, hash))
    assertFalse(KidsPin.matches("1357", salt, hash))
    assertFalse(KidsPin.matches("246", salt, hash))
    assertFalse(KidsPin.matches("2468", "", hash))
  }

  @Test
  fun kidsNavigationKeepsOnlyWhitelistedDestinations() {
    assertEquals(
      listOf(
        AppDestination.Favorites,
        AppDestination.Collections,
        AppDestination.Following,
        AppDestination.Settings,
      ),
      AppDestination.visible(kidsModeEnabled = true),
    )
    assertFalse(AppDestination.Recommend in AppDestination.visible(kidsModeEnabled = true))
    assertFalse(AppDestination.Search in AppDestination.visible(kidsModeEnabled = true))
    assertEquals(AppDestination.StandardOrder, AppDestination.visible(kidsModeEnabled = false))
  }

  @Test
  fun collectionCompletionSelectsTheNextLoadedItem() {
    val request = collectionRequest(index = 0)

    val next = request.nextLoadedCollectionRequest(selectedQualityId = 80)

    assertEquals("BV2", next?.bvid)
    assertEquals(2L, next?.cid)
    assertEquals(0L, next?.startPositionMs)
    assertEquals(true, next?.forceStartPosition)
    assertEquals(1, next?.collectionPlayback?.index)
    assertEquals(42L, next?.collectionPlayback?.mediaId)
  }

  @Test
  fun collectionCompletionDoesNotLeaveTheLoadedQueue() {
    val request = collectionRequest(index = 1)

    assertNull(request.nextLoadedCollectionRequest(selectedQualityId = null))
  }

  @Test
  fun nextCollectionPageStartsAtItsFirstVideo() {
    val request = collectionRequest(index = 1)
    val next = request.withCollectionPage(
      mediaId = 42L,
      page = 2,
      videos = listOf(video("BV3", 3L), video("BV4", 4L)),
      hasMore = false,
      selectedQualityId = 64,
    )

    assertEquals("BV3", next?.bvid)
    assertEquals(0, next?.collectionPlayback?.index)
    assertEquals(2, next?.collectionPlayback?.page)
    assertEquals(false, next?.collectionPlayback?.hasMore)
    assertEquals(99L, next?.collectionPlayback?.ownerMid)
    assertEquals(listOf("BV3", "BV4"), next?.collectionPlayback?.items?.map { item -> item.bvid })
  }

  private fun collectionRequest(index: Int): PlaybackRequest {
    return PlaybackRequest(
      bvid = if (index == 0) "BV1" else "BV2",
      cid = if (index == 0) 1L else 2L,
      title = "current",
      collectionPlayback = CollectionPlaybackContext(
        mediaId = 42L,
        page = 1,
        index = index,
        hasMore = true,
        ownerMid = 99L,
        items = listOf(
          CollectionPlaybackItem(bvid = "BV1", cid = 1L, title = "one"),
          CollectionPlaybackItem(bvid = "BV2", cid = 2L, title = "two"),
        ),
      ),
    )
  }

  private fun video(bvid: String, cid: Long): VideoSummary {
    return VideoSummary(
      bvid = bvid,
      title = bvid,
      pic = "",
      ownerName = "owner",
      ownerFace = "",
      ownerMid = 7L,
      view = 0,
      danmaku = 0,
      duration = 10,
      pubdate = 0L,
      badge = "",
      cid = cid,
    )
  }
}
