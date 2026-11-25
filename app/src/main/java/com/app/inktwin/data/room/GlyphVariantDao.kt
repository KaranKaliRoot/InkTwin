package com.app.inktwin.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GlyphVariantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(variant: GlyphVariantEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(variants: List<GlyphVariantEntity>): List<Long>

    @Query("SELECT * FROM glyph_variants WHERE glyph_id = :glyphId ORDER BY variant_index")
    fun getByGlyphId(glyphId: Long): Flow<List<GlyphVariantEntity>>

    @Query("SELECT * FROM glyph_variants WHERE glyph_id = :glyphId AND variant_index = :index")
    fun getByGlyphIdAndIndex(glyphId: Long, index: Int): Flow<GlyphVariantEntity?>

    @Query("SELECT * FROM glyph_variants WHERE id = :id")
    fun getById(id: Long): Flow<GlyphVariantEntity?>

    @Query("DELETE FROM glyph_variants WHERE glyph_id = :glyphId")
    suspend fun deleteByGlyphId(glyphId: Long): Int

    @Query("DELETE FROM glyph_variants WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT MAX(variant_index) FROM glyph_variants WHERE glyph_id = :glyphId")
    fun getMaxVariantIndex(glyphId: Long): Flow<Int?>
}