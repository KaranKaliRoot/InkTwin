package com.app.inktwin.core.note

import kotlinx.serialization.Serializable

@Serializable
data class ColorSpan(
    val start: Int,
    val end: Int,
    val colorHex: String,
    val granularity: String
)

data class NoteDraft(
    val id: Long = 0,
    val profileId: Long? = null,
    val title: String = "Untitled Note",
    val plainText: String = "",
    val colorSpans: List<ColorSpan> = emptyList(),
    val pageSettings: PageSettings = PageSettings(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)