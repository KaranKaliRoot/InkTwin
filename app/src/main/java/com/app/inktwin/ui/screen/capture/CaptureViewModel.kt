package com.app.inktwin.ui.screen.capture

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.inktwin.core.glyph.PathCommand
import com.app.inktwin.core.glyph.Glyph
import com.app.inktwin.core.glyph.GlyphNormalizer
import com.app.inktwin.core.glyph.GlyphVariant
import com.app.inktwin.core.pipeline.PlainPaperScanner
import com.app.inktwin.core.pipeline.ScannedCharCandidate
import com.app.inktwin.core.profile.HandwritingProfile
import com.app.inktwin.core.profile.SourceType
import com.app.inktwin.data.repository.GlyphRepository
import com.app.inktwin.data.repository.HandwritingProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// Safe character sets as simple strings
const val LOWERCASE_CHARS = "abcdefghijklmnopqrstuvwxyz"
const val UPPERCASE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
const val OPTIONAL_CHARS = "0123456789%.,?!-:;'()#"

data class CharacterCategory(
    val name: String,
    val chars: String,
    val displayName: String
) {
    companion object {
        val LOWERCASE = CharacterCategory("lowercase", LOWERCASE_CHARS, "a-z")
        val UPPERCASE = CharacterCategory("uppercase", UPPERCASE_CHARS, "A-Z")
        val OPTIONAL = CharacterCategory("optional", OPTIONAL_CHARS, "0-9 & .")
        val ALL = listOf(LOWERCASE, UPPERCASE, OPTIONAL)
    }
}

data class CaptureUiState(
    val activeProfile: HandwritingProfile? = null,
    val currentCharIndex: Int = 0,
    val selectedCategory: CharacterCategory = CharacterCategory.LOWERCASE,
    val capturedGlyphs: List<Glyph> = emptyList(),
    val isSaving: Boolean = false,
    val modeSwitch: String = "draw", // "draw" or "scan"
    // Mode B - Scan state
    val scannedPages: List<Bitmap> = emptyList(),
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val thresholdSensitivity: Float = 0.15f,
    val scannedCandidates: List<ScannedCharCandidate> = emptyList()
) {
    val currentChar: Char
        get() {
            val chars = selectedCategory.chars
            return if (chars.isNotEmpty()) {
                chars[currentCharIndex.coerceIn(0, chars.length - 1)]
            } else {
                'a'
            }
        }

    val progressCount: Int
        get() = getProgressForCategory(selectedCategory)

    private fun getProgressForCategory(category: CharacterCategory): Int {
        val chars = category.chars
        return chars.count { c ->
            capturedGlyphs.any { it.character == c.toString() && it.variants.isNotEmpty() }
        }
    }

    val totalLetters: Int
        get() = when (selectedCategory) {
            CharacterCategory.LOWERCASE -> 26
            CharacterCategory.UPPERCASE -> 26
            CharacterCategory.OPTIONAL -> OPTIONAL_CHARS.length
            else -> 52
        }

    val selectedCandidatesCount: Int
        get() = scannedCandidates.count { it.isSelected }
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val profileRepository: HandwritingProfileRepository,
    private val glyphRepository: GlyphRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _currentCategory = MutableStateFlow(CharacterCategory.LOWERCASE)
    val currentCategory: StateFlow<CharacterCategory> = _currentCategory.asStateFlow()

    // Exposed stroke data for DrawingPad (Mode A)
    private val _currentStrokes = MutableStateFlow<List<List<PathCommand>>>(emptyList())
    val currentStrokes: StateFlow<List<List<PathCommand>>> = _currentStrokes.asStateFlow()

    init {
        ensureActiveProfile()
        observeProfileAndGlyphs()
    }

    private fun ensureActiveProfile() {
        viewModelScope.launch {
            val profile = profileRepository.observeActiveProfile().firstOrNull()
            if (profile == null) {
                createDefaultProfile()
            } else {
                _uiState.update { it.copy(activeProfile = profile) }
            }
        }
    }

    private suspend fun createDefaultProfile() {
        try {
            val id = profileRepository.createProfile("Default Profile", SourceType.DRAW)
            profileRepository.setActiveProfile(id)
            val newProfile = profileRepository.observeActiveProfile().firstOrNull()
            _uiState.update { it.copy(activeProfile = newProfile) }
        } catch (e: Exception) {
            // Handle error silently for now
        }
    }

    private fun observeProfileAndGlyphs() {
        viewModelScope.launch {
            profileRepository.observeActiveProfile()
                .flatMapLatest { profile ->
                    _uiState.update { it.copy(activeProfile = profile) }
                    profile?.let {
                        glyphRepository.observeGlyphs(it.id)
                    } ?: kotlinx.coroutines.flow.flowOf(emptyList())
                }
                .collect { glyphs ->
                    _uiState.update { state -> state.copy(capturedGlyphs = glyphs) }
                }
        }
    }

    fun selectCategory(category: CharacterCategory) {
        _currentCategory.value = category
        _uiState.update { it.copy(selectedCategory = category, currentCharIndex = 0) }
    }

    fun setModeSwitch(mode: String) {
        _uiState.update { it.copy(modeSwitch = mode) }
        // Clear scan candidates when switching modes
        if (mode == "draw") {
            _uiState.update { it.copy(scannedCandidates = emptyList()) }
        }
    }

    fun setCurrentCharIndex(index: Int) {
        val maxIndex = _uiState.value.selectedCategory.chars.length - 1
        _uiState.update { it.copy(currentCharIndex = index.coerceIn(0, maxIndex)) }
    }

    fun navigatePrevious() {
        _uiState.update {
            it.copy(currentCharIndex = (it.currentCharIndex - 1).coerceAtLeast(0))
        }
    }

    fun navigateNext() {
        val state = _uiState.value
        val maxIndex = state.selectedCategory.chars.length - 1
        val nextIndex = (state.currentCharIndex + 1).coerceAtMost(maxIndex)

        _uiState.update {
            it.copy(currentCharIndex = nextIndex)
        }

        // Auto-switch to next category when finishing the last character
        if (nextIndex == maxIndex && state.currentCharIndex == maxIndex) {
            when (state.selectedCategory) {
                CharacterCategory.LOWERCASE -> selectCategory(CharacterCategory.UPPERCASE)
                CharacterCategory.UPPERCASE -> selectCategory(CharacterCategory.OPTIONAL)
                else -> {} // OPTIONAL stays
            }
        }
    }

    fun addStroke(stroke: List<PathCommand>) {
        _currentStrokes.value = _currentStrokes.value + listOf(stroke)
    }

    fun undoLastStroke() {
        val strokes = _currentStrokes.value
        if (strokes.isNotEmpty()) {
            _currentStrokes.value = strokes.dropLast(1)
        }
    }

    fun clearStrokes() {
        _currentStrokes.value = emptyList()
    }

    fun saveCurrentStroke() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val state = _uiState.value
            val profileId = state.activeProfile?.id
            val currentChar = state.currentChar

            if (profileId != null && _currentStrokes.value.isNotEmpty()) {
                // Convert strokes to PathCommands and normalize
                val pathCommands = convertStrokesToPathCommands(_currentStrokes.value)
                val normalizedResult = GlyphNormalizer.normalize(pathCommands)

                // Get existing glyphs for this profile
                val existingGlyphs = glyphRepository.observeGlyphs(profileId).firstOrNull() ?: emptyList()
                val existingGlyph = existingGlyphs.find { it.character == currentChar.toString() }

                if (existingGlyph != null) {
                    // Add variant to existing glyph
                    val newVariant = GlyphVariant(
                        variantIndex = existingGlyph.variants.size,
                        vectorCommands = normalizedResult.normalizedCommands,
                        advanceWidth = normalizedResult.advanceWidth,
                        leftBearing = normalizedResult.leftBearing,
                        topBearing = 0f,
                        ascent = normalizedResult.baselineY,
                        descent = 1000f - normalizedResult.baselineY
                    )
                    val updatedGlyph = existingGlyph.copy(
                        variants = existingGlyph.variants + newVariant
                    )
                    // Delete old and save updated
                    glyphRepository.deleteGlyphs(profileId)
                    val updatedList = existingGlyphs.filter { it.character != currentChar.toString() } + updatedGlyph
                    glyphRepository.saveGlyphs(profileId, updatedList)
                } else {
                    // Create new glyph
                    val newGlyph = Glyph(
                        character = currentChar.toString(),
                        unicode = currentChar.code,
                        variants = listOf(
                            GlyphVariant(
                                variantIndex = 0,
                                vectorCommands = normalizedResult.normalizedCommands,
                                advanceWidth = normalizedResult.advanceWidth,
                                leftBearing = normalizedResult.leftBearing,
                                topBearing = 0f,
                                ascent = normalizedResult.baselineY,
                                descent = 1000f - normalizedResult.baselineY
                            )
                        )
                    )
                    val updatedList = existingGlyphs + newGlyph
                    glyphRepository.saveGlyphs(profileId, updatedList)
                }

                // Clear strokes and move to next character
                clearStrokes()
                navigateNext()
            }

            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun convertStrokesToPathCommands(
        strokes: List<List<PathCommand>>
    ): List<PathCommand> {
        // Flatten the list of PathCommand lists into a single list
        return strokes.flatten()
    }

    // ============ MODE B: SCAN FUNCTIONS ============

    fun addScannedPages(bitmaps: List<Bitmap>) {
        _uiState.update { state ->
            state.copy(scannedPages = state.scannedPages + bitmaps)
        }
    }

    fun removeScannedPage(index: Int) {
        _uiState.update { state ->
            val newPages = state.scannedPages.toMutableList()
            if (index in newPages.indices) {
                newPages.removeAt(index)
            }
            state.copy(scannedPages = newPages)
        }
    }

    fun clearScannedPages() {
        _uiState.update { it.copy(scannedPages = emptyList(), scannedCandidates = emptyList()) }
    }

    fun setThresholdSensitivity(sensitivity: Float) {
        _uiState.update { it.copy(thresholdSensitivity = sensitivity.coerceIn(0.05f, 0.25f)) }
    }

    fun scanPages() {
        val pages = _uiState.value.scannedPages
        val sensitivity = _uiState.value.thresholdSensitivity
        if (pages.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isScanning = true, scanProgress = 0f, scannedCandidates = emptyList()) }

            val allCandidates = mutableListOf<ScannedCharCandidate>()

            for ((pageIndex, page) in pages.withIndex()) {
                // Binarize page
                val binarized = PlainPaperScanner.binarizePage(page, sensitivity)

                // Extract candidates
                val candidates = PlainPaperScanner.extractCandidates(page, binarized)

                // Assign proposed characters from recognized text
                // ML Kit returns text in reading order; we assign sequentially
                var charIndex = 0
                val recognizedText = extractRecognizedText(page)
                for (i in candidates.indices) {
                    if (charIndex < recognizedText.length) {
                        val c = recognizedText[charIndex]
                        if (c.isLetterOrDigit() || !c.isLetterOrDigit() && !c.isWhitespace() || c.isWhitespace()) {
                            // Only assign meaningful chars (letter, digit, or punctuation)
                            if (!c.isWhitespace()) {
                                allCandidates.add(candidates[i].copy(proposedChar = c.toString()))
                                charIndex++
                            } else {
                                allCandidates.add(candidates[i])
                            }
                        } else {
                            allCandidates.add(candidates[i])
                        }
                    } else {
                        allCandidates.add(candidates[i])
                    }
                }

                _uiState.update { it.copy(scanProgress = (pageIndex + 1).toFloat() / pages.size) }
            }

            _uiState.update { it.copy(isScanning = false, scannedCandidates = allCandidates) }
        }
    }

    private suspend fun extractRecognizedText(bitmap: Bitmap): String {
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
        )
        val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(inputImage).await()
        recognizer.close()
        return result.text
    }

    fun updateCandidateChar(candidateId: String, newChar: String) {
        _uiState.update { state ->
            val newCandidates = state.scannedCandidates.map { candidate ->
                if (candidate.id == candidateId) {
                    candidate.copy(proposedChar = newChar)
                } else {
                    candidate
                }
            }
            state.copy(scannedCandidates = newCandidates)
        }
    }

    fun toggleCandidateSelection(candidateId: String) {
        _uiState.update { state ->
            val newCandidates = state.scannedCandidates.map { candidate ->
                if (candidate.id == candidateId) {
                    candidate.copy(isSelected = !candidate.isSelected)
                } else {
                    candidate
                }
            }
            state.copy(scannedCandidates = newCandidates)
        }
    }

    fun commitScannedCandidates() {
        val state = _uiState.value
        val profileId = state.activeProfile?.id
        val selectedCandidates = state.scannedCandidates.filter { it.isSelected }
        if (profileId == null || selectedCandidates.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            // Get existing glyphs
            val existingGlyphs = glyphRepository.observeGlyphs(profileId).firstOrNull() ?: emptyList()
            val updatedGlyphs = existingGlyphs.toMutableList()

            for (candidate in selectedCandidates) {
                val char = candidate.proposedChar
                if (char.isEmpty()) continue

                val existingIndex = updatedGlyphs.indexOfFirst { it.character == char }
                val newVariant = candidate.toGlyphVariant(
                    variantIndex = if (existingIndex >= 0) updatedGlyphs[existingIndex].variants.size else 0
                )

                // Limit to 5 variants per character
                if (existingIndex >= 0) {
                    val existingGlyph = updatedGlyphs[existingIndex]
                    if (existingGlyph.variants.size < 5) {
                        val updated = existingGlyph.copy(variants = existingGlyph.variants + newVariant)
                        updatedGlyphs[existingIndex] = updated
                    }
                } else {
                    val newGlyph = Glyph(
                        character = char,
                        unicode = char.first().code,
                        variants = listOf(newVariant)
                    )
                    updatedGlyphs.add(newGlyph)
                }
            }

            glyphRepository.saveGlyphs(profileId, updatedGlyphs)

            // Clear scanned candidates after commit
            _uiState.update { it.copy(isSaving = false, scannedCandidates = emptyList()) }
        }
    }

    fun updateActiveProfile(profile: HandwritingProfile?) {
        _uiState.update { it.copy(activeProfile = profile) }
    }
}