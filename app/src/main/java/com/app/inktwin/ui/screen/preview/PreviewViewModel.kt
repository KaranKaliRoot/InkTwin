package com.app.inktwin.ui.screen.preview

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.inktwin.core.glyph.Glyph
import com.app.inktwin.core.note.NoteDraft
import com.app.inktwin.core.note.PageSettings
import com.app.inktwin.core.render.HandwritingPageRenderer
import com.app.inktwin.core.render.PageLayout
import com.app.inktwin.data.repository.GlyphRepository
import com.app.inktwin.data.repository.HandwritingProfileRepository
import com.app.inktwin.data.repository.NoteDraftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import javax.inject.Inject

enum class ExportFormat { PNG, PDF }
enum class ExportScope { CURRENT_PAGE, ENTIRE_DOCUMENT }

data class PreviewUiState(
    val draft: NoteDraft? = null,
    val glyphs: Map<String, Glyph> = emptyMap(),
    val pages: List<PageLayout> = emptyList(),
    val pageBitmaps: List<Bitmap> = emptyList(),
    val currentPageIndex: Int = 0,
    val pageSettings: PageSettings = PageSettings(),
    val fontSize: Float = 36f,
    val showRuledLines: Boolean = true,
    val isExporting: Boolean = false,
    val exportStatus: String? = null
)

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val noteDraftRepository: NoteDraftRepository,
    private val glyphRepository: GlyphRepository,
    private val handwritingProfileRepository: HandwritingProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()

    val currentPageIndex: StateFlow<Int> = _uiState.map { state -> state.currentPageIndex }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pageBitmaps: StateFlow<List<Bitmap>> = _uiState.map { state -> state.pageBitmaps }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalPages: Int
        get() = _uiState.value.pages.size

    init {
        observeProfileAndGlyphs()
        loadDraft()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeProfileAndGlyphs() {
        viewModelScope.launch {
            handwritingProfileRepository.observeActiveProfile()
                .flatMapLatest { profile ->
                    if (profile != null) {
                        glyphRepository.observeGlyphs(profile.id)
                    } else kotlinx.coroutines.flow.flowOf(emptyList())
                }
                .collect { glyphs ->
                    val glyphMap = glyphs
                        .filter { it.variants.isNotEmpty() && !it.isFallback }
                        .associateBy { it.character }
                    _uiState.update { it.copy(glyphs = glyphMap) }
                    _uiState.value.draft?.let { draft ->
                        renderPages(draft, glyphMap)
                    }
                }
        }
    }

    private fun loadDraft() {
        viewModelScope.launch {
            noteDraftRepository.observeDrafts(null)
                .firstOrNull()
                ?.firstOrNull()
                ?.let { draft ->
                    _uiState.update { it.copy(draft = draft) }
                    // Always render draft even with 0 custom glyphs (uses fallback)
                    renderPages(draft, _uiState.value.glyphs)
                }
        }
    }

    fun setPageSettings(settings: PageSettings) {
        _uiState.update { it.copy(pageSettings = settings) }
        val draft = _uiState.value.draft
        if (draft != null) {
            viewModelScope.launch {
                renderPages(draft, _uiState.value.glyphs)
            }
        }
    }

    fun setFontSize(size: Float) {
        _uiState.update { it.copy(fontSize = size) }
        val draft = _uiState.value.draft
        if (draft != null) {
            viewModelScope.launch {
                renderPages(draft, _uiState.value.glyphs)
            }
        }
    }

    fun setRuledLines(enabled: Boolean) {
        _uiState.update { it.copy(showRuledLines = enabled) }
        val draft = _uiState.value.draft
        if (draft != null) {
            viewModelScope.launch {
                renderPages(draft, _uiState.value.glyphs)
            }
        }
    }

    fun navigateToPage(index: Int) {
        val maxIndex = _uiState.value.pages.size - 1
        _uiState.update {
            it.copy(currentPageIndex = index.coerceIn(0, maxIndex))
        }
    }

    private suspend fun renderPages(draft: NoteDraft, glyphs: Map<String, Glyph>) {
        val pages = HandwritingPageRenderer.paginateText(
            draft = draft,
            pageSettings = _uiState.value.pageSettings,
            fontSize = _uiState.value.fontSize,
            glyphsByChar = glyphs
        )

        _uiState.update { it.copy(pages = pages) }

        val bitmaps = pages.map { page ->
            HandwritingPageRenderer.renderPageToBitmap(
                page = page,
                draft = draft,
                glyphsByChar = glyphs,
                pageSettings = _uiState.value.pageSettings,
                fontSize = _uiState.value.fontSize,
                showRedMargins = _uiState.value.pageSettings.showRedMargins,
                showPageNumbers = _uiState.value.pageSettings.showPageNumbers,
                showRuledLines = _uiState.value.showRuledLines
            )
        }

        _uiState.update { it.copy(pageBitmaps = bitmaps) }
    }

    fun exportDocument(context: Context, format: ExportFormat, scope: ExportScope) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportStatus = null) }

            try {
                val state = _uiState.value
                val draft = state.draft ?: return@launch

                val pagesToExport = when (scope) {
                    ExportScope.CURRENT_PAGE -> listOf(state.pages[state.currentPageIndex])
                    ExportScope.ENTIRE_DOCUMENT -> state.pages
                }

                val bitmapsToExport = when (scope) {
                    ExportScope.CURRENT_PAGE -> listOf(state.pageBitmaps[state.currentPageIndex])
                    ExportScope.ENTIRE_DOCUMENT -> state.pageBitmaps
                }

                when (format) {
                    ExportFormat.PNG -> exportAsPng(context, bitmapsToExport, draft.title)
                    ExportFormat.PDF -> exportAsPdf(context, pagesToExport, draft, state)
                }

                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportStatus = "Export successful! Check your Downloads folder."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportStatus = "Export failed: ${e.message}"
                    )
                }
            }
        }
    }

    private fun exportAsPng(context: Context, bitmaps: List<Bitmap>, title: String) {
        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_")

        bitmaps.forEachIndexed { index, bitmap ->
            val filename = if (bitmaps.size == 1) {
                "${sanitizedTitle}.png"
            } else {
                "${sanitizedTitle}_page${index + 1}.png"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/InkTwin")
                }
                val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { mediaUri ->
                    resolver.openOutputStream(mediaUri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                }
            } else {
                // Fallback for older Android
                val downloadsDir = context.getExternalFilesDir(null)
                val inktwinDir = File(downloadsDir, "InkTwin").apply { mkdirs() }
                val file = File(inktwinDir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        }
    }

    private fun exportAsPdf(context: Context, pages: List<PageLayout>, draft: NoteDraft, state: PreviewUiState) {
        val document = PdfDocument()
        val (pageWidth, pageHeight) = HandwritingPageRenderer.getPageSize(state.pageSettings.pageSize)

        for ((index, page) in pages.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val pdfPage = document.startPage(pageInfo)

            val bitmap = HandwritingPageRenderer.renderPageToBitmap(
                page = page,
                draft = draft,
                glyphsByChar = state.glyphs,
                pageSettings = state.pageSettings,
                fontSize = state.fontSize,
                showRedMargins = state.pageSettings.showRedMargins,
                showPageNumbers = state.pageSettings.showPageNumbers,
                showRuledLines = state.showRuledLines
            )

            pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
            document.finishPage(pdfPage)
            bitmap.recycle()
        }

        val sanitizedTitle = draft.title.replace(Regex("[^a-zA-Z0-9]"), "_")
        val filename = "${sanitizedTitle}.pdf"

        var exportSuccess = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore for Android 10+
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/InkTwin")
            }
            val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    document.writeTo(outputStream)
                    exportSuccess = true
                }
            }
        }
        if (!exportSuccess) {
            // Fallback: use app-specific Downloads folder (always works)
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val inktwinDir = File(downloadsDir, "InkTwin").apply { mkdirs() }
            val file = File(inktwinDir, filename)
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            exportSuccess = true
        }
        document.close()
        if (!exportSuccess) throw RuntimeException("PDF export failed")
    }

    fun clearExportStatus() {
        _uiState.update { it.copy(exportStatus = null) }
    }
}
