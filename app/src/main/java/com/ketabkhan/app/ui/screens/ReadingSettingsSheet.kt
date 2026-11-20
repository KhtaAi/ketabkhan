package com.ketabkhan.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ketabkhan.app.domain.reading.ReadingSettings
import com.ketabkhan.app.domain.reading.TitlePauseDuration
import com.ketabkhan.app.domain.text.PersianDigits
import com.ketabkhan.app.ui.components.KetabkhanPrimaryButton
import com.ketabkhan.app.ui.components.KetabkhanSecondaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingSettingsSheet(
  initialSettings: ReadingSettings,
  onSaveSettings: (ReadingSettings) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var draft by remember(initialSettings) { mutableStateOf(initialSettings) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "تنظیمات برنامهٔ خواندن",
          style = MaterialTheme.typography.headlineSmall,
        )
        Text(
          text = "تعیین کنید کدام نقش‌های سند در خواندن صوتی خوانده یا رد شوند.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(vertical = 4.dp),
        ) {
          ReadingToggleRow(
            title = "خواندن فهرست کتاب",
            subtitle = "خواندن ردیف‌های فهرست به‌شکل «عنوان، صفحهٔ …»",
            checked = draft.readTocEntries,
            onCheckedChange = { draft = draft.copy(readTocEntries = it) },
            switchTestTag = "switch_read_toc",
          )
          ReadingToggleRow(
            title = "خواندن هدر و فوتر",
            subtitle = "خواندن سرصفحه و پاصفحه‌های تکراری صفحات",
            checked = draft.readHeadersAndFooters,
            onCheckedChange = { draft = draft.copy(readHeadersAndFooters = it) },
            switchTestTag = "switch_read_headers_footers",
          )
          ReadingToggleRow(
            title = "خواندن پاورقی و ارجاع",
            subtitle = "شامل پاورقی‌های پایین صفحه و علامت‌های ارجاع",
            checked = draft.readFootnotes,
            onCheckedChange = { draft = draft.copy(readFootnotes = it) },
            switchTestTag = "switch_read_footnotes",
          )
          ReadingToggleRow(
            title = "خواندن شمارهٔ صفحه",
            subtitle = "اعلام شمارهٔ چاپ‌شدهٔ بالای یا پایین صفحه",
            checked = draft.readPageNumbers,
            onCheckedChange = { draft = draft.copy(readPageNumbers = it) },
            switchTestTag = "switch_read_page_numbers",
          )
          ReadingToggleRow(
            title = "خواندن متن لاتین",
            subtitle = "شامل بخش‌های تماماً انگلیسی مانند صفحهٔ حق‌نشر",
            checked = draft.readLatinBlocks,
            onCheckedChange = { draft = draft.copy(readLatinBlocks = it) },
            switchTestTag = "switch_read_latin_blocks",
          )
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val currentPauseSec = PersianDigits.toPersianDigits(
          String.format(java.util.Locale.US, "%.1f", draft.titlePauseDuration.millis / 1000f).replace('.', '٫')
        )
        Text(
          text = "طول مکث پس از تیتر (${currentPauseSec} ثانیه)",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )

        val durations = TitlePauseDuration.entries
        SingleChoiceSegmentedButtonRow(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("title_pause_segmented"),
        ) {
          durations.forEachIndexed { index, option ->
            SegmentedButton(
              selected = draft.titlePauseDuration == option,
              onClick = { draft = draft.copy(titlePauseDuration = option) },
              shape = SegmentedButtonDefaults.itemShape(index = index, count = durations.size),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("pause_${option.name.lowercase()}"),
            ) {
              Text(option.persianLabel)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        KetabkhanSecondaryButton(
          text = "انصراف",
          onClick = onDismiss,
          modifier = Modifier
            .weight(1f)
            .testTag("cancel_reading_settings_button"),
        )
        KetabkhanPrimaryButton(
          text = "ذخیره و اعمال",
          onClick = { onSaveSettings(draft) },
          modifier = Modifier
            .weight(1f)
            .testTag("save_reading_settings_button"),
        )
      }
    }
  }
}

@Composable
private fun ReadingToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  switchTestTag: String,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .minimumInteractiveComponentSize()
      .clickable(
        role = Role.Switch,
        onClick = { onCheckedChange(!checked) },
      )
      .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(switchTestTag),
    )
  }
}
