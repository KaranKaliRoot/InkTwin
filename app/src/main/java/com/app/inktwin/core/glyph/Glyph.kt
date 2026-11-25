package com.app.inktwin.core.glyph

data class Glyph(
    val character: String,
    val unicode: Int,
    val variants: List<GlyphVariant> = emptyList(),
    val isFallback: Boolean = false
)