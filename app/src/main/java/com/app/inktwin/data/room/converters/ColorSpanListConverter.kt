package com.app.inktwin.data.room.converters

import androidx.room.TypeConverter
import com.app.inktwin.core.note.ColorSpan
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ColorSpanListConverter {

    @TypeConverter
    fun fromJson(json: String?): List<ColorSpan> {
        return json?.let { Json.decodeFromString<List<ColorSpan>>(it) } ?: emptyList()
    }

    @TypeConverter
    fun toJson(spans: List<ColorSpan>?): String? {
        return spans?.let { Json.encodeToString(it) }
    }
}