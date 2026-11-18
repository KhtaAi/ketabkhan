package com.ketabkhan.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ketabkhan.app.data.pdf.ExtractedPage
import com.ketabkhan.app.domain.text.PersianDigits
import com.ketabkhan.app.ui.components.KetabkhanPrimaryButton
import com.ketabkhan.app.ui.components.KetabkhanSecondaryButton
import com.ketabkhan.app.ui.components.WarningBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangePickerSheet(
  pages: List<ExtractedPage>,
  initialStartPage: Int,
  initialEndPage: Int,
  onComputePreview: suspend (List<ExtractedPage>, Int, Int) -> PageRangeSummary,
  onConfirmRange: (Int, Int) -> Unit,
  onDismiss: () -> Unit,
) {
  val totalPages = pages.size.coerceAtLeast(1)
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var startInput by rememberSaveable {
    mutableStateOf(PersianDigits.formatInt(initialStartPage))
  }
  var endInput by rememberSaveable {
    mutableStateOf(PersianDigits.formatInt(initialEndPage))
  }

  val parsedStart = PersianDigits.parseFlexibleInt(startInput)
  val parsedEnd = PersianDigits.parseFlexibleInt(endInput)

  val startError = when {
    startInput.isBlank() -> "شمارهٔ صفحهٔ شروع را وارد کنید."
    parsedStart == null -> "لطفاً یک عدد صحیح وارد کنید."
    parsedStart < 1 || parsedStart > totalPages ->
      "عدد باید بین ۱ تا ${PersianDigits.formatInt(totalPages)} باشد."
    else -> null
  }

  val endError = when {
    endInput.isBlank() -> "شمارهٔ صفحهٔ پایان را وارد کنید."
    parsedEnd == null -> "لطفاً یک عدد صحیح وارد کنید."
    parsedEnd < 1 || parsedEnd > totalPages ->
      "عدد باید بین ۱ تا ${PersianDigits.formatInt(totalPages)} باشد."
    parsedStart != null && startError == null && parsedEnd < parsedStart ->
      "صفحهٔ پایان باید بزرگ‌تر یا مساوی صفحهٔ شروع باشد."
    else -> null
  }

  val isValidRange = startError == null && endError == null && parsedStart != null && parsedEnd != null

  var liveSummary by remember { mutableStateOf<PageRangeSummary?>(null) }
  LaunchedEffect(parsedStart, parsedEnd, isValidRange) {
    if (isValidRange && parsedStart != null && parsedEnd != null) {
      liveSummary = onComputePreview(pages, parsedStart, parsedEnd)
    } else {
      liveSummary = null
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .imePadding()
        .navigationBarsPadding()
        .padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "انتخاب بازهٔ صفحات",
          style = MaterialTheme.typography.headlineSmall,
        )
        Text(
          text = "تعداد کل صفحات کتاب: ${PersianDigits.formatInt(totalPages)} صفحه (ورودی با ارقام فارسی یا لاتین پذیرفته می‌شود)",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = startInput,
          onValueChange = { startInput = it },
          label = { Text("از صفحه") },
          singleLine = true,
          isError = startError != null,
          supportingText = startError?.let { msg ->
            { Text(msg, color = MaterialTheme.colorScheme.error) }
          },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier
            .weight(1f)
            .testTag("range_start_input"),
        )

        OutlinedTextField(
          value = endInput,
          onValueChange = { endInput = it },
          label = { Text("تا صفحه") },
          singleLine = true,
          isError = endError != null,
          supportingText = endError?.let { msg ->
            { Text(msg, color = MaterialTheme.colorScheme.error) }
          },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier
            .weight(1f)
            .testTag("range_end_input"),
        )
      }

      liveSummary?.let { summary ->
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          ),
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("range_preview_summary_card"),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "صفحات انتخاب‌شده: ${PersianDigits.formatInt(summary.selectedPageCount)}",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "حدود ${PersianDigits.formatInt(summary.sentences.size)} جمله",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.secondary,
              )
            }

            if (summary.emptyPageCount > 0) {
              WarningBanner(
                title = "صفحهٔ بدون متن در بازه",
                message = "در این بازه ${PersianDigits.formatInt(summary.emptyPageCount)} صفحه بدون متن (تصویری یا خالی) وجود دارد که در شمارش جمله‌ها نادیده گرفته می‌شوند.",
              )
            }

            Text(
              text = "پیش‌نمایش متن بازه",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )

            if (summary.previewText.isBlank()) {
              Text(
                text = "در این بازه هیچ متن قابل استخراجی وجود ندارد.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              Text(
                text = summary.previewText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("range_preview_text"),
              )
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
            .testTag("cancel_range_button"),
        )
        KetabkhanPrimaryButton(
          text = "تأیید بازه",
          onClick = {
            if (parsedStart != null && parsedEnd != null) {
              onConfirmRange(parsedStart, parsedEnd)
            }
          },
          enabled = isValidRange,
          modifier = Modifier
            .weight(1f)
            .testTag("confirm_range_button"),
        )
      }
    }
  }
}
