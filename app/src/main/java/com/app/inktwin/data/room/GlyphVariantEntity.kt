package com.app.inktwin.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.app.inktwin.data.room.converters.PathListConverter
import com.app.inktwin.data.room.converters.BitmapConverter
import kotlinx.serialization.Serializable

@Entity(
    tableName = "glyph_variants",
    foreignKeys = [
        ForeignKey(
            entity = GlyphEntity::class,
            parentColumns = ["id"],
            childColumns = ["glyph_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("glyph_id")]
)
@Serializable
data class GlyphVariantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "glyph_id") val glyphId: Long,
    @ColumnInfo(name = "variant_index") val variantIndex: Int, // 0 = primary, 1+ = alternates
    // Vector path data (Mode A - Draw) - stored as JSON-serialized PathCommand list
    @ColumnInfo(name = "vector_commands_json") val vectorCommandsJson: String? = null,
    // Alpha-mask bitmap (Mode B - Scan) - stored as compressed PNG bytes
    @ColumnInfo(name = "alpha_mask_blob") val alphaMaskBlob: ByteArray? = null,
    @ColumnInfo(name = "bitmap_width") val bitmapWidth: Int = 0,
    @ColumnInfo(name = "bitmap_height") val bitmapHeight: Int = 0,
    // Metrics (normalized to em-square 1000x1000 for vector, or pixels for bitmap)
    @ColumnInfo(name = "advance_width") val advanceWidth: Float = 0f,
    @ColumnInfo(name = "left_bearing") val leftBearing: Float = 0f,
    @ColumnInfo(name = "top_bearing") val topBearing: Float = 0f,
    @ColumnInfo(name = "ascent") val ascent: Float = 0f,
    @ColumnInfo(name = "descent") val descent: Float = 0f,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
) {
    fun hasVectorData(): Boolean = vectorCommandsJson != null && vectorCommandsJson.isNotBlank()
    fun hasBitmapData(): Boolean = alphaMaskBlob != null && alphaMaskBlob.isNotEmpty()
}