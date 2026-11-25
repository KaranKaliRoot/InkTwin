package com.app.inktwin.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GlyphDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(glyph: GlyphEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(glyphs: List<GlyphEntity>): List<Long>

    @Query("SELECT * FROM glyphs WHERE profile_id = :profileId ORDER BY character")
    fun getByProfileId(profileId: Long): Flow<List<GlyphEntity>>

    @Query("SELECT * FROM glyphs WHERE profile_id = :profileId AND character = :character")
    fun getByProfileAndCharacter(profileId: Long, character: String): Flow<GlyphEntity?>

    @Query("SELECT * FROM glyphs WHERE id = :id")
    fun getById(id: Long): Flow<GlyphEntity?>

    @Query("DELETE FROM glyphs WHERE profile_id = :profileId")
    suspend fun deleteByProfileId(profileId: Long): Int

    @Query("DELETE FROM glyphs WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Transaction
    @Query("SELECT * FROM glyphs WHERE profile_id = :profileId ORDER BY character")
    fun getWithVariants(profileId: Long): Flow<List<GlyphWithVariants>>

    @Query("SELECT COUNT(*) FROM glyphs WHERE profile_id = :profileId AND is_fallback = 0")
    fun getCapturedCount(profileId: Long): Flow<Int>
}

data class GlyphWithVariants(
    @androidx.room.Embedded val glyph: GlyphEntity,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "glyph_id",
        entity = GlyphVariantEntity::class
    ) val variants: List<GlyphVariantEntity>
)