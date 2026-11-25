package com.app.inktwin.data.repository

import com.app.inktwin.core.glyph.Glyph
import com.app.inktwin.core.glyph.GlyphVariant
import com.app.inktwin.core.glyph.PathCommand
import com.app.inktwin.core.note.NoteDraft
import com.app.inktwin.core.profile.HandwritingProfile
import com.app.inktwin.core.profile.SourceType
import com.app.inktwin.data.room.GlyphEntity
import com.app.inktwin.data.room.GlyphVariantEntity
import com.app.inktwin.data.room.HandwritingProfileEntity
import com.app.inktwin.data.room.InkTwinDatabase
import com.app.inktwin.data.room.NoteDraftEntity
import com.app.inktwin.data.room.converters.PathListConverter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction

private val pathListConverter = PathListConverter()

private fun bitmapToBlob(bitmap: android.graphics.Bitmap?): ByteArray? {
    return bitmap?.let {
        val stream = java.io.ByteArrayOutputStream()
        it.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray()
    }
}

private fun blobToBitmap(blob: ByteArray?): android.graphics.Bitmap? {
    return blob?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
}

class HandwritingProfileRepositoryImpl(private val database: InkTwinDatabase) : HandwritingProfileRepository {

    override fun observeActiveProfile(): Flow<HandwritingProfile?> {
        return database.handwritingProfileDao().getActiveProfile().map { entity ->
            entity?.toDomainModel()
        }
    }

    override fun observeAllProfiles(): Flow<List<HandwritingProfile>> {
        return database.handwritingProfileDao().getAllProfiles().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun createProfile(name: String, sourceType: SourceType): Long {
        val entity = HandwritingProfileEntity(
            name = name,
            sourceType = sourceType.value,
            isActive = true
        )
        return database.handwritingProfileDao().insert(entity)
    }

    override suspend fun updateProfile(profile: HandwritingProfile) {
        val entity = profile.toEntity()
        database.handwritingProfileDao().update(entity)
    }

    override suspend fun deleteProfile(id: Long) {
        database.handwritingProfileDao().deleteById(id)
    }

    override suspend fun setActiveProfile(id: Long) {
        database.handwritingProfileDao().deactivateAll()
        database.handwritingProfileDao().setActive(id, System.currentTimeMillis())
    }

    private fun HandwritingProfileEntity.toDomainModel(): HandwritingProfile {
        return HandwritingProfile(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            sourceType = SourceType.fromValue(sourceType),
            isActive = isActive
        )
    }

    private fun HandwritingProfile.toEntity(): HandwritingProfileEntity {
        return HandwritingProfileEntity(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis(),
            sourceType = sourceType.value,
            isActive = isActive
        )
    }
}

class GlyphRepositoryImpl(private val database: InkTwinDatabase) : GlyphRepository {

    override fun observeGlyphs(profileId: Long): Flow<List<Glyph>> {
        return database.glyphDao().getWithVariants(profileId).map { glyphsWithVariants ->
            glyphsWithVariants.map { gv ->
                val variants = gv.variants.map { ve ->
                    val commands = if (ve.vectorCommandsJson != null && ve.vectorCommandsJson.isNotBlank()) {
                        pathListConverter.fromJson(ve.vectorCommandsJson)
                    } else emptyList<PathCommand>()

                    GlyphVariant(
                        variantIndex = ve.variantIndex,
                        vectorCommands = commands,
                        alphaMaskBitmap = blobToBitmap(ve.alphaMaskBlob),
                        bitmapWidth = ve.bitmapWidth,
                        bitmapHeight = ve.bitmapHeight,
                        advanceWidth = ve.advanceWidth,
                        leftBearing = ve.leftBearing,
                        topBearing = ve.topBearing,
                        ascent = ve.ascent,
                        descent = ve.descent
                    )
                }

                Glyph(
                    character = gv.glyph.character,
                    unicode = gv.glyph.unicode,
                    variants = variants,
                    isFallback = gv.glyph.isFallback
                )
            }
        }
    }

    override suspend fun saveGlyphs(profileId: Long, glyphs: List<Glyph>) {
        // Use transaction to avoid emitting intermediate empty state (0 -> N flicker)
        database.withTransaction {
            // Delete existing glyphs for this profile
            database.glyphDao().deleteByProfileId(profileId)

            // Insert new glyphs and variants
            for (glyph in glyphs) {
                val glyphEntity = GlyphEntity(
                    profileId = profileId,
                    character = glyph.character,
                    unicode = glyph.unicode,
                    isFallback = glyph.isFallback
                )
                val glyphId = database.glyphDao().insert(glyphEntity)

                val variantEntities = glyph.variants.mapIndexed { index, variant ->
                    GlyphVariantEntity(
                        glyphId = glyphId,
                        variantIndex = index,
                        vectorCommandsJson = if (variant.vectorCommands.isNotEmpty()) {
                            pathListConverter.toJson(variant.vectorCommands)
                        } else null,
                        alphaMaskBlob = bitmapToBlob(variant.alphaMaskBitmap),
                        bitmapWidth = variant.bitmapWidth,
                        bitmapHeight = variant.bitmapHeight,
                        advanceWidth = variant.advanceWidth,
                        leftBearing = variant.leftBearing,
                        topBearing = variant.topBearing,
                        ascent = variant.ascent,
                        descent = variant.descent
                    )
                }
                database.glyphVariantDao().insertAll(variantEntities)
            }
        }
    }

    override suspend fun deleteGlyphs(profileId: Long) {
        database.glyphDao().deleteByProfileId(profileId)
    }
}

class NoteDraftRepositoryImpl(private val database: InkTwinDatabase) : NoteDraftRepository {

    override fun observeDrafts(profileId: Long?): Flow<List<NoteDraft>> {
        val dao = database.noteDraftDao()
        return if (profileId != null) {
            dao.getByProfileId(profileId).map { entities ->
                entities.map { it.toDomainModel() }
            } as Flow<List<NoteDraft>>
        } else {
            dao.getAllDrafts().map { entities ->
                entities.map { it.toDomainModel() }
            } as Flow<List<NoteDraft>>
        }
    }

    override suspend fun createDraft(draft: NoteDraft): Long {
        val entity = draft.toEntity()
        return database.noteDraftDao().insert(entity)
    }

    override suspend fun updateDraft(draft: NoteDraft) {
        val entity = draft.toEntity().copy(updatedAt = System.currentTimeMillis())
        database.noteDraftDao().update(entity)
    }

    override suspend fun deleteDraft(id: Long) {
        database.noteDraftDao().deleteById(id)
    }

    private fun NoteDraftEntity.toDomainModel(): NoteDraft {
        return NoteDraft(
            id = id,
            profileId = profileId,
            title = title,
            plainText = plainText,
            colorSpans = colorSpans,
            pageSettings = pageSettings,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun NoteDraft.toEntity(): NoteDraftEntity {
        return NoteDraftEntity(
            id = id,
            profileId = profileId,
            title = title,
            plainText = plainText,
            colorSpans = colorSpans,
            pageSettings = pageSettings,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}