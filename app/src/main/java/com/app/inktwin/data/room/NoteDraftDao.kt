package com.app.inktwin.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDraftDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(draft: NoteDraftEntity): Long

    @Update
    suspend fun update(draft: NoteDraftEntity): Int

    @Query("SELECT * FROM note_drafts WHERE id = :id")
    fun getById(id: Long): Flow<NoteDraftEntity?>

    @Query("SELECT * FROM note_drafts WHERE profile_id = :profileId ORDER BY updated_at DESC")
    fun getByProfileId(profileId: Long): Flow<List<NoteDraftEntity>>

    @Query("SELECT * FROM note_drafts ORDER BY updated_at DESC")
    fun getAllDrafts(): Flow<List<NoteDraftEntity>>

    @Query("DELETE FROM note_drafts WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM note_drafts WHERE profile_id = :profileId")
    suspend fun deleteByProfileId(profileId: Long): Int
}