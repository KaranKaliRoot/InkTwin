package com.app.inktwin.core.glyph

import android.graphics.Bitmap

data class GlyphVariant(
    val variantIndex: Int,
    val vectorCommands: List<PathCommand> = emptyList(),
    val alphaMaskBitmap: Bitmap? = null,
    val bitmapWidth: Int = 0,
    val bitmapHeight: Int = 0,
    val advanceWidth: Float = 0f,
    val leftBearing: Float = 0f,
    val topBearing: Float = 0f,
    val ascent: Float = 0f,
    val descent: Float = 0f
)