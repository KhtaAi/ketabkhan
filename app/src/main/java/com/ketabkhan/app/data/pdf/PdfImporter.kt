package com.ketabkhan.app.data.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ImportedPdfFile(
  val bookId: String,
  val bookDir: File,
  val pdfFile: File,
  val cacheFile: File,
  val originalFileName: String,
  val fallbackTitle: String,
  val fileSizeBytes: Long,
  val sourceUriString: String,
)

class PdfImporter(private val context: Context) {

  private val booksRootDir: File
    get() = File(context.filesDir, "books").apply { mkdirs() }

  suspend fun importFromUri(uri: Uri): Result<ImportedPdfFile> = withContext(Dispatchers.IO) {
    try {
      takePersistableReadPermission(uri)

      val (displayName, queriedSize) = queryFileMetadata(uri)
      val safeFileName = sanitizeFileName(displayName)
      val fallbackTitle = safeFileName
        .removeSuffix(".pdf")
        .removeSuffix(".PDF")
        .trim()
        .ifEmpty { "کتاب بدون عنوان" }

      val bookId = UUID.randomUUID().toString()
      val bookDir = File(booksRootDir, bookId).apply { mkdirs() }
      val pdfFile = File(bookDir, "book.pdf")
      val cacheFile = File(bookDir, "pages.json")

      var copiedBytes = 0L
      val headerBuffer = ByteArray(5)
      var headerRead = 0

      val inputStream = context.contentResolver.openInputStream(uri)
        ?: return@withContext Result.failure(
          IllegalStateException("دسترسی به فایل انتخاب‌شده امکان‌پذیر نیست.")
        )

      inputStream.use { input ->
        FileOutputStream(pdfFile).use { output ->
          val buffer = ByteArray(32 * 1024)
          while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read <= 0) break

            if (headerRead < headerBuffer.size) {
              val toCopy = minOf(read, headerBuffer.size - headerRead)
              System.arraycopy(buffer, 0, headerBuffer, headerRead, toCopy)
              headerRead += toCopy
            }

            output.write(buffer, 0, read)
            copiedBytes += read
          }
          output.flush()
        }
      }

      if (copiedBytes == 0L) {
        bookDir.deleteRecursively()
        return@withContext Result.failure(
          IllegalArgumentException("فایل انتخاب‌شده خالی است.")
        )
      }

      val headerStr = String(headerBuffer, 0, headerRead, Charsets.US_ASCII)
      if (!headerStr.startsWith("%PDF-")) {
        bookDir.deleteRecursively()
        return@withContext Result.failure(
          IllegalArgumentException("فایل انتخاب‌شده یک سند PDF معتبر نیست.")
        )
      }

      val finalSize = if (copiedBytes > 0L) copiedBytes else queriedSize
      Result.success(
        ImportedPdfFile(
          bookId = bookId,
          bookDir = bookDir,
          pdfFile = pdfFile,
          cacheFile = cacheFile,
          originalFileName = safeFileName,
          fallbackTitle = fallbackTitle,
          fileSizeBytes = finalSize,
          sourceUriString = uri.toString(),
        )
      )
    } catch (t: Throwable) {
      Result.failure(
        IllegalStateException("خطا در وارد کردن فایل PDF: ${t.localizedMessage ?: "خطای ناشناخته"}", t)
      )
    }
  }

  private fun takePersistableReadPermission(uri: Uri) {
    try {
      context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
      )
    } catch (_: SecurityException) {
      // Some SAF document providers do not grant persistable permissions; local copy handles durability.
    } catch (_: UnsupportedOperationException) {
      // Ignored for providers without persistable grant support.
    }
  }

  private fun queryFileMetadata(uri: Uri): Pair<String, Long> {
    var displayName: String? = null
    var fileSize = 0L
    context.contentResolver.query(
      uri,
      arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
      null,
      null,
      null,
    )?.use { cursor ->
      if (cursor.moveToFirst()) {
        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIdx >= 0) {
          displayName = cursor.getString(nameIdx)
        }
        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
          fileSize = cursor.getLong(sizeIdx)
        }
      }
    }
    val resolvedName = displayName?.takeIf { it.isNotBlank() }
      ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
      ?: "book.pdf"
    return resolvedName to fileSize
  }

  private fun sanitizeFileName(name: String): String {
    return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
  }
}
