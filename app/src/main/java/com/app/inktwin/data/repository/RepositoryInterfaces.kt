package com.app.inktwin.data.repository

import com.app.inktwin.core.glyph.Glyph
import com.app.inktwin.core.profile.HandwritingProfile
import com.app.inktwin.core.profile.SourceType
import com.app.inktwin.core.note.NoteDraft
import kotlinx.coroutines.flow.Flow

interface HandwritingProfileRepository {
    fun observeActiveProfile(): Flow<HandwritingProfile?>
    fun observeAllProfiles(): Flow<List<HandwritingProfile>>
    suspend fun createProfile(name: String, sourceType: SourceType): Long
    suspend fun updateProfile(profile: HandwritingProfile)
    suspend fun deleteProfile(id: Long)
    suspend fun setActiveProfile(id: Long)
}

interface GlyphRepository {
    fun observeGlyphs(profileId: Long): Flow<List<Glyph>>
    suspend fun saveGlyphs(profileId: Long, glyphs: List<Glyph>)
    suspend fun deleteGlyphs(profileId: Long)
}

interface NoteDraftRepository {
    fun observeDrafts(profileId: Long?): Flow<List<NoteDraft>>
    suspend fun createDraft(draft: NoteDraft): Long
    suspend fun updateDraft(draft: NoteDraft)
    suspend fun deleteDraft(id: Long)
}