package com.app.inktwin.core.glyph

import kotlinx.serialization.Serializable

@Serializable
data class PathCommand(
    val type: String, // "moveTo", "lineTo", "quadTo", "cubicTo", "close"
    val points: List<Float> // x,y pairs: moveTo=2, lineTo=2, quadTo=4, cubicTo=6
)