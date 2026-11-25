package com.app.inktwin.data.room.converters

import androidx.room.TypeConverter
import com.app.inktwin.core.glyph.PathCommand
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PathListConverter {

    @TypeConverter
    fun fromJson(json: String?): List<PathCommand> {
        return json?.let { Json.decodeFromString<List<PathCommand>>(it) } ?: emptyList()
    }

    @TypeConverter
    fun toJson(commands: List<PathCommand>?): String? {
        return commands?.let { Json.encodeToString<List<PathCommand>>(it) }
    }
}