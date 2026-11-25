package com.app.inktwin.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "glyphs",
    foreignKeys = [
        ForeignKey(
            entity = HandwritingProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profile_id", "character"], unique = true)]
)
@Serializable
data class GlyphEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "character") val character: String, // Single character: "a", "Z", "5", "."
    @ColumnInfo(name = "unicode") val unicode: Int,
    @ColumnInfo(name = "is_fallback") val isFallback: Boolean = false, // True if from bundled font
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)