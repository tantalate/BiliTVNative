package com.kirin.bilitv.ui.kids

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kirin.bilitv.R
import com.kirin.bilitv.core.image.buildOwnerAvatarRequest
import com.kirin.bilitv.core.image.buildVideoThumbnailRequest
import com.kirin.bilitv.core.model.VideoSummary
import com.kirin.bilitv.core.network.KidsEntry
import com.kirin.bilitv.core.player.CollectionPlaybackContext
import com.kirin.bilitv.ui.common.FeedStatusScreen
import com.kirin.bilitv.ui.focus.BiliFocusableSurface
import com.kirin.bilitv.ui.home.AdaptiveVideoGrid
import com.kirin.bilitv.ui.i18n.convertChineseText
import com.kirin.bilitv.ui.settings.LocalBiliPerformancePolicy
import com.kirin.bilitv.ui.theme.BiliColors
import com.kirin.bilitv.ui.theme.BiliRadius
import com.kirin.bilitv.ui.theme.BiliSizing
import com.kirin.bilitv.ui.theme.BiliSpacing
import com.kirin.bilitv.ui.theme.BiliTypography
import com.kirin.bilitv.ui.theme.LocalHomeColors
import kotlinx.coroutines.flow.first

private const val KidsFocusRetryCount = 12

@Composable
internal fun KidsBrowseScreen(
  viewModel: KidsBrowseViewModel,
  section: KidsSection,
  isLoggedIn: Boolean,
  firstItemFocusRequester: FocusRequester,
  restoreFocusRequestKey: Int,
  onRestoreFocusHandled: (Int) -> Unit,
  onMoveLeftToNav: () -> Boolean,
  onVideoSelected: (VideoSummary, CollectionPlaybackContext?) -> Unit,
  contentFilter: String,
) {
  if (!isLoggedIn) {
    FeedStatusScreen(message = stringResource(R.string.kids_signed_out))
    return
  }

  val viewState by viewModel.viewState.collectAsStateWithLifecycle()
  var focusedVideoIndex by rememberSaveable(section) { mutableIntStateOf(0) }
  var returnEntryId by remember { mutableStateOf<Long?>(null) }
  val backFocusRequester = remember { FocusRequester() }
  val loadingFocusRequester = remember { FocusRequester() }
  val followingTabFocusRequesters = remember {
    FollowingTab.entries.associateWith { FocusRequester() }
  }
  val openedEntry = viewState.openedEntry

  fun holdContentFocus() {
    runCatching { loadingFocusRequester.requestFocus() }
  }

  fun closeOpenedEntry() {
    holdContentFocus()
    returnEntryId = openedEntry?.id
    viewModel.closeEntry()
  }

  fun focusSelectedFollowingTab(): Boolean {
    val requester = followingTabFocusRequesters[viewState.followingTab] ?: return false
    return runCatching { requester.requestFocus() }.getOrDefault(false)
  }

  BackHandler(enabled = openedEntry != null, onBack = ::closeOpenedEntry)

  LaunchedEffect(contentFilter) {
    viewModel.updateContentFilter(contentFilter)
  }

  LaunchedEffect(viewModel, openedEntry?.id, viewState.followingTab) {
    viewModel.load()
  }

  LaunchedEffect(openedEntry?.id) {
    val entryId = openedEntry?.id ?: return@LaunchedEffect
    focusedVideoIndex = 0
    holdContentFocus()
    snapshotFlow { viewState.loading }.first { loading -> !loading }
    val focusVideos = viewState.openedEntry?.id == entryId && viewState.videos.isNotEmpty()
    repeat(KidsFocusRetryCount) {
      withFrameNanos { }
      val requester = if (focusVideos) firstItemFocusRequester else backFocusRequester
      if (runCatching { requester.requestFocus() }.getOrDefault(false)) {
        return@LaunchedEffect
      }
    }
  }

  val showVideos = openedEntry != null ||
    (section == KidsSection.Following && viewState.followingTab == FollowingTab.Latest)
  val followingTabsVisible = section == KidsSection.Following && openedEntry == null

  Box(modifier = Modifier.fillMaxSize()) {
  Column(modifier = Modifier.fillMaxSize()) {
    if (followingTabsVisible) {
      FollowingTabRow(
        selected = viewState.followingTab,
        tabFocusRequesters = followingTabFocusRequesters,
        onSelected = viewModel::selectFollowingTab,
        onMoveLeftToNav = onMoveLeftToNav,
        onMoveDown = {
          runCatching { firstItemFocusRequester.requestFocus() }
          true
        },
      )
    }
    if (openedEntry != null) {
      KidsBackHeader(
        section = section,
        title = convertChineseText(openedEntry.title),
        backFocusRequester = backFocusRequester,
        onBack = ::closeOpenedEntry,
        onMoveLeftToNav = onMoveLeftToNav,
        onMoveDown = {
          runCatching { firstItemFocusRequester.requestFocus() }
          true
        },
      )
    }
    when {
      viewState.loading && viewState.entries.isEmpty() && viewState.videos.isEmpty() -> {
        FeedStatusScreen(message = stringResource(R.string.kids_loading))
      }
      viewState.errorMessage != null && viewState.entries.isEmpty() && viewState.videos.isEmpty() -> {
        FeedStatusScreen(
          message = stringResource(R.string.kids_failed_with_message, viewState.errorMessage.orEmpty()),
          actionLabel = stringResource(R.string.kids_retry),
          onAction = { viewModel.load(forceRefresh = true) },
        )
      }
      showVideos && viewState.videos.isEmpty() -> {
        FeedStatusScreen(message = stringResource(R.string.kids_empty))
      }
      !showVideos && viewState.entries.isEmpty() -> {
        FeedStatusScreen(message = stringResource(R.string.kids_empty))
      }
      showVideos -> {
        AdaptiveVideoGrid(
          videos = viewState.videos,
          firstItemFocusRequester = firstItemFocusRequester,
          restoredFocusIndex = focusedVideoIndex,
          restoreFocusRequestKey = restoreFocusRequestKey,
          onRestoreFocusHandled = onRestoreFocusHandled,
          onFocusedIndexChange = { index, _ ->
            focusedVideoIndex = index
          },
          onLoadMore = viewModel::loadMore,
          onRefresh = { viewModel.load(forceRefresh = true) },
          onMoveLeftToNav = onMoveLeftToNav,
          onMoveUpFromFirstRow = {
            when {
              openedEntry != null -> runCatching { backFocusRequester.requestFocus() }
              followingTabsVisible -> focusSelectedFollowingTab()
            }
            true
          },
          onBackKey = if (openedEntry != null) {
            {
              closeOpenedEntry()
              true
            }
          } else {
            null
          },
          onVideoSelected = { video ->
            onVideoSelected(video, viewModel.collectionContextFor(video))
          },
        )
      }
      else -> {
        KidsEntryGrid(
          entries = viewState.entries,
          showAvatar = section == KidsSection.Following,
          firstItemFocusRequester = firstItemFocusRequester,
          returnEntryId = returnEntryId,
          onReturnFocusHandled = { returnEntryId = null },
          onLoadMore = viewModel::loadMore,
          onMoveLeftToNav = onMoveLeftToNav,
          onMoveUpFromFirstRow = {
            if (followingTabsVisible) {
              focusSelectedFollowingTab()
            }
            true
          },
          onEntrySelected = { entry ->
            holdContentFocus()
            viewModel.openEntry(entry)
          },
        )
      }
    }
  }
    Box(
      modifier = Modifier
        .size(BiliSpacing.Xs)
        .focusRequester(loadingFocusRequester)
        .focusable(),
    )
  }
}

@Composable
private fun KidsBackHeader(
  section: KidsSection,
  title: String,
  backFocusRequester: FocusRequester,
  onBack: () -> Unit,
  onMoveLeftToNav: () -> Boolean,
  onMoveDown: () -> Boolean,
) {
  val homeColors = LocalHomeColors.current
  val label = when (section) {
    KidsSection.Favorites -> stringResource(R.string.kids_back_to_favorites)
    KidsSection.Collections -> stringResource(R.string.kids_back_to_collections)
    KidsSection.Following -> stringResource(R.string.kids_back_to_users)
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = BiliSpacing.Md),
    horizontalArrangement = Arrangement.spacedBy(BiliSpacing.Md),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BiliFocusableSurface(
      shape = RoundedCornerShape(BiliRadius.Panel),
      onClick = onBack,
      modifier = Modifier
        .focusRequester(backFocusRequester)
        .height(BiliSizing.SettingsChipHeight)
        .onPreviewKeyEvent { event ->
          if (event.type != KeyEventType.KeyDown) {
            return@onPreviewKeyEvent false
          }
          when (event.key) {
            Key.DirectionLeft -> onMoveLeftToNav()
            Key.DirectionDown -> onMoveDown()
            else -> false
          }
        },
    ) {
      Text(
        text = label,
        color = homeColors.textPrimary,
        fontSize = BiliTypography.Body,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = BiliSpacing.Lg, vertical = BiliSpacing.Sm),
      )
    }
    Text(
      text = title,
      color = homeColors.textSecondary,
      fontSize = BiliTypography.Body,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun FollowingTabRow(
  selected: FollowingTab,
  tabFocusRequesters: Map<FollowingTab, FocusRequester>,
  onSelected: (FollowingTab) -> Unit,
  onMoveLeftToNav: () -> Boolean,
  onMoveDown: () -> Boolean,
) {
  val tabs = FollowingTab.entries
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = BiliSpacing.Md),
    horizontalArrangement = Arrangement.spacedBy(BiliSpacing.Md),
  ) {
    tabs.forEachIndexed { index, tab ->
      val title = when (tab) {
        FollowingTab.Latest -> stringResource(R.string.kids_following_latest)
        FollowingTab.Users -> stringResource(R.string.kids_following_users)
      }
      BiliFocusableSurface(
        shape = RoundedCornerShape(BiliRadius.Panel),
        onClick = { onSelected(tab) },
        modifier = Modifier
          .focusRequester(tabFocusRequesters.getValue(tab))
          .height(BiliSizing.SettingsRowHeight / 2)
          .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) {
              return@onPreviewKeyEvent false
            }
            when (event.key) {
              Key.DirectionLeft -> {
                if (index == 0) {
                  onMoveLeftToNav()
                } else {
                  runCatching { tabFocusRequesters.getValue(tabs[index - 1]).requestFocus() }
                }
                true
              }
              Key.DirectionRight -> {
                if (index < tabs.lastIndex) {
                  runCatching { tabFocusRequesters.getValue(tabs[index + 1]).requestFocus() }
                }
                true
              }
              Key.DirectionDown -> onMoveDown()
              Key.DirectionUp -> true
              else -> false
            }
          },
      ) {
        Text(
          text = title,
          color = if (tab == selected) LocalHomeColors.current.accent else LocalHomeColors.current.textPrimary,
          fontSize = BiliTypography.Body,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = BiliSpacing.Lg, vertical = BiliSpacing.Sm),
        )
      }
    }
  }
}

@Composable
private fun KidsEntryGrid(
  entries: List<KidsEntry>,
  showAvatar: Boolean,
  firstItemFocusRequester: FocusRequester,
  returnEntryId: Long?,
  onReturnFocusHandled: () -> Unit,
  onLoadMore: () -> Unit,
  onMoveLeftToNav: () -> Boolean,
  onMoveUpFromFirstRow: () -> Boolean,
  onEntrySelected: (KidsEntry) -> Unit,
) {
  val cellSize = BiliSizing.KidsEntryTileSize + BiliSizing.KidsEntryTileInset * 2
  val gridState = rememberLazyGridState()
  val returnIndex = if (returnEntryId == null) {
    -1
  } else {
    entries.indexOfFirst { entry -> entry.id == returnEntryId }
  }
  val initialFocusIndex = if (returnIndex >= 0) returnIndex else 0
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val columns = ((maxWidth + BiliSpacing.Md) / (cellSize + BiliSpacing.Md)).toInt().coerceIn(3, 6)
    LazyVerticalGrid(
      columns = GridCells.Fixed(columns),
      state = gridState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(BiliSpacing.Sm),
      horizontalArrangement = Arrangement.spacedBy(BiliSpacing.Md),
      verticalArrangement = Arrangement.spacedBy(BiliSpacing.Md),
    ) {
      itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
        if (index >= entries.lastIndex - columns) {
          onLoadMore()
        }
        KidsEntryTile(
          entry = entry,
          showAvatar = showAvatar,
          modifier = Modifier
            .then(if (index == initialFocusIndex) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
            .kidsEntryBoundaryKeys(
              index = index,
              columns = columns,
              onMoveLeftToNav = onMoveLeftToNav,
              onMoveUpFromFirstRow = onMoveUpFromFirstRow,
            ),
          onClick = { onEntrySelected(entry) },
        )
      }
    }
  }
  LaunchedEffect(returnEntryId, entries.firstOrNull()?.id) {
    val targetId = returnEntryId ?: return@LaunchedEffect
    if (entries.isEmpty()) {
      return@LaunchedEffect
    }
    val index = entries.indexOfFirst { entry -> entry.id == targetId }.let { found ->
      if (found >= 0) found else 0
    }
    gridState.scrollToItem(index)
    repeat(KidsFocusRetryCount) {
      withFrameNanos { }
      if (runCatching { firstItemFocusRequester.requestFocus() }.getOrDefault(false)) {
        onReturnFocusHandled()
        return@LaunchedEffect
      }
    }
  }
  LaunchedEffect(entries.firstOrNull()?.id) {
    if (returnEntryId != null || entries.isEmpty()) {
      return@LaunchedEffect
    }
    withFrameNanos { }
    runCatching { firstItemFocusRequester.requestFocus() }
  }
}

@Composable
private fun KidsEntryTile(
  entry: KidsEntry,
  showAvatar: Boolean,
  modifier: Modifier,
  onClick: () -> Unit,
) {
  val homeColors = LocalHomeColors.current
  val title = convertChineseText(entry.title)
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth(),
  ) {
    BiliFocusableSurface(
      shape = if (showAvatar) CircleShape else RoundedCornerShape(BiliRadius.Panel),
      onClick = onClick,
      modifier = modifier
        .padding(BiliSizing.KidsEntryTileInset)
        .size(BiliSizing.KidsEntryTileSize),
    ) {
      if (showAvatar) {
        EntryAvatar(url = entry.cover, contentDescription = title)
      } else {
        EntryCover(url = entry.cover, contentDescription = title)
      }
    }
    Text(
      text = title,
      color = homeColors.textPrimary,
      fontSize = BiliTypography.BodySmall,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .padding(top = BiliSpacing.Sm)
        .width(BiliSizing.KidsEntryTileSize),
    )
  }
}

@Composable
private fun EntryAvatar(
  url: String,
  contentDescription: String,
) {
  val context = LocalContext.current
  val performancePolicy = LocalBiliPerformancePolicy.current
  val fallback = ColorPainter(BiliColors.SurfaceElevated)
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    if (url.isBlank()) {
      Box(
        modifier = Modifier
          .size(BiliSizing.KidsEntryAvatarSize)
          .clip(CircleShape)
          .padding(BiliSpacing.Sm),
      )
    } else {
      val request = remember(
        context,
        url,
        performancePolicy.ownerAvatarRgb565Enabled,
        performancePolicy.imageMemoryCacheEnabled,
      ) {
        buildOwnerAvatarRequest(
          context = context,
          url = url,
          allowRgb565 = performancePolicy.ownerAvatarRgb565Enabled,
          memoryCacheEnabled = performancePolicy.imageMemoryCacheEnabled,
        )
      }
      AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        placeholder = fallback,
        error = fallback,
        modifier = Modifier
          .size(BiliSizing.KidsEntryAvatarSize)
          .clip(CircleShape),
      )
    }
  }
}

@Composable
private fun EntryCover(
  url: String,
  contentDescription: String,
) {
  val context = LocalContext.current
  val performancePolicy = LocalBiliPerformancePolicy.current
  val fallback = ColorPainter(BiliColors.SurfaceElevated)
  if (url.isBlank()) {
    Box(modifier = Modifier.fillMaxSize())
    return
  }
  val request = remember(
    context,
    url,
    performancePolicy.imageMemoryCacheEnabled,
  ) {
    buildVideoThumbnailRequest(
      context = context,
      url = url,
      memoryCacheEnabled = performancePolicy.imageMemoryCacheEnabled,
    )
  }
  AsyncImage(
    model = request,
    contentDescription = contentDescription,
    contentScale = ContentScale.Crop,
    placeholder = fallback,
    error = fallback,
    modifier = Modifier.fillMaxSize(),
  )
}

private fun Modifier.kidsEntryBoundaryKeys(
  index: Int,
  columns: Int,
  onMoveLeftToNav: () -> Boolean,
  onMoveUpFromFirstRow: () -> Boolean,
): Modifier {
  return onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) {
      return@onPreviewKeyEvent false
    }
    when (event.key) {
      Key.DirectionUp -> if (index < columns) onMoveUpFromFirstRow() else false
      Key.DirectionLeft -> if (index % columns == 0) onMoveLeftToNav() else false
      else -> false
    }
  }
}
