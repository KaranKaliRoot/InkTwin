package com.app.inktwin.core.note

import kotlinx.serialization.Serializable

@Serializable
data class PageSettings(
    val showRedMargins: Boolean = false,
    val showPageNumbers: Boolean = false,
    val pageSize: String = "A4",
    val marginLeft: Float = 72f,
    val marginRight: Float = 72f,
    val marginTop: Float = 72f,
    val marginBottom: Float = 72f,
    val redMarginOffset: Float = 36f,
    val lineSpacing: Float = 1.2f,
    val letterSpacing: Float = 0f
)