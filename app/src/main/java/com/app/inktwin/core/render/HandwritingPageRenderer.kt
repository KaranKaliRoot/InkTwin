package com.app.inktwin.core.render

import android.graphics.*
import com.app.inktwin.core.glyph.Glyph
import com.app.inktwin.core.glyph.PathCommand
import com.app.inktwin.core.note.ColorSpan
import com.app.inktwin.core.note.NoteDraft
import com.app.inktwin.core.note.PageSettings

data class PageLayout(
    val pageIndex: Int,
    val lines: List<LineLayout>,
    val charStartIndex: Int,
    val charEndIndex: Int
)

data class LineLayout(
    val lineIndex: Int,
    val text: String,
    val charStartIndex: Int,
    val charEndIndex: Int,
    val baselineY: Float,
    val glyphLayouts: List<GlyphLayout>
)

data class GlyphLayout(
    val char: Char,
    val charIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val colorHex: String,
    val variantIndex: Int,
    val glyph: Glyph?,
    val isFallback: Boolean
)

object HandwritingPageRenderer {

    private const val DPI = 150
    private const val A4_WIDTH_PX = 1240
    private const val A4_HEIGHT_PX = 1754
    private const val FONT_SIZE = 36f
    private const val LINE_SPACING_DEFAULT = 1.2f

    private val fallbackTypeface = Typeface.create("serif", Typeface.ITALIC)
    private val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DC2626")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val ruledLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D1D5DB")
        strokeWidth = 1f
    }
    private val pageNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B7280")
        textSize = 24f
        textAlign = Paint.Align.RIGHT
    }

    fun getPageSize(pageSize: String): Pair<Int, Int> {
        return when (pageSize) {
            "A4" -> A4_WIDTH_PX to A4_HEIGHT_PX
            "Letter" -> 1275 to 1650
            else -> A4_WIDTH_PX to A4_HEIGHT_PX
        }
    }

    fun paginateText(
        draft: NoteDraft,
        pageSettings: PageSettings,
        fontSize: Float = FONT_SIZE,
        glyphsByChar: Map<String, Glyph> = emptyMap()
    ): List<PageLayout> {
        val pages = mutableListOf<PageLayout>()
        val text = draft.plainText
        val lines = text.split("\n")

        val (pageWidth, pageHeight) = getPageSize(pageSettings.pageSize)
        val usableHeight = pageHeight - pageSettings.marginTop - pageSettings.marginBottom
        val lineHeight = fontSize * pageSettings.lineSpacing
        val maxLinesPerPage = (usableHeight / lineHeight).toInt().coerceAtLeast(1)

        var charIndex = 0
        var pageIndex = 0
        var lineIndexInPage = 0
        var currentLines = mutableListOf<LineLayout>()

        for ((globalLineIndex, lineText) in lines.withIndex()) {
            val lineStartChar = charIndex
            val lineEndChar = charIndex + lineText.length

            val baselineY = pageSettings.marginTop + pageSettings.redMarginOffset + 24f + lineIndexInPage * lineHeight
            val glyphLayouts = layoutLineGlyphs(
                lineText = lineText,
                charStartIndex = lineStartChar,
                baselineY = baselineY,
                draft = draft,
                fontSize = fontSize,
                glyphsByChar = glyphsByChar,
                pageSettings = pageSettings
            )

            currentLines.add(
                LineLayout(
                    lineIndex = globalLineIndex,
                    text = lineText,
                    charStartIndex = lineStartChar,
                    charEndIndex = lineEndChar,
                    baselineY = baselineY,
                    glyphLayouts = glyphLayouts
                )
            )

            charIndex = lineEndChar + 1
            lineIndexInPage++

            if (lineIndexInPage >= maxLinesPerPage) {
                pages.add(
                    PageLayout(
                        pageIndex = pageIndex,
                        lines = currentLines.toList(),
                        charStartIndex = pages.lastOrNull()?.charEndIndex ?: 0,
                        charEndIndex = lineEndChar
                    )
                )
                pageIndex++
                lineIndexInPage = 0
                currentLines = mutableListOf()
            }
        }

        if (currentLines.isNotEmpty()) {
            pages.add(
                PageLayout(
                    pageIndex = pageIndex,
                    lines = currentLines.toList(),
                    charStartIndex = pages.lastOrNull()?.charEndIndex ?: 0,
                    charEndIndex = text.length
                )
            )
        }

        return pages.ifEmpty {
            listOf(
                PageLayout(
                    pageIndex = 0,
                    lines = emptyList(),
                    charStartIndex = 0,
                    charEndIndex = 0
                )
            )
        }
    }

    private fun layoutLineGlyphs(
        lineText: String,
        charStartIndex: Int,
        baselineY: Float,
        draft: NoteDraft,
        fontSize: Float,
        glyphsByChar: Map<String, Glyph> = emptyMap(),
        pageSettings: PageSettings
    ): List<GlyphLayout> {
        val (pageWidth, _) = getPageSize(pageSettings.pageSize)
        val maxX = pageWidth - pageSettings.marginRight
        val glyphs = mutableListOf<GlyphLayout>()
        var x = pageSettings.marginLeft + 24f
        val y = baselineY

        for ((i, c) in lineText.withIndex()) {
            val globalCharIndex = charStartIndex + i
            val colorSpan = draft.colorSpans.find {
                globalCharIndex in it.start until it.end
            }
            val colorHex = colorSpan?.colorHex ?: "#1E3A8A"

            val charKey = c.toString()
            val glyph = glyphsByChar[charKey]?.takeIf { it.variants.isNotEmpty() && !it.isFallback }

            val variantIndex = if (glyph != null && glyph.variants.isNotEmpty()) {
                (globalCharIndex * 31) % glyph.variants.size
            } else 0

            val width = if (glyph != null && glyph.variants.isNotEmpty()) {
                val variant = glyph.variants[variantIndex]
                if (variant.vectorCommands.isNotEmpty()) {
                    (variant.advanceWidth / 1000f * fontSize).coerceIn(fontSize * 0.35f, fontSize * 1.1f)
                } else if (variant.alphaMaskBitmap != null) {
                    fontSize * (variant.bitmapWidth.toFloat() / variant.bitmapHeight.coerceAtLeast(1))
                } else {
                    fontSize * 0.6f
                }
            } else {
                fontSize * 0.6f
            }
            val height = fontSize

            // Word wrap: if adding this glyph exceeds maxX, wrap to next line
            if (x + width > maxX && glyphs.isNotEmpty()) {
                // Start new line
                x = pageSettings.marginLeft + 24f
            }

            glyphs.add(
                GlyphLayout(
                    char = c,
                    charIndex = globalCharIndex,
                    x = x,
                    y = y,
                    width = width,
                    height = height,
                    colorHex = colorHex,
                    variantIndex = variantIndex,
                    glyph = glyph,
                    isFallback = glyph == null || glyph.variants.isEmpty()
                )
            )

            x += width
        }

        return glyphs
    }

    fun renderPageToBitmap(
        page: PageLayout,
        draft: NoteDraft,
        glyphsByChar: Map<String, Glyph>,
        pageSettings: PageSettings,
        fontSize: Float = FONT_SIZE,
        showRedMargins: Boolean = pageSettings.showRedMargins,
        showPageNumbers: Boolean = pageSettings.showPageNumbers,
        showRuledLines: Boolean = true
    ): Bitmap {
        val (width, height) = getPageSize(pageSettings.pageSize)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.WHITE)

        if (showRuledLines) {
            drawRuledLines(canvas, page, pageSettings, fontSize)
        }

        if (showRedMargins) {
            drawRedMargins(canvas, pageSettings)
        }

        for (line in page.lines) {
            for (glyphLayout in line.glyphLayouts) {
                drawGlyph(canvas, glyphLayout, glyphsByChar, fontSize)
            }
        }

        if (showPageNumbers) {
            drawPageNumber(canvas, page.pageIndex + 1, pageSettings)
        }

        return bitmap
    }

    private fun drawRuledLines(
        canvas: Canvas,
        page: PageLayout,
        pageSettings: PageSettings,
        fontSize: Float
    ) {
        val lineHeight = fontSize * pageSettings.lineSpacing
        for (line in page.lines) {
            val y = line.baselineY + fontSize * 0.2f
            canvas.drawLine(
                pageSettings.marginLeft,
                y,
                (getPageSize(pageSettings.pageSize).first - pageSettings.marginRight).toFloat(),
                y,
                ruledLinePaint
            )
        }
    }

    private fun drawRedMargins(canvas: Canvas, pageSettings: PageSettings) {
        val (width, height) = getPageSize(pageSettings.pageSize)

        val topMarginY = pageSettings.marginTop + pageSettings.redMarginOffset
        canvas.drawLine(
            pageSettings.marginLeft,
            topMarginY,
            width - pageSettings.marginRight,
            topMarginY,
            marginPaint
        )

        canvas.drawLine(
            pageSettings.marginLeft,
            pageSettings.marginTop,
            pageSettings.marginLeft,
            height - pageSettings.marginBottom,
            marginPaint
        )
    }

    private fun drawPageNumber(canvas: Canvas, pageNum: Int, pageSettings: PageSettings) {
        val (width, height) = getPageSize(pageSettings.pageSize)
        canvas.drawText(
            "Page $pageNum",
            width - pageSettings.marginRight,
            height - pageSettings.marginBottom + 20f,
            pageNumberPaint
        )
    }

    private fun drawGlyph(
        canvas: Canvas,
        glyphLayout: GlyphLayout,
        glyphsByChar: Map<String, Glyph>,
        fontSize: Float
    ) {
        val color = Color.parseColor(glyphLayout.colorHex)

        if (glyphLayout.glyph != null && glyphLayout.glyph.variants.isNotEmpty()) {
            val variant = glyphLayout.glyph.variants.getOrNull(glyphLayout.variantIndex)
                ?: glyphLayout.glyph.variants.firstOrNull()
            if (variant?.vectorCommands?.isNotEmpty() == true) {
                drawVectorGlyph(canvas, variant, glyphLayout, color, fontSize)
                return
            }
            if (variant?.alphaMaskBitmap != null) {
                drawBitmapGlyph(canvas, variant, glyphLayout, color, fontSize)
                return
            }
        }

        drawFallbackGlyph(canvas, glyphLayout.char, glyphLayout, color, fontSize)
    }

    private fun drawVectorGlyph(
        canvas: Canvas,
        variant: com.app.inktwin.core.glyph.GlyphVariant,
        glyphLayout: GlyphLayout,
        color: Int,
        fontSize: Float
    ) {
        val path = android.graphics.Path()
        val scale = fontSize / 1000f

        for (cmd in variant.vectorCommands) {
            when (cmd.type) {
                "moveTo" -> path.moveTo(cmd.points[0] * scale + glyphLayout.x, cmd.points[1] * scale + glyphLayout.y - fontSize)
                "lineTo" -> path.lineTo(cmd.points[0] * scale + glyphLayout.x, cmd.points[1] * scale + glyphLayout.y - fontSize)
                "quadTo" -> path.quadTo(
                    cmd.points[0] * scale + glyphLayout.x,
                    cmd.points[1] * scale + glyphLayout.y - fontSize,
                    cmd.points[2] * scale + glyphLayout.x,
                    cmd.points[3] * scale + glyphLayout.y - fontSize
                )
                "cubicTo" -> path.cubicTo(
                    cmd.points[0] * scale + glyphLayout.x,
                    cmd.points[1] * scale + glyphLayout.y - fontSize,
                    cmd.points[2] * scale + glyphLayout.x,
                    cmd.points[3] * scale + glyphLayout.y - fontSize,
                    cmd.points[4] * scale + glyphLayout.x,
                    cmd.points[5] * scale + glyphLayout.y - fontSize
                )
                "close" -> path.close()
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = fontSize * 0.08f
        }
        canvas.drawPath(path, paint)
    }

    private fun drawBitmapGlyph(
        canvas: Canvas,
        variant: com.app.inktwin.core.glyph.GlyphVariant,
        glyphLayout: GlyphLayout,
        color: Int,
        fontSize: Float
    ) {
        val alphaMask = variant.alphaMaskBitmap ?: return
        val drawHeight = fontSize * 1.15f
        val scale = drawHeight / alphaMask.height

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }

        val dstRect = RectF(
            glyphLayout.x,
            glyphLayout.y - fontSize,
            glyphLayout.x + alphaMask.width * scale,
            glyphLayout.y
        )

        canvas.drawBitmap(alphaMask, null, dstRect, paint)
    }

    private fun drawFallbackGlyph(
        canvas: Canvas,
        char: Char,
        glyphLayout: GlyphLayout,
        color: Int,
        fontSize: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = fontSize
            typeface = fallbackTypeface
        }

        canvas.drawText(char.toString(), glyphLayout.x, glyphLayout.y, paint)
    }
}