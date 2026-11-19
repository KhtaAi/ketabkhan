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
  private val pendingReextractions = mutableMapOf<String, BookMeta>()

  private val booksRootDir: File
    get() = File(context.filesDir, "books").apply { mkdirs() }

  private val indexFile: File
    get() = File(booksRootDir, "library_index.json")

  private val _books = MutableStateFlow<List<BookMeta>>(emptyList())
  val books: StateFlow<List<BookMeta>> = _books.asStateFlow()

  private data class CachedPagesData(
    val version: Int,
    val pages: List<ExtractedPage>,
  )

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
    val pendingImport = mutex.withLock { pendingImports[bookId] }
    if (pendingImport != null) {
      return extractAndPersist(imported = pendingImport, password = password, onProgress = onProgress)
    }

    val pendingReextract = mutex.withLock { pendingReextractions[bookId] }
    if (pendingReextract != null) {
      return reextractExistingBook(meta = pendingReextract, password = password, onProgress = onProgress)
    }

    return ImportOutcome.Failure("اطلاعات فایل رمزدار یافت نشد. لطفاً دوباره تلاش کنید.")
  }

  suspend fun cancelPendingImport(bookId: String) {
    val pending = mutex.withLock { pendingImports.remove(bookId) }
    mutex.withLock { pendingReextractions.remove(bookId) }
    if (pending != null) {
      withContext(Dispatchers.IO) {
        pending.bookDir.deleteRecursively()
      }
    }
  }

  suspend fun reextractBook(
    bookId: String,
    password: String? = null,
    onProgress: suspend (ExtractionProgress) -> Unit = {},
  ): ImportOutcome {
    val meta = withContext(Dispatchers.IO) {
      _books.value.firstOrNull { it.id == bookId }
        ?: readIndexFromDisk().firstOrNull { it.id == bookId }
    } ?: return ImportOutcome.Failure("کتاب مورد نظر در کتابخانه یافت نشد.")

    return reextractExistingBook(meta = meta, password = password, onProgress = onProgress)
  }

  fun isCacheVersionCurrent(meta: BookMeta): Boolean = !meta.needsReextraction

  private suspend fun reextractExistingBook(
    meta: BookMeta,
    password: String?,
    onProgress: suspend (ExtractionProgress) -> Unit,
  ): ImportOutcome {
    val pdfFile = File(meta.pdfFilePath)
    if (!pdfFile.exists() || !pdfFile.isFile) {
      return ImportOutcome.Failure("فایل PDF این کتاب در حافظهٔ داخلی پیدا نشد.")
    }

    val fallbackTitle = meta.fileName.substringBeforeLast('.').ifBlank { meta.title }
    return when (
      val result = extractor.extractBook(
        pdfFile = pdfFile,
        fallbackTitle = fallbackTitle,
        password = password,
        onProgress = onProgress,
      )
    ) {
      is ExtractionResult.PasswordRequired -> {
        mutex.withLock { pendingReextractions[meta.id] = meta }
        ImportOutcome.NeedsPassword(
          bookId = meta.id,
          displayTitle = meta.title,
          errorMessage = null,
        )
      }

      is ExtractionResult.InvalidPassword -> {
        mutex.withLock { pendingReextractions[meta.id] = meta }
        ImportOutcome.NeedsPassword(
          bookId = meta.id,
          displayTitle = meta.title,
          errorMessage = result.message,
        )
      }

      is ExtractionResult.Error -> {
        mutex.withLock { pendingReextractions.remove(meta.id) }
        ImportOutcome.Failure(result.message)
      }

      is ExtractionResult.Success -> {
        mutex.withLock { pendingReextractions.remove(meta.id) }
        val updatedMeta = meta.copy(
          title = result.resolvedTitle,
          pageCount = result.pages.size,
          emptyPagesCount = result.emptyPagesCount,
          nonEmptyPagesCount = result.nonEmptyPagesCount,
          totalChars = result.totalChars,
          isPasswordProtected = result.isPasswordProtected,
          qualityReport = result.qualityReport,
          cacheSchemaVersion = CURRENT_EXTRACTION_SCHEMA_VERSION,
        )

        withContext(Dispatchers.IO) {
          writePagesCache(File(updatedMeta.cacheFilePath), updatedMeta.id, result.pages)
          mutex.withLock {
            val current = readIndexFromDisk().filterNot { it.id == updatedMeta.id }.toMutableList()
            current.add(0, updatedMeta)
            writeIndexToDisk(current)
            _books.value = current.sortedByDescending { it.addedAtMillis }
          }
        }

        ImportOutcome.Success(updatedMeta)
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
          cacheSchemaVersion = CURRENT_EXTRACTION_SCHEMA_VERSION,
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
    val cached = if (cacheFile.exists()) readPagesCache(cacheFile) else null

    if (cached == null) {
      val pdfExists = File(meta.pdfFilePath).exists()
      return@withContext if (pdfExists) {
        BookContent(
          meta = meta.copy(cacheSchemaVersion = 1),
          pages = emptyList(),
          isCacheOutdated = true,
        )
      } else {
        null
      }
    }

    val effectiveVersion = minOf(meta.cacheSchemaVersion, cached.version)
    val isOutdated = effectiveVersion < CURRENT_EXTRACTION_SCHEMA_VERSION
    val effectiveMeta = meta.copy(cacheSchemaVersion = effectiveVersion)
    BookContent(
      meta = effectiveMeta,
      pages = cached.pages,
      isCacheOutdated = isOutdated,
    )
  }

  suspend fun deleteBook(bookId: String) {
    withContext(Dispatchers.IO) {
      mutex.withLock {
        pendingImports.remove(bookId)
        pendingReextractions.remove(bookId)
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
      put("version", CURRENT_EXTRACTION_SCHEMA_VERSION)
      put("books", array)
    }
    atomicWriteText(indexFile, root.toString())
  }

  private fun writePagesCache(cacheFile: File, bookId: String, pages: List<ExtractedPage>) {
    val pagesArray = JSONArray()
    for (page in pages) {
      val blocksArray = JSONArray()
      for (block in page.blocks) {
        blocksArray.put(blockToJson(block))
      }
      pagesArray.put(
        JSONObject().apply {
          put("pageNumber", page.pageNumber)
          put("cleanedText", page.cleanedText)
          put("rawText", page.rawText)
          put("isEmpty", page.isEmpty)
          put("charCount", page.charCount)
          put("blocks", blocksArray)
        }
      )
    }
    val root = JSONObject().apply {
      put("version", CURRENT_EXTRACTION_SCHEMA_VERSION)
      put("bookId", bookId)
      put("pages", pagesArray)
    }
    atomicWriteText(cacheFile, root.toString())
  }

  private fun readPagesCache(cacheFile: File): CachedPagesData? {
    return try {
      val root = JSONObject(cacheFile.readText(Charsets.UTF_8))
      val version = root.optInt("version", 1)
      val array = root.optJSONArray("pages") ?: return null
      val pages = buildList(array.length()) {
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          val pageNumber = obj.getInt("pageNumber")
          val blocksArray = obj.optJSONArray("blocks")
          val blocks = if (blocksArray != null) {
            buildList(blocksArray.length()) {
              for (j in 0 until blocksArray.length()) {
                add(blockFromJson(blocksArray.getJSONObject(j), pageNumber, j))
              }
            }
          } else {
            emptyList()
          }
          add(
            ExtractedPage(
              pageNumber = pageNumber,
              cleanedText = obj.getString("cleanedText"),
              isEmpty = obj.getBoolean("isEmpty"),
              charCount = obj.getInt("charCount"),
              rawText = obj.optString("rawText", ""),
              blocks = blocks,
            )
          )
        }
      }
      CachedPagesData(version = version, pages = pages)
    } catch (_: Throwable) {
      null
    }
  }

  private fun blockToJson(block: DocumentBlock): JSONObject {
    return JSONObject().apply {
      put("id", block.id)
      put("type", block.type.name)
      put("text", block.text)
      put("rawText", block.rawText)
      put("title", block.title ?: JSONObject.NULL)
      put("referencePageNumber", block.referencePageNumber ?: JSONObject.NULL)
      put("fontSizeSp", block.fontSizeSp.toDouble())
      put("isBold", block.isBold)
      put("zone", block.zone.name)
      put("baselineY", block.baselineY.toDouble())
      put("leftX", block.leftX.toDouble())
      put("rightX", block.rightX.toDouble())
      put("lineCount", block.lineCount)
    }
  }

  private fun blockFromJson(obj: JSONObject, pageNumber: Int, index: Int): DocumentBlock {
    val typeName = obj.optString("type", DocumentBlockType.BODY_PARAGRAPH.name)
    val type = runCatching { DocumentBlockType.valueOf(typeName) }.getOrDefault(DocumentBlockType.BODY_PARAGRAPH)
    val zoneName = obj.optString("zone", PageZone.MIDDLE.name)
    val zone = runCatching { PageZone.valueOf(zoneName) }.getOrDefault(PageZone.MIDDLE)
    val title = obj.optString("title").takeIf { it.isNotBlank() && it != "null" }
    val refPage = if (obj.has("referencePageNumber") && !obj.isNull("referencePageNumber")) {
      obj.optInt("referencePageNumber").takeIf { it > 0 }
    } else {
      null
    }
    val text = obj.optString("text", "")
    return DocumentBlock(
      id = obj.optString("id", "p${pageNumber}_b${index}"),
      type = type,
      text = text,
      rawText = obj.optString("rawText", text),
      title = title,
      referencePageNumber = refPage,
      fontSizeSp = obj.optDouble("fontSizeSp", 0.0).toFloat(),
      isBold = obj.optBoolean("isBold", false),
      zone = zone,
      baselineY = obj.optDouble("baselineY", 0.0).toFloat(),
      leftX = obj.optDouble("leftX", 0.0).toFloat(),
      rightX = obj.optDouble("rightX", 0.0).toFloat(),
      lineCount = obj.optInt("lineCount", 1),
    )
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
      put("cacheSchemaVersion", meta.cacheSchemaVersion)
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
      cacheSchemaVersion = obj.optInt("cacheSchemaVersion", 1),
      qualityReport = BookQualityReport(
        status = status,
        warningMessage = warningMessage,
        persianLetterRatio = qualityObj?.optDouble("persianLetterRatio", 1.0)?.toFloat() ?: 1f,
        corruptedCharRatio = qualityObj?.optDouble("corruptedCharRatio", 0.0)?.toFloat() ?: 0f,
      ),
    )
  }
}
