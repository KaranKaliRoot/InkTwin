package com.app.inktwin.core.profile

enum class SourceType(val value: Int) {
    DRAW(0),
    SCAN(1),
    MERGED(2);

    companion object {
        fun fromValue(value: Int): SourceType = entries.first { it.value == value }
    }
}

data class HandwritingProfile(
    val id: Long = 0,
    val name: String = "Default",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sourceType: SourceType = SourceType.DRAW,
    val isActive: Boolean = true
)