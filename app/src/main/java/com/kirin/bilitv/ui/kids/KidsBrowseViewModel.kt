package com.kirin.bilitv.ui.kids

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kirin.bilitv.core.model.VideoSummary
import com.kirin.bilitv.core.network.KidsEntry
import com.kirin.bilitv.core.network.VideoRepository
import com.kirin.bilitv.core.settings.KidsContentFilter
import com.kirin.bilitv.core.player.CollectionPlaybackContext
import com.kirin.bilitv.core.player.CollectionPlaybackItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class KidsSection {
  Favorites,
  Collections,
  Following,
}

internal enum class FollowingTab {
  Latest,
  Users,
}

internal data class KidsBrowseViewState(
  val loading: Boolean = false,
  val loadingMore: Boolean = false,
  val errorMessage: String? = null,
  val entries: List<KidsEntry> = emptyList(),
  val videos: List<VideoSummary> = emptyList(),
  val openedEntry: KidsEntry? = null,
  val followingTab: FollowingTab = FollowingTab.Latest,
  val hasMore: Boolean = false,
  val collectionPage: Int = 1,
  val contentFilter: String = "",
)

internal class KidsBrowseViewModel(
  private val section: KidsSection,
  private val videoRepository: VideoRepository,
) : ViewModel() {
  private val _viewState = MutableStateFlow(KidsBrowseViewState())
  val viewState: StateFlow<KidsBrowseViewState> = _viewState.asStateFlow()

  private var loadedKey: String? = null
  private var nextPage = 1
  private var nextOffset = ""
  private var loadJob: Job? = null
  private var moreJob: Job? = null
  private var allowedFollowingMids: Set<Long>? = null
  private var allowedFollowingPattern: String? = null
  private var groupedUsers: List<KidsEntry>? = null
  private var groupedUsersPattern: String? = null

  fun updateContentFilter(pattern: String) {
    if (_viewState.value.contentFilter == pattern) {
      return
    }
    clearFollowingGroupCache()
    loadedKey = null
    _viewState.value = _viewState.value.copy(contentFilter = pattern)
    load(forceRefresh = true)
  }

  fun load(forceRefresh: Boolean = false) {
    val key = loadKey(_viewState.value)
    if (!forceRefresh && loadedKey == key && _viewState.value.errorMessage == null) {
      return
    }
    loadJob?.cancel()
    moreJob?.cancel()
    if (forceRefresh) {
      clearFollowingGroupCache()
    }
    nextPage = 1
    nextOffset = ""
    _viewState.value = _viewState.value.copy(
      loading = true,
      loadingMore = false,
      errorMessage = null,
      entries = emptyList(),
      videos = emptyList(),
      hasMore = false,
      collectionPage = 1,
    )
    loadJob = viewModelScope.launch {
      runCatching { collectVisible(page = 1, offset = "") }
        .onSuccess { page ->
          loadedKey = key
          nextPage = page.nextPage
          nextOffset = page.nextOffset
          _viewState.value = _viewState.value.copy(
            loading = false,
            entries = page.entries,
            videos = page.videos,
            hasMore = page.hasMore,
            collectionPage = 1,
          )
        }
        .onFailure { error ->
          if (error is CancellationException) throw error
          _viewState.value = _viewState.value.copy(
            loading = false,
            errorMessage = error.message.orEmpty(),
          )
        }
    }
  }

  fun loadMore() {
    val state = _viewState.value
    if (state.loading || state.loadingMore || !state.hasMore || moreJob?.isActive == true) {
      return
    }
    val page = nextPage
    val offset = nextOffset
    moreJob = viewModelScope.launch {
      _viewState.value = _viewState.value.copy(loadingMore = true)
      runCatching { collectVisible(page = page, offset = offset) }
        .onSuccess { result ->
          nextPage = result.nextPage
          nextOffset = result.nextOffset
          _viewState.value = _viewState.value.copy(
            loadingMore = false,
            entries = _viewState.value.entries + result.entries,
            videos = _viewState.value.videos + result.videos,
            hasMore = result.hasMore,
            collectionPage = (result.nextPage - 1).coerceAtLeast(1),
          )
        }
        .onFailure { error ->
          if (error is CancellationException) throw error
          _viewState.value = _viewState.value.copy(loadingMore = false)
        }
    }
  }

  fun openEntry(entry: KidsEntry) {
    if (_viewState.value.openedEntry?.id == entry.id) return
    loadedKey = null
    _viewState.value = _viewState.value.copy(openedEntry = entry)
    load(forceRefresh = true)
  }

  fun closeEntry() {
    if (_viewState.value.openedEntry == null) return
    loadedKey = null
    _viewState.value = _viewState.value.copy(openedEntry = null)
    load(forceRefresh = true)
  }

  fun selectFollowingTab(tab: FollowingTab) {
    if (section != KidsSection.Following || _viewState.value.followingTab == tab) return
    loadedKey = null
    _viewState.value = _viewState.value.copy(
      followingTab = tab,
      openedEntry = null,
    )
    load(forceRefresh = true)
  }

  fun collectionContextFor(video: VideoSummary): CollectionPlaybackContext? {
    val state = _viewState.value
    if (section != KidsSection.Collections || state.openedEntry == null) return null
    val index = state.videos.indexOfFirst { item -> item.bvid == video.bvid }
    if (index < 0) return null
    return CollectionPlaybackContext(
      mediaId = state.openedEntry.id,
      page = state.collectionPage,
      index = index,
      hasMore = state.hasMore,
      ownerMid = state.openedEntry.ownerMid,
      items = state.videos.map { item ->
        CollectionPlaybackItem(
          bvid = item.bvid,
          cid = item.cid,
          title = item.title,
          ownerName = item.ownerName,
          ownerFace = item.ownerFace,
          ownerMid = item.ownerMid,
        )
      },
    )
  }

  private suspend fun fetch(page: Int, offset: String): KidsFetchResult {
    val state = _viewState.value
    val opened = state.openedEntry
    return when (section) {
      KidsSection.Favorites -> if (opened == null) {
        val result = videoRepository.getCreatedFavoriteFolders(page)
        KidsFetchResult(entries = result.entries, hasMore = result.hasMore)
      } else {
        val result = videoRepository.getFavoriteVideos(opened.id, page)
        KidsFetchResult(videos = result.videos, hasMore = result.hasMore)
      }
      KidsSection.Collections -> if (opened == null) {
        val result = videoRepository.getSubscribedCollections(page)
        KidsFetchResult(entries = result.entries, hasMore = result.hasMore)
      } else {
        val result = videoRepository.getCollectionVideos(opened.id, opened.ownerMid, page)
        KidsFetchResult(videos = result.videos, hasMore = result.hasMore)
      }
      KidsSection.Following -> when {
        state.followingTab == FollowingTab.Users && opened == null -> {
          val result = videoRepository.getFollowings(page)
          KidsFetchResult(entries = result.entries, hasMore = result.hasMore)
        }
        state.followingTab == FollowingTab.Users && opened != null -> {
          val videos = videoRepository.getSpaceVideos(mid = opened.id, page = page)
          KidsFetchResult(videos = videos, hasMore = videos.isNotEmpty())
        }
        else -> {
          val result = videoRepository.getFollowingVideos(offset)
          KidsFetchResult(videos = result.videos, hasMore = result.hasMore, nextOffset = result.offset)
        }
      }
    }
  }

  private suspend fun collectVisible(page: Int, offset: String): KidsFetchResult {
    val state = _viewState.value
    val pattern = state.contentFilter
    val opened = state.openedEntry
    val filterEntries = opened == null &&
      !(section == KidsSection.Following && state.followingTab == FollowingTab.Latest)
    val filterLatest = opened == null &&
      section == KidsSection.Following &&
      state.followingTab == FollowingTab.Latest &&
      pattern.isNotBlank()
    if (!filterEntries && !filterLatest) {
      val result = fetch(page = page, offset = offset)
      return result.copy(nextPage = page + 1)
    }
    if (filterLatest) {
      return collectFilteredLatest(offset, pattern)
    }
    if (section == KidsSection.Following && pattern.isNotBlank()) {
      return pageEntries(usersInMatchingGroups(pattern), page)
    }
    return collectFilteredEntries(page, pattern)
  }

  private suspend fun collectFilteredEntries(
    startPage: Int,
    pattern: String,
  ): KidsFetchResult {
    val kept = mutableListOf<KidsEntry>()
    var page = startPage
    var hasMore = true
    var reads = 0
    while (kept.size < VisiblePageSize && hasMore && reads < MaxFilterPages) {
      val result = fetch(page = page, offset = "")
      kept += result.entries.filter { entry ->
        KidsContentFilter.matches(pattern, entry.title)
      }
      hasMore = result.hasMore
      page += 1
      reads += 1
    }
    return KidsFetchResult(
      entries = kept,
      hasMore = hasMore,
      nextPage = page,
    )
  }

  private suspend fun collectFilteredLatest(offset: String, pattern: String): KidsFetchResult {
    val allowedMids = allowedFollowingMids(pattern)
    val videos = mutableListOf<VideoSummary>()
    var cursor = offset
    var hasMore = true
    var reads = 0
    while (videos.size < VisiblePageSize && hasMore && reads < FilteredLatestMaxReads) {
      val page = fetch(page = 1, offset = cursor)
      videos += page.videos.filter { video -> video.ownerMid in allowedMids }
      cursor = page.nextOffset
      hasMore = page.hasMore
      reads += 1
    }
    return KidsFetchResult(
      videos = videos,
      hasMore = hasMore,
      nextOffset = cursor,
    )
  }

  private suspend fun allowedFollowingMids(pattern: String): Set<Long> {
    if (allowedFollowingPattern == pattern && allowedFollowingMids != null) {
      return allowedFollowingMids.orEmpty()
    }
    val mids = usersInMatchingGroups(pattern).map { entry -> entry.id }.toSet()
    allowedFollowingMids = mids
    allowedFollowingPattern = pattern
    return mids
  }

  private suspend fun usersInMatchingGroups(pattern: String): List<KidsEntry> {
    if (groupedUsersPattern == pattern && groupedUsers != null) {
      return groupedUsers.orEmpty()
    }
    val groups = videoRepository.getRelationGroups().filter { group ->
      group.count > 0 && KidsContentFilter.matches(pattern, group.name)
    }
    val users = videoRepository.getUsersInGroups(groups)
    groupedUsers = users
    groupedUsersPattern = pattern
    return users
  }

  private fun pageEntries(entries: List<KidsEntry>, page: Int): KidsFetchResult {
    val from = (page - 1).coerceAtLeast(0) * VisiblePageSize
    val slice = entries.drop(from).take(VisiblePageSize)
    return KidsFetchResult(
      entries = slice,
      hasMore = from + slice.size < entries.size,
      nextPage = page + 1,
    )
  }

  private fun clearFollowingGroupCache() {
    allowedFollowingMids = null
    allowedFollowingPattern = null
    groupedUsers = null
    groupedUsersPattern = null
  }

  private fun loadKey(state: KidsBrowseViewState): String {
    return "${section.name}:${state.followingTab.name}:${state.openedEntry?.id ?: 0L}:${state.contentFilter}"
  }

  companion object {
    fun factory(section: KidsSection, videoRepository: VideoRepository): ViewModelProvider.Factory {
      return viewModelFactory {
        initializer {
          KidsBrowseViewModel(section = section, videoRepository = videoRepository)
        }
      }
    }
  }
}

private data class KidsFetchResult(
  val entries: List<KidsEntry> = emptyList(),
  val videos: List<VideoSummary> = emptyList(),
  val hasMore: Boolean = false,
  val nextOffset: String = "",
  val nextPage: Int = 1,
)

private const val VisiblePageSize = 20
private const val MaxFilterPages = 8
private const val FilteredLatestMaxReads = 24
