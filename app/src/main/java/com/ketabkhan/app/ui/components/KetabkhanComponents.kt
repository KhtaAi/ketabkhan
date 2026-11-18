package com.ketabkhan.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ketabkhan.app.data.pdf.ExtractionProgress
import com.ketabkhan.app.domain.text.PersianDigits
import com.ketabkhan.app.ui.theme.AmberWarningContainer
import com.ketabkhan.app.ui.theme.AmberWarningContainerDark
import com.ketabkhan.app.ui.theme.OnAmberWarningContainer
import com.ketabkhan.app.ui.theme.OnAmberWarningContainerDark

@Composable
fun KetabkhanPrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier
      .height(50.dp)
      .minimumInteractiveComponentSize(),
    shape = RoundedCornerShape(14.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
    ),
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
      maxLines = 1,
    )
  }
}

@Composable
fun KetabkhanSecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
) {
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier
      .height(50.dp)
      .minimumInteractiveComponentSize(),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(
      width = 1.dp,
      color = if (enabled) {
        MaterialTheme.colorScheme.outline
      } else {
        MaterialTheme.colorScheme.outlineVariant
      },
    ),
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
      maxLines = 1,
    )
  }
}

@Composable
fun EmptyLibraryState(
  onAddBookClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .widthIn(max = 520.dp)
      .padding(horizontal = 24.dp, vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.primaryContainer,
      modifier = Modifier.size(96.dp),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.MenuBook,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
          .padding(24.dp)
          .size(48.dp),
      )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = "کتابخانهٔ شما هنوز خالی است",
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "یک کتاب PDF متنی فارسی اضافه کنید تا متن آن صفحه‌به‌صفحه استخراج، پاکسازی و آمادهٔ انتخاب بازه شود.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(20.dp))

    Card(
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
      ),
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "راهنمای سریع",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
        )
        GuideRow(number = "۱", text = "دکمهٔ «افزودن کتاب» را بزنید و یک فایل PDF متنی انتخاب کنید.")
        GuideRow(number = "۲", text = "متن فارسی به‌صورت خودکار از خط‌شکستگی و سرصفحه پاکسازی می‌شود.")
        GuideRow(number = "۳", text = "بازهٔ «از صفحهٔ X تا Y» را برگزینید و پیش‌نمایش جمله‌ها را ببینید.")
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    KetabkhanPrimaryButton(
      text = "افزودن کتاب",
      onClick = onAddBookClick,
      icon = Icons.Default.Add,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("add_book_empty_button"),
    )
  }
}

@Composable
private fun GuideRow(number: String, text: String) {
  Row(
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(8.dp),
      color = MaterialTheme.colorScheme.secondaryContainer,
      modifier = Modifier.size(24.dp),
    ) {
      Text(
        text = number,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 3.dp),
      )
    }
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
fun ExtractionProgressCard(
  progress: ExtractionProgress,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("extraction_progress_card"),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
    ),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "در حال استخراج و پاکسازی متن: ${progress.bookTitle}",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      LinearProgressIndicator(
        progress = { progress.fraction },
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surface,
      )
      val statusText = if (progress.totalPages > 0) {
        "صفحهٔ ${PersianDigits.formatInt(progress.currentPage)} از ${PersianDigits.formatInt(progress.totalPages)}"
      } else {
        "در حال آماده‌سازی فایل PDF..."
      }
      Text(
        text = statusText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
  }
}

@Composable
fun WarningBanner(
  title: String,
  message: String,
  modifier: Modifier = Modifier,
) {
  val isDark = isSystemInDarkTheme()
  val bg = if (isDark) AmberWarningContainerDark else AmberWarningContainer
  val fg = if (isDark) OnAmberWarningContainerDark else OnAmberWarningContainer

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("warning_banner"),
    shape = RoundedCornerShape(16.dp),
    color = bg,
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Icon(
        imageVector = Icons.Default.WarningAmber,
        contentDescription = "هشدار",
        tint = fg,
        modifier = Modifier.size(24.dp),
      )
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          color = fg,
        )
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = fg,
        )
      }
    }
  }
}

@Composable
fun OutdatedCacheBanner(
  onReextractClick: () -> Unit,
  isReextracting: Boolean,
  modifier: Modifier = Modifier,
) {
  val isDark = isSystemInDarkTheme()
  val bg = if (isDark) AmberWarningContainerDark else AmberWarningContainer
  val fg = if (isDark) OnAmberWarningContainerDark else OnAmberWarningContainer

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("outdated_cache_banner"),
    shape = RoundedCornerShape(16.dp),
    color = bg,
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
      ) {
        Icon(
          imageVector = Icons.Default.WarningAmber,
          contentDescription = "نیاز به استخراج دوباره",
          tint = fg,
          modifier = Modifier.size(24.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "نیاز به استخراج دوباره",
            style = MaterialTheme.typography.titleSmall,
            color = fg,
          )
          Text(
            text = "این کتاب با نسخهٔ قدیمی‌تر استخراج شده است. برای اعمال اصلاحات نیم‌فاصله، ترتیب سطرها و ذخیرهٔ متن خام، دوباره استخراج کنید.",
            style = MaterialTheme.typography.bodySmall,
            color = fg,
          )
        }
      }
      OutlinedButton(
        onClick = onReextractClick,
        enabled = !isReextracting,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.6f)),
        modifier = Modifier
          .align(Alignment.End)
          .minimumInteractiveComponentSize()
          .testTag("reextract_banner_button"),
      ) {
        Text(
          text = if (isReextracting) "در حال استخراج دوباره..." else "استخراج دوباره",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          color = fg,
        )
      }
    }
  }
}

@Composable
fun ErrorBanner(
  message: String,
  onDismiss: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("error_banner"),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.errorContainer,
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Default.ErrorOutline,
        contentDescription = "خطا",
        tint = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.size(24.dp),
      )
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.weight(1f),
      )
      if (onDismiss != null) {
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Text("بستن", style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPasswordSheet(
  bookTitle: String,
  errorMessage: String?,
  onSubmitPassword: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var password by rememberSaveable { mutableStateOf("") }
  var passwordVisible by rememberSaveable { mutableStateOf(false) }

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
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.secondaryContainer,
          modifier = Modifier.size(44.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(10.dp),
          )
        }
        Column {
          Text(
            text = "فایل PDF رمزدار است",
            style = MaterialTheme.typography.titleLarge,
          )
          Text(
            text = bookTitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Text(
        text = "برای باز کردن و استخراج متن این کتاب، رمز عبور فایل PDF را وارد کنید.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("رمز عبور PDF") },
        singleLine = true,
        isError = errorMessage != null,
        supportingText = if (errorMessage != null) {
          { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
        } else {
          null
        },
        visualTransformation = if (passwordVisible) {
          VisualTransformation.None
        } else {
          PasswordVisualTransformation()
        },
        trailingIcon = {
          IconButton(
            onClick = { passwordVisible = !passwordVisible },
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
              contentDescription = if (passwordVisible) "پنهان کردن رمز" else "نمایش رمز",
            )
          }
        },
        keyboardOptions = KeyboardOptions(
          keyboardType = KeyboardType.Password,
          imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
          onDone = {
            if (password.isNotEmpty()) {
              onSubmitPassword(password)
            }
          }
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("password_input"),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        KetabkhanSecondaryButton(
          text = "انصراف",
          onClick = onDismiss,
          modifier = Modifier
            .weight(1f)
            .testTag("cancel_password_button"),
        )
        KetabkhanPrimaryButton(
          text = "باز کردن کتاب",
          onClick = { onSubmitPassword(password) },
          enabled = password.isNotEmpty(),
          modifier = Modifier
            .weight(1f)
            .testTag("submit_password_button"),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteBookConfirmSheet(
  bookTitle: String,
  onConfirmDelete: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
      Text(
        text = "حذف کتاب از کتابخانه",
        style = MaterialTheme.typography.titleLarge,
      )
      Text(
        text = "آیا مطمئن هستید که می‌خواهید کتاب «$bookTitle» را حذف کنید؟ فایل PDF کپی‌شده و کش متن استخراج‌شده هر دو پاک می‌شوند.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        KetabkhanSecondaryButton(
          text = "انصراف",
          onClick = onDismiss,
          modifier = Modifier
            .weight(1f)
            .testTag("cancel_delete_button"),
        )
        Button(
          onClick = onConfirmDelete,
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .minimumInteractiveComponentSize()
            .testTag("confirm_delete_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        ) {
          Text(
            text = "حذف کتاب",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          )
        }
      }
    }
  }
}
