package com.kirin.bilitv.ui.kids

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.kirin.bilitv.R
import com.kirin.bilitv.core.settings.KidsPin
import com.kirin.bilitv.ui.focus.BiliFocusableSurface
import com.kirin.bilitv.ui.theme.BiliRadius
import com.kirin.bilitv.ui.theme.BiliSizing
import com.kirin.bilitv.ui.theme.BiliSpacing
import com.kirin.bilitv.ui.theme.BiliTypography
import com.kirin.bilitv.ui.theme.LocalHomeColors

internal enum class KidsPinPrompt {
  Create,
  ConfirmCreate,
  Unlock,
}

@Composable
internal fun KidsPinDialog(
  prompt: KidsPinPrompt,
  entered: String,
  error: String?,
  onDigit: (Int) -> Unit,
  onBackspace: () -> Unit,
  onDismiss: () -> Unit,
) {
  val homeColors = LocalHomeColors.current
  val title = when (prompt) {
    KidsPinPrompt.Create -> stringResource(R.string.kids_pin_create_title)
    KidsPinPrompt.ConfirmCreate -> stringResource(R.string.kids_pin_confirm_title)
    KidsPinPrompt.Unlock -> stringResource(R.string.kids_pin_unlock_title)
  }
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(homeColors.backgroundTop.copy(alpha = 0.96f))
      .clickable(enabled = false) {}
      .padding(BiliSpacing.Xl),
    contentAlignment = Alignment.Center,
  ) {
    Column(
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
        text = "●".repeat(entered.length) + "○".repeat((KidsPin.Length - entered.length).coerceAtLeast(0)),
        color = homeColors.accent,
        fontSize = BiliTypography.SectionTitle,
      )
      if (!error.isNullOrBlank()) {
        Text(
          text = error,
          color = homeColors.accent,
          fontSize = BiliTypography.BodySmall,
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(BiliSpacing.Sm)) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
          androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(BiliSpacing.Sm)) {
            row.forEach { digit ->
              PinKey(label = digit.toString(), onClick = { onDigit(digit) })
            }
          }
        }
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(BiliSpacing.Sm)) {
          PinKey(label = stringResource(R.string.kids_pin_backspace), onClick = onBackspace)
          PinKey(label = "0", onClick = { onDigit(0) })
          PinKey(label = stringResource(R.string.kids_pin_cancel), onClick = onDismiss)
        }
      }
    }
  }
}

@Composable
private fun PinKey(
  label: String,
  onClick: () -> Unit,
) {
  val homeColors = LocalHomeColors.current
  BiliFocusableSurface(
    shape = RoundedCornerShape(BiliRadius.Panel),
    onClick = onClick,
    modifier = Modifier.size(BiliSizing.SettingsRowHeight),
  ) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(
        text = label,
        color = homeColors.textPrimary,
        fontSize = BiliTypography.Body,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}
