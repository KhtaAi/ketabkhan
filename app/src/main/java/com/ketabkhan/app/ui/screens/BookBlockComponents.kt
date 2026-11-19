package com.ketabkhan.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ketabkhan.app.data.pdf.DocumentBlock
import com.ketabkhan.app.data.pdf.DocumentBlockType
import com.ketabkhan.app.data.pdf.ExtractedPage
import com.ketabkhan.app.domain.text.PersianDigits

@Composable
internal fun StructuredPageBlocksColumn(
  page: ExtractedPage,
  modifier: Modifier = Modifier,
) {
  val combinedReadableText = remember(page.blocks, page.cleanedText) {
    page.blocks
      .filter { it.type != DocumentBlockType.EMPTY && it.text.isNotBlank() }
      .joinToString("\n") { it.text }
      .ifBlank { page.cleanedText }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .semantics { text = AnnotatedString(combinedReadableText) }
      .testTag("page_text_${page.pageNumber}"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    page.blocks.forEach { block ->
      if (block.type != DocumentBlockType.EMPTY) {
        DocumentBlockItem(block = block)
      }
    }
  }
}

@Composable
internal fun DocumentBlockItem(
  block: DocumentBlock,
) {
  when (block.type) {
    DocumentBlockType.PAGE_TITLE -> {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("block_${block.id}"),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          BlockRoleBadge(type = block.type)
          Text(
            text = block.text,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }

    DocumentBlockType.SUBTITLE -> {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("block_${block.id}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        BlockRoleBadge(type = block.type)
        Text(
          text = block.text,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }

    DocumentBlockType.TOC_ENTRY -> {
      val entryTitle = block.title ?: block.text
      val refPage = block.referencePageNumber
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("block_${block.id}"),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            BlockRoleBadge(type = DocumentBlockType.TOC_ENTRY)
            Text(
              text = entryTitle,
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
          }
          if (refPage != null) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
              Text(
                text = "صفحهٔ ${PersianDigits.formatInt(refPage)}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
        }
      }
    }

    DocumentBlockType.CENTERED_SHORT_LINE -> {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("block_${block.id}"),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          BlockRoleBadge(type = block.type)
          Text(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }

    DocumentBlockType.BODY_PARAGRAPH -> {
      Text(
        text = block.text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("block_${block.id}"),
      )
    }

    DocumentBlockType.HEADER,
    DocumentBlockType.FOOTER,
    DocumentBlockType.PAGE_NUMBER,
    DocumentBlockType.FOOTNOTE,
    DocumentBlockType.REFERENCE_MARK -> {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("block_${block.id}"),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 7.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.Top,
        ) {
          BlockRoleBadge(type = block.type)
          Text(
            text = block.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    DocumentBlockType.EMPTY -> Unit
  }
}

@Composable
internal fun BlockRoleBadge(
  type: DocumentBlockType,
  modifier: Modifier = Modifier,
) {
  val (containerColor, contentColor) = when (type) {
    DocumentBlockType.PAGE_TITLE ->
      MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    DocumentBlockType.SUBTITLE ->
      MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    DocumentBlockType.TOC_ENTRY ->
      MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    DocumentBlockType.CENTERED_SHORT_LINE ->
      MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    DocumentBlockType.BODY_PARAGRAPH ->
      MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    DocumentBlockType.HEADER,
    DocumentBlockType.FOOTER,
    DocumentBlockType.PAGE_NUMBER,
    DocumentBlockType.FOOTNOTE,
    DocumentBlockType.REFERENCE_MARK,
    DocumentBlockType.EMPTY ->
      MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f) to MaterialTheme.colorScheme.onSurfaceVariant
  }

  Surface(
    shape = RoundedCornerShape(6.dp),
    color = containerColor,
    modifier = modifier,
  ) {
    Text(
      text = type.persianLabel,
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
      color = contentColor,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
    )
  }
}
