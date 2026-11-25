package com.app.inktwin.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "handwriting_profiles")
@Serializable
data class HandwritingProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String = "Default",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "source_type") val sourceType: Int = 0, // 0 = Draw, 1 = Scan, 2 = Merged
    @ColumnInfo(name = "is_active") val isActive: Boolean = true
)