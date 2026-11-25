package com.app.inktwin.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.app.inktwin.data.room.converters.ColorSpanListConverter
import com.app.inktwin.data.room.converters.PageSettingsConverter
import kotlinx.serialization.Serializable

@Entity(
    tableName = "note_drafts",
    foreignKeys = [
        ForeignKey(
            entity = HandwritingProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("profile_id")]
)
@Serializable
@TypeConverters(ColorSpanListConverter::class, PageSettingsConverter::class)
data class NoteDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long? = null,
    @ColumnInfo(name = "title") val title: String = "Untitled Note",
    @ColumnInfo(name = "plain_text") val plainText: String = "",
    @ColumnInfo(name = "color_spans_json") val colorSpans: List<com.app.inktwin.core.note.ColorSpan> = emptyList(),
    @ColumnInfo(name = "page_settings_json") val pageSettings: com.app.inktwin.core.note.PageSettings = com.app.inktwin.core.note.PageSettings(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)