package com.app.inktwin.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HandwritingProfileDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(profile: HandwritingProfileEntity): Long

    @Update
    suspend fun update(profile: HandwritingProfileEntity): Int

    @Query("DELETE FROM handwriting_profiles WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM handwriting_profiles WHERE id = :id")
    fun getById(id: Long): Flow<HandwritingProfileEntity?>

    @Query("SELECT * FROM handwriting_profiles WHERE is_active = 1 ORDER BY updated_at DESC LIMIT 1")
    fun getActiveProfile(): Flow<HandwritingProfileEntity?>

    @Query("SELECT * FROM handwriting_profiles ORDER BY updated_at DESC")
    fun getAllProfiles(): Flow<List<HandwritingProfileEntity>>

    @Query("UPDATE handwriting_profiles SET is_active = 0 WHERE is_active = 1")
    suspend fun deactivateAll(): Int

    @Query("UPDATE handwriting_profiles SET is_active = 1, updated_at = :now WHERE id = :id")
    suspend fun setActive(id: Long, now: Long): Int
}