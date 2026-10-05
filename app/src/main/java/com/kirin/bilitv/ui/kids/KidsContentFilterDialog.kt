package com.kirin.bilitv.ui.kids

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.kirin.bilitv.R
import com.kirin.bilitv.core.settings.KidsContentFilter
import com.kirin.bilitv.ui.focus.BiliFocusableSurface
import com.kirin.bilitv.ui.theme.BiliFocus
import com.kirin.bilitv.ui.theme.BiliRadius
import com.kirin.bilitv.ui.theme.BiliSizing
import com.kirin.bilitv.ui.theme.BiliSpacing
import com.kirin.bilitv.ui.theme.BiliTypography
import com.kirin.bilitv.ui.theme.LocalHomeColors

@Composable
internal fun KidsContentFilterDialog(
  title: String,
  description: String,
  initialPattern: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val homeColors = LocalHomeColors.current
  var pattern by remember(initialPattern) { mutableStateOf(initialPattern) }
  val inputFocusRequester = remember { FocusRequester() }
  val trimmed = pattern.trim()
  val invalid = !KidsContentFilter.isValid(trimmed)
  val shape = RoundedCornerShape(BiliRadius.Card)
  BackHandler(onBack = onDismiss)
  LaunchedEffect(Unit) {
    runCatching { inputFocusRequester.requestFocus() }
  }
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(homeColors.backgroundTop.copy(alpha = 0.96f))
      .padding(BiliSpacing.Xl),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(0.6f),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(BiliSpacing.Md),
    ) {
      Text(
        text = title,
        color = homeColors.textPrimary,
        fontSize = BiliTypography.SectionTitle,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Text(
        text = description,
        color = homeColors.textSecondary,
        fontSize = BiliTypography.BodySmall,
        textAlign = TextAlign.Center,
      )
      BasicTextField(
        value = pattern,
        onValueChange = { pattern = it },
        modifier = Modifier
          .fillMaxWidth()
          .height(BiliSizing.SearchTouchInputHeight)
          .focusRequester(inputFocusRequester)
          .clip(shape)
          .background(homeColors.glassSurfaceStrong, shape)
          .border(BorderStroke(BiliFocus.RestingBorderWidth, homeColors.glassBorder), shape)
          .padding(horizontal = BiliSpacing.Lg),
        singleLine = true,
        textStyle = TextStyle(
          color = homeColors.textPrimary,
          fontSize = BiliTypography.SearchInput,
          fontWeight = FontWeight.Bold,
        ),
        cursorBrush = SolidColor(homeColors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
          onDone = {
            if (!invalid) {
              onConfirm(trimmed)
            }
          },
        ),
        decorationBox = { innerTextField ->
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            if (pattern.isEmpty()) {
              Text(
                text = stringResource(R.string.settings_kids_filter_hint),
                color = homeColors.textSecondary,
                fontSize = BiliTypography.Body,
              )
            }
            innerTextField()
          }
        },
      )
      if (invalid) {
        Text(
          text = stringResource(R.string.settings_kids_filter_invalid),
          color = homeColors.accent,
          fontSize = BiliTypography.BodySmall,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(BiliSpacing.Md)) {
        FilterAction(
          label = stringResource(R.string.kids_pin_cancel),
          onClick = onDismiss,
        )
        FilterAction(
          label = stringResource(R.string.settings_kids_filter_confirm),
          onClick = {
            if (!invalid) {
              onConfirm(trimmed)
            }
          },
        )
      }
    }
  }
}

@Composable
private fun FilterAction(
  label: String,
  onClick: () -> Unit,
) {
  val homeColors = LocalHomeColors.current
  BiliFocusableSurface(
    shape = RoundedCornerShape(BiliRadius.Panel),
    onClick = onClick,
    modifier = Modifier.height(BiliSizing.SettingsChipHeight),
  ) {
    Text(
      text = label,
      color = homeColors.textPrimary,
      fontSize = BiliTypography.Body,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = BiliSpacing.Lg, vertical = BiliSpacing.Sm),
    )
  }
}
