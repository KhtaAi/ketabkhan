package com.ketabkhan.app.data.pdf

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BookRepository(
  private val context: Context,
  private val importer: PdfImporter = PdfImporter(context),
  private val extractor: PdfTextExtractor = PdfTextExtractor(context),
) {

  private val mutex = Mutex()
  private val pendingImports = mutableMapOf<String, ImportedPdfFile>()

  private val booksRootDir: File
    get() = File(context.filesDir, "books").apply { mkdirs() }

  private val indexFile: File
    get() = File(booksRootDir, "library_index.json")

  private val _books = MutableStateFlow<List<BookMeta>>(emptyList())
  val books: StateFlow<List<BookMeta>> = _books.asStateFlow()

  suspend fun loadLibrary() {
    val loaded = withContext(Dispatchers.IO) {
      readIndexFromDisk()
    }
    _books.value = loaded.sortedByDescending { it.addedAtMillis }
  }

  suspend fun importAndExtractBook(
    uri: Uri,
    onProgress: suspend (ExtractionProgress) -> Unit,
  ): ImportOutcome {
    val imported = importer.importFromUri(uri).getOrElse { error ->
      return ImportOutcome.Failure(error.message ?: "خطا در وارد کردن فایل PDF.")
    }

    return extractAndPersist(imported = imported, password = null, onProgress = onProgress)
  }

  suspend fun retryExtractWithPassword(
    bookId: String,
    password: String,
    onProgress: suspend (ExtractionProgress) -> Unit,
  ): ImportOutcome {
    val pending = mutex.withLock { pendingImports[bookId] }
      ?: return ImportOutcome.Failure("اطلاعات فایل رمزدار یافت نشد. لطفاً دوباره فایل را انتخاب کنید.")

    return extractAndPersist(imported = pending, password = password, onProgress = onProgress)
  }

  suspend fun cancelPendingImport(bookId: String) {
    val pending = mutex.withLock { pendingImports.remove(bookId) }
    if (pending != null) {
      withContext(Dispatchers.IO) {
        pending.bookDir.deleteRecursively()
      }
    }
  }

  private suspend fun extractAndPersist(
    imported: ImportedPdfFile,
    password: String?,
    onProgress: suspend (ExtractionProgress) -> Unit,
  ): ImportOutcome {
    return when (
      val result = extractor.extractBook(
        pdfFile = imported.pdfFile,
        fallbackTitle = imported.fallbackTitle,
        password = password,
        onProgress = onProgress,
      )
    ) {
      is ExtractionResult.PasswordRequired -> {
        mutex.withLock { pendingImports[imported.bookId] = imported }
        ImportOutcome.NeedsPassword(
          bookId = imported.bookId,
          displayTitle = imported.fallbackTitle,
          errorMessage = null,
        )
      }

      is ExtractionResult.InvalidPassword -> {
        mutex.withLock { pendingImports[imported.bookId] = imported }
        ImportOutcome.NeedsPassword(
          bookId = imported.bookId,
          displayTitle = imported.fallbackTitle,
          errorMessage = result.message,
        )
      }

      is ExtractionResult.Error -> {
        mutex.withLock { pendingImports.remove(imported.bookId) }
        withContext(Dispatchers.IO) { imported.bookDir.deleteRecursively() }
        ImportOutcome.Failure(result.message)
      }

      is ExtractionResult.Success -> {
        mutex.withLock { pendingImports.remove(imported.bookId) }
        val meta = BookMeta(
          id = imported.bookId,
          title = result.resolvedTitle,
          fileName = imported.originalFileName,
          sourceUri = imported.sourceUriString,
          pdfFilePath = imported.pdfFile.absolutePath,
          cacheFilePath = imported.cacheFile.absolutePath,
          pageCount = result.pages.size,
          emptyPagesCount = result.emptyPagesCount,
          nonEmptyPagesCount = result.nonEmptyPagesCount,
          totalChars = result.totalChars,
          fileSizeBytes = imported.fileSizeBytes,
          addedAtMillis = System.currentTimeMillis(),
          isPasswordProtected = result.isPasswordProtected,
          qualityReport = result.qualityReport,
        )

        withContext(Dispatchers.IO) {
          writePagesCache(imported.cacheFile, meta.id, result.pages)
          mutex.withLock {
            val current = readIndexFromDisk().filterNot { it.id == meta.id }.toMutableList()
            current.add(0, meta)
            writeIndexToDisk(current)
            _books.value = current.sortedByDescending { it.addedAtMillis }
          }
        }

        ImportOutcome.Success(meta)
      }
    }
  }

  suspend fun getBookContent(bookId: String): BookContent? = withContext(Dispatchers.IO) {
    val meta = _books.value.firstOrNull { it.id == bookId }
      ?: readIndexFromDisk().firstOrNull { it.id == bookId }
      ?: return@withContext null

    val cacheFile = File(meta.cacheFilePath)
    if (!cacheFile.exists()) return@withContext null

    val pages = readPagesCache(cacheFile) ?: return@withContext null
    BookContent(meta = meta, pages = pages)
  }

  suspend fun deleteBook(bookId: String) {
    withContext(Dispatchers.IO) {
      mutex.withLock {
        pendingImports.remove(bookId)
        val current = readIndexFromDisk()
        val target = current.firstOrNull { it.id == bookId }
        val updated = current.filterNot { it.id == bookId }
        writeIndexToDisk(updated)

        if (target != null) {
          val pdfParent = File(target.pdfFilePath).parentFile
          if (pdfParent != null && pdfParent.exists()) {
            pdfParent.deleteRecursively()
          } else {
            File(target.pdfFilePath).delete()
            File(target.cacheFilePath).delete()
          }
        } else {
          File(booksRootDir, bookId).deleteRecursively()
        }

        _books.value = updated.sortedByDescending { it.addedAtMillis }
      }
    }
  }

  private fun readIndexFromDisk(): List<BookMeta> {
    if (!indexFile.exists()) return emptyList()
    return try {
      val root = JSONObject(indexFile.readText(Charsets.UTF_8))
      val array = root.optJSONArray("books") ?: return emptyList()
      buildList(array.length()) {
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          add(bookMetaFromJson(obj))
        }
      }
    } catch (_: Throwable) {
      emptyList()
    }
  }

  private fun writeIndexToDisk(books: List<BookMeta>) {
    val array = JSONArray()
    for (book in books) {
      array.put(bookMetaToJson(book))
    }
    val root = JSONObject().apply {
      put("version", 1)
      put("books", array)
    }
    atomicWriteText(indexFile, root.toString())
  }

  private fun writePagesCache(cacheFile: File, bookId: String, pages: List<ExtractedPage>) {
    val pagesArray = JSONArray()
    for (page in pages) {
      pagesArray.put(
        JSONObject().apply {
          put("pageNumber", page.pageNumber)
          put("cleanedText", page.cleanedText)
          put("isEmpty", page.isEmpty)
          put("charCount", page.charCount)
        }
      )
    }
    val root = JSONObject().apply {
      put("version", 1)
      put("bookId", bookId)
      put("pages", pagesArray)
    }
    atomicWriteText(cacheFile, root.toString())
  }

  private fun readPagesCache(cacheFile: File): List<ExtractedPage>? {
    return try {
      val root = JSONObject(cacheFile.readText(Charsets.UTF_8))
      val array = root.optJSONArray("pages") ?: return null
      buildList(array.length()) {
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          add(
            ExtractedPage(
              pageNumber = obj.getInt("pageNumber"),
              cleanedText = obj.getString("cleanedText"),
              isEmpty = obj.getBoolean("isEmpty"),
              charCount = obj.getInt("charCount"),
            )
          )
        }
      }
    } catch (_: Throwable) {
      null
    }
  }

  private fun atomicWriteText(target: File, content: String) {
    target.parentFile?.mkdirs()
    val tmp = File(target.parentFile, "${target.name}.tmp")
    tmp.writeText(content, Charsets.UTF_8)
    if (target.exists()) {
      target.delete()
    }
    if (!tmp.renameTo(target)) {
      tmp.copyTo(target, overwrite = true)
      tmp.delete()
    }
  }

  private fun bookMetaToJson(meta: BookMeta): JSONObject {
    return JSONObject().apply {
      put("id", meta.id)
      put("title", meta.title)
      put("fileName", meta.fileName)
      put("sourceUri", meta.sourceUri ?: JSONObject.NULL)
      put("pdfFilePath", meta.pdfFilePath)
      put("cacheFilePath", meta.cacheFilePath)
      put("pageCount", meta.pageCount)
      put("emptyPagesCount", meta.emptyPagesCount)
      put("nonEmptyPagesCount", meta.nonEmptyPagesCount)
      put("totalChars", meta.totalChars)
      put("fileSizeBytes", meta.fileSizeBytes)
      put("addedAtMillis", meta.addedAtMillis)
      put("isPasswordProtected", meta.isPasswordProtected)
      put(
        "qualityReport",
        JSONObject().apply {
          put("status", meta.qualityReport.status.name)
          put("warningMessage", meta.qualityReport.warningMessage ?: JSONObject.NULL)
          put("persianLetterRatio", meta.qualityReport.persianLetterRatio.toDouble())
          put("corruptedCharRatio", meta.qualityReport.corruptedCharRatio.toDouble())
        }
      )
    }
  }

  private fun bookMetaFromJson(obj: JSONObject): BookMeta {
    val qualityObj = obj.optJSONObject("qualityReport")
    val statusName = qualityObj?.optString("status", PdfQualityStatus.OK.name) ?: PdfQualityStatus.OK.name
    val status = runCatching { PdfQualityStatus.valueOf(statusName) }.getOrDefault(PdfQualityStatus.OK)
    val warningMessage = qualityObj?.optString("warningMessage")?.takeIf { it.isNotBlank() && it != "null" }

    return BookMeta(
      id = obj.getString("id"),
      title = obj.getString("title"),
      fileName = obj.getString("fileName"),
      sourceUri = obj.optString("sourceUri").takeIf { it.isNotBlank() && it != "null" },
      pdfFilePath = obj.getString("pdfFilePath"),
      cacheFilePath = obj.getString("cacheFilePath"),
      pageCount = obj.getInt("pageCount"),
      emptyPagesCount = obj.optInt("emptyPagesCount", 0),
      nonEmptyPagesCount = obj.optInt("nonEmptyPagesCount", 0),
      totalChars = obj.optInt("totalChars", 0),
      fileSizeBytes = obj.getLong("fileSizeBytes"),
      addedAtMillis = obj.getLong("addedAtMillis"),
      isPasswordProtected = obj.optBoolean("isPasswordProtected", false),
      qualityReport = BookQualityReport(
        status = status,
        warningMessage = warningMessage,
        persianLetterRatio = qualityObj?.optDouble("persianLetterRatio", 1.0)?.toFloat() ?: 1f,
        corruptedCharRatio = qualityObj?.optDouble("corruptedCharRatio", 0.0)?.toFloat() ?: 0f,
      ),
    )
  }
}
