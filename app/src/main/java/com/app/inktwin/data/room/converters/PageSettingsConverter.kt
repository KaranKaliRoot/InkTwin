package com.app.inktwin.data.room.converters

import androidx.room.TypeConverter
import com.app.inktwin.core.note.PageSettings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PageSettingsConverter {

    @TypeConverter
    fun fromJson(json: String?): PageSettings {
        return json?.let { Json.decodeFromString<PageSettings>(it) } ?: PageSettings()
    }

    @TypeConverter
    fun toJson(settings: PageSettings?): String? {
        return settings?.let { Json.encodeToString(it) }
    }
}