package com.kirin.bilitv.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import com.kirin.bilitv.core.storage.UserSession
import com.kirin.bilitv.ui.theme.BiliRadius
import com.kirin.bilitv.ui.theme.BiliSizing
import com.kirin.bilitv.ui.theme.BiliTypography
import com.kirin.bilitv.ui.theme.LocalHomeColors

@Composable
internal fun TvAppScaffold(
  selectedDestination: AppDestination,
  accountSelected: Boolean,
  userSession: UserSession,
  autoConfirmOnFocus: Boolean,
  accountFocusRequester: FocusRequester,
  navFocusRequesters: Map<AppDestination, FocusRequester>,
  contentPaddingEnabled: Boolean,
  onAccountSelected: () -> Unit,
  onDestinationSelected: (AppDestination) -> Unit,
  shouldAutoConfirmDestination: (AppDestination) -> Boolean,
  onMoveRight: (AppDestination) -> Boolean,
  destinations: List<AppDestination> = AppDestination.StandardOrder,
  content: @Composable () -> Unit,
) {
  val showDestinationLabel = destinations == AppDestination.KidsOrder
  var scaffoldTopPx by remember { mutableFloatStateOf(0f) }
  var labelDestination by remember { mutableStateOf<AppDestination?>(null) }
  var labelTopPx by remember { mutableFloatStateOf(0f) }
  val density = LocalDensity.current
  Box(
    modifier = Modifier
      .fillMaxSize()
      .onGloballyPositioned { coordinates ->
        scaffoldTopPx = coordinates.positionInRoot().y
      },
  ) {
  Row(
    modifier = Modifier
      .fillMaxSize()
      .testTag(AdaptiveAppScaffoldSideNavigationTestTag),
  ) {
    AppSidebar(
      selectedDestination = selectedDestination,
      accountSelected = accountSelected,
      userSession = userSession,
      autoConfirmOnFocus = autoConfirmOnFocus,
      accountFocusRequester = accountFocusRequester,
      navFocusRequesters = navFocusRequesters,
      onAccountSelected = onAccountSelected,
      onDestinationSelected = onDestinationSelected,
      shouldAutoConfirmDestination = shouldAutoConfirmDestination,
      onMoveRight = onMoveRight,
      destinations = destinations,
      onDestinationLabel = { destination, topPx ->
        labelDestination = destination
        labelTopPx = topPx
      },
    )
    Box(
      modifier = if (contentPaddingEnabled) {
        Modifier
          .fillMaxSize()
          .padding(BiliSizing.ContentPadding)
      } else {
        Modifier.fillMaxSize()
      },
    ) {
      content()
    }
  }
  if (showDestinationLabel && labelDestination != null) {
    val labelTop = with(density) { (labelTopPx - scaffoldTopPx).toDp() }
    NavDestinationLabel(
      title = stringResource(labelDestination!!.titleRes),
      modifier = Modifier
        .offset(x = BiliSizing.SidebarWidth + BiliSizing.NavDestinationLabelGap, y = labelTop)
        .zIndex(1f),
    )
  }
  }
}

@Composable
private fun NavDestinationLabel(
  title: String,
  modifier: Modifier = Modifier,
) {
  val homeColors = LocalHomeColors.current
  Box(
    modifier = modifier
      .height(BiliSizing.NavItemHeight)
      .focusProperties { canFocus = false },
    contentAlignment = Alignment.CenterStart,
  ) {
    Text(
      text = title,
      color = homeColors.textPrimary,
      fontSize = BiliTypography.Body,
      fontWeight = FontWeight.Bold,
      modifier = Modifier
        .background(homeColors.cardSurface, RoundedCornerShape(BiliRadius.Panel))
        .padding(
          horizontal = BiliSizing.NavDestinationLabelHorizontalPadding,
          vertical = BiliSizing.NavDestinationLabelVerticalPadding,
        ),
    )
  }
}
