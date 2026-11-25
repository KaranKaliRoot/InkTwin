package com.app.inktwin.ui.screen.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.text.input.TextFieldValue
import com.app.inktwin.core.note.ColorSpan
import com.app.inktwin.core.note.NoteDraft
import com.app.inktwin.core.profile.HandwritingProfile
import com.app.inktwin.data.repository.NoteDraftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val noteDraftRepository: NoteDraftRepository,
    profileRepository: com.app.inktwin.data.repository.HandwritingProfileRepository
) : ViewModel() {

    private val _textFieldValue = MutableStateFlow(TextFieldValue(""))
    val textFieldValue: StateFlow<TextFieldValue> = _textFieldValue.asStateFlow()

    private val _colorSpans = MutableStateFlow<List<ColorSpan>>(emptyList())
    val colorSpans: StateFlow<List<ColorSpan>> = _colorSpans.asStateFlow()

    private val _activeColorHex = MutableStateFlow("#1E3A8A")
    val activeColorHex: StateFlow<String> = _activeColorHex.asStateFlow()

    private val _undoStack = MutableStateFlow<List<Pair<String, List<ColorSpan>>>>(emptyList())
    private val _redoStack = MutableStateFlow<List<Pair<String, List<ColorSpan>>>>(emptyList())

    private val profileIdFlow = profileRepository.observeActiveProfile()
        .map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val canUndo: StateFlow<Boolean> = _undoStack.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val canRedo: StateFlow<Boolean> = _redoStack.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val characterCount: Int
        get() = _textFieldValue.value.text.count { !it.isWhitespace() }

    val wordCount: Int
        get() = _textFieldValue.value.text.trim().takeIf { it.isNotEmpty() }
            ?.split(Regex("\\s+"))?.size ?: 0

    init {
        loadDraft()
        setupAutoSave()
    }

    private fun loadDraft() {
        viewModelScope.launch {
            val profileId = profileIdFlow.value
            val drafts = noteDraftRepository.observeDrafts(profileId).firstOrNull()
            val draft = drafts?.firstOrNull()

            if (draft != null && draft.plainText.isNotEmpty()) {
                _textFieldValue.value = TextFieldValue(draft.plainText)
                _colorSpans.value = draft.colorSpans
                _activeColorHex.value = draft.colorSpans.firstOrNull()?.colorHex ?: "#1E3A8A"
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun setupAutoSave() {
        viewModelScope.launch {
            _textFieldValue
                .debounce(800)
                .distinctUntilChanged()
                .collect { saveCurrentDraft() }
        }
    }

    fun setActiveColorHex(hex: String) {
        _activeColorHex.value = hex
    }

    private fun pushUndo() {
        val snapshot = _textFieldValue.value.text to _colorSpans.value
        _undoStack.update { it + snapshot }
        _redoStack.value = emptyList()
    }

    fun undo() {
        val undoList = _undoStack.value
        if (undoList.isEmpty()) return

        val current = _textFieldValue.value.text to _colorSpans.value
        val last = undoList.last()

        _textFieldValue.value = TextFieldValue(last.first)
        _colorSpans.value = last.second

        _undoStack.value = undoList.dropLast(1)
        _redoStack.update { it + current }
    }

    fun redo() {
        val redoList = _redoStack.value
        if (redoList.isEmpty()) return

        val current = _textFieldValue.value.text to _colorSpans.value
        val next = redoList.last()

        _textFieldValue.value = TextFieldValue(next.first)
        _colorSpans.value = next.second

        _redoStack.value = redoList.dropLast(1)
        _undoStack.update { it + current }
    }

    fun onTextFieldValueChange(newValue: TextFieldValue) {
        _textFieldValue.value = newValue
    }

    fun applyColorToSelection() {
        pushUndo()
        val sel = _textFieldValue.value.selection
        if (sel.length > 0) {
            val text = _textFieldValue.value.text
            val newSpan = ColorSpan(
                start = sel.min.coerceIn(0, text.length),
                end = sel.max.coerceIn(0, text.length),
                colorHex = _activeColorHex.value,
                granularity = "selection"
            )
            _colorSpans.value = applySpanToList(newSpan, _colorSpans.value)
        } else {
            applyColorToCurrentWord()
        }
    }

    fun applyColorToCurrentWord() {
        pushUndo()
        val text = _textFieldValue.value.text
        val cursor = _textFieldValue.value.selection.start

        var start = cursor
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        var end = cursor
        while (end < text.length && !text[end].isWhitespace()) end++

        if (start < end) {
            val newSpan = ColorSpan(start, end, _activeColorHex.value, "word")
            _colorSpans.value = applySpanToList(newSpan, _colorSpans.value)
        }
    }

    fun applyColorToCurrentLine() {
        pushUndo()
        val text = _textFieldValue.value.text
        val cursor = _textFieldValue.value.selection.start

        var start = cursor
        while (start > 0 && text[start - 1] != '\n') start--
        var end = cursor
        while (end < text.length && text[end] != '\n') end++

        if (start < end) {
            val newSpan = ColorSpan(start, end, _activeColorHex.value, "line")
            _colorSpans.value = applySpanToList(newSpan, _colorSpans.value)
        }
    }

    fun applyColorToCurrentParagraph() {
        pushUndo()
        val text = _textFieldValue.value.text
        val cursor = _textFieldValue.value.selection.start

        var start = cursor
        while (start > 0) {
            if (start >= 2 && text[start-1] == '\n' && text[start-2] == '\n') break
            start--
        }
        var end = cursor
        while (end < text.length) {
            if (end <= text.length - 2 && text[end] == '\n' && text[end+1] == '\n') break
            end++
        }

        if (start < end) {
            val newSpan = ColorSpan(start, end, _activeColorHex.value, "paragraph")
            _colorSpans.value = applySpanToList(newSpan, _colorSpans.value)
        }
    }

    fun clearSelectionColor() {
        pushUndo()
        val sel = _textFieldValue.value.selection
        if (sel.length > 0) {
            _colorSpans.value = _colorSpans.value.filterNot { it.start < sel.max && it.end > sel.min }
        }
    }

    private fun applySpanToList(newSpan: ColorSpan, existing: List<ColorSpan>): List<ColorSpan> {
        val nonOverlapping = existing.filter { !(it.start < newSpan.end && it.end > newSpan.start) }
        return (nonOverlapping + newSpan).sortedBy { it.start }
    }

    suspend fun saveCurrentDraft() {
        val text = _textFieldValue.value.text
        val draft = NoteDraft(
            title = text.lines().firstOrNull()?.take(30) ?: "Untitled",
            plainText = text,
            colorSpans = _colorSpans.value,
            profileId = profileIdFlow.value
        )
        noteDraftRepository.createDraft(draft)
    }
}