package com.app.inktwin.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.app.inktwin.data.room.converters.BitmapConverter
import com.app.inktwin.data.room.converters.ColorSpanListConverter
import com.app.inktwin.data.room.converters.PageSettingsConverter
import com.app.inktwin.data.room.converters.PathListConverter

@Database(
    entities = [
        HandwritingProfileEntity::class,
        GlyphEntity::class,
        GlyphVariantEntity::class,
        NoteDraftEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(
    PathListConverter::class,
    BitmapConverter::class,
    ColorSpanListConverter::class,
    PageSettingsConverter::class
)
abstract class InkTwinDatabase : RoomDatabase() {

    abstract fun handwritingProfileDao(): HandwritingProfileDao
    abstract fun glyphDao(): GlyphDao
    abstract fun glyphVariantDao(): GlyphVariantDao
    abstract fun noteDraftDao(): NoteDraftDao

    companion object {
        @Volatile private var INSTANCE: InkTwinDatabase? = null

        fun getInstance(context: Context): InkTwinDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InkTwinDatabase::class.java,
                    "inktwin_database"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}