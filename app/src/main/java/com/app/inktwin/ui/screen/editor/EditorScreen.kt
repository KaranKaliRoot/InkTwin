package com.app.inktwin.ui.screen.editor

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun EditorScreen(
    onNavigateToPreview: () -> Unit
) {
    val viewModel: EditorViewModel = hiltViewModel()
    val coroutineScope = rememberCoroutineScope()
    val textFieldValue by viewModel.textFieldValue.collectAsState()
    val colorSpans by viewModel.colorSpans.collectAsState()
    val activeColorHex by viewModel.activeColorHex.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

    var showCustomColorDialog by remember { mutableStateOf(false) }

    val visualTransformation = remember(colorSpans) {
        VisualTransformation { annotatedString ->
            val builder = AnnotatedString.Builder(annotatedString.text)
            val len = annotatedString.text.length
            for (span in colorSpans) {
                val s = span.start.coerceIn(0, len)
                val e = span.end.coerceIn(0, len)
                if (s < e) {
                    try {
                        val c = ComposeColor(Color.parseColor(span.colorHex))
                        builder.addStyle(SpanStyle(color = c), s, e)
                    } catch (_: Exception) {}
                }
            }
            TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Edit Note", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.undo() }, enabled = canUndo) { Text("Undo") }
                Button(onClick = { viewModel.redo() }, enabled = canRedo) { Text("Redo") }
            }
            Button(onClick = { coroutineScope.launch { viewModel.saveCurrentDraft() }; onNavigateToPreview() }) { Text("Preview →") }
        }

        Text(
            text = "${viewModel.characterCount} chars | ${viewModel.wordCount} words",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        ColorPaletteBar(
            activeHex = activeColorHex,
            onSelect = { viewModel.setActiveColorHex(it) },
            onCustomClick = { showCustomColorDialog = true }
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { viewModel.applyColorToSelection() }) { Text("Sel") }
            Button(onClick = { viewModel.applyColorToCurrentWord() }) { Text("Word") }
            Button(onClick = { viewModel.applyColorToCurrentLine() }) { Text("Line") }
            Button(onClick = { viewModel.applyColorToCurrentParagraph() }) { Text("Para") }
            Button(onClick = { viewModel.clearSelectionColor() }) { Text("Clear") }
        }

        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { viewModel.onTextFieldValueChange(it) },
            modifier = Modifier.fillMaxWidth().weight(1f),
            visualTransformation = visualTransformation,
            placeholder = { Text("Start typing...") },
            keyboardActions = KeyboardActions.Default
        )
    }

    if (showCustomColorDialog) {
        CustomColorDialog(
            onDismiss = { showCustomColorDialog = false },
            onColorConfirmed = { hex ->
                viewModel.setActiveColorHex(hex)
                showCustomColorDialog = false
            }
        )
    }
}

@Composable
fun ColorPaletteBar(
    activeHex: String,
    onSelect: (String) -> Unit,
    onCustomClick: () -> Unit
) {
    val colors = listOf("#1E3A8A", "#0F172A", "#DC2626", "#15803D", "#6D28D9")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        colors.forEach { hex ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ComposeColor(Color.parseColor(hex)))
                    .border(
                        width = if (activeHex == hex) 3.dp else 1.dp,
                        color = if (activeHex == hex) ComposeColor.Black else ComposeColor.LightGray,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onSelect(hex) }
            )
        }

        OutlinedButton(
            onClick = onCustomClick,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text("+ Custom", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun CustomColorDialog(
    onDismiss: () -> Unit,
    onColorConfirmed: (String) -> Unit
) {
    var red by remember { mutableFloatStateOf(30f) }
    var green by remember { mutableFloatStateOf(58f) }
    var blue by remember { mutableFloatStateOf(138f) }

    val currentHex = String.format("#%02X%02X%02X", red.toInt(), green.toInt(), blue.toInt())
    val previewColor = ComposeColor(red.toInt(), green.toInt(), blue.toInt())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom RGB Ink Color") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(previewColor)
                        .border(1.dp, ComposeColor.Gray, RoundedCornerShape(8.dp))
                )

                Text(currentHex, style = MaterialTheme.typography.bodyMedium)

                Text("Red: ${red.toInt()}")
                Slider(
                    value = red,
                    onValueChange = { red = it },
                    valueRange = 0f..255f
                )

                Text("Green: ${green.toInt()}")
                Slider(
                    value = green,
                    onValueChange = { green = it },
                    valueRange = 0f..255f
                )

                Text("Blue: ${blue.toInt()}")
                Slider(
                    value = blue,
                    onValueChange = { blue = it },
                    valueRange = 0f..255f
                )
            }
        },
        confirmButton = {
            Button(onClick = { onColorConfirmed(currentHex) }) {
                Text("Apply Color")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}