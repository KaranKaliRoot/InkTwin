package com.app.inktwin.ui.screen.capture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.inktwin.core.glyph.PathCommand
import com.app.inktwin.core.glyph.Glyph

@Composable
fun DrawingPad(
    currentChar: Char?,
    progressCount: Int,
    totalLetters: Int,
    capturedGlyphs: List<Glyph>,
    onStrokeComplete: (List<PathCommand>) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSaveAndNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allStrokes = remember { mutableStateListOf<List<Offset>>() }
    val currentStroke = remember { mutableStateOf(listOf<Offset>()) }

    // Automatically clear the canvas whenever the active character changes
    LaunchedEffect(currentChar) {
        allStrokes.clear()
        currentStroke.value = emptyList()
    }

    val padSize = 300.dp
    val guideColor = Color.LightGray
    val baselineColor = Color(0xFFFF4444)

    fun strokesToPathCommands(strokes: List<List<Offset>>): List<PathCommand> {
        val commands = mutableListOf<PathCommand>()
        for (stroke in strokes) {
            if (stroke.isEmpty()) continue
            commands.add(PathCommand(type = "moveTo", points = listOf(stroke[0].x, stroke[0].y)))
            for (i in 1 until stroke.size) {
                commands.add(PathCommand(type = "lineTo", points = listOf(stroke[i].x, stroke[i].y)))
            }
        }
        return commands
    }

    fun addCurrentStrokeToPath() {
        val stroke = currentStroke.value
        if (stroke.isEmpty()) return

        val savedStroke = stroke.toList()
        allStrokes.add(savedStroke)

        // Send only the newly completed stroke so ViewModel does not duplicate earlier strokes
        val pathCommands = strokesToPathCommands(listOf(savedStroke))
        onStrokeComplete(pathCommands)

        currentStroke.value = emptyList()
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Draw: '$currentChar'",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        currentChar?.let { char ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Draw: '$char'",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Card(
            modifier = Modifier
                .size(padSize)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .pointerInput(currentChar) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStroke.value = listOf(offset)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val last = currentStroke.value.lastOrNull() ?: change.position
                                currentStroke.value = currentStroke.value + Offset(
                                    last.x + dragAmount.x,
                                    last.y + dragAmount.y
                                )
                            },
                            onDragEnd = {
                                addCurrentStrokeToPath()
                            },
                            onDragCancel = {
                                currentStroke.value = emptyList()
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val padding = 20.dp.toPx()

                    val ascenderY = height * 0.20f
                    val xHeightY = height * 0.45f
                    val baselineY = height * 0.75f
                    val descenderY = height * 0.92f

                    drawLine(
                        color = guideColor,
                        start = Offset(padding, ascenderY),
                        end = Offset(width - padding, ascenderY),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = guideColor,
                        start = Offset(padding, xHeightY),
                        end = Offset(width - padding, xHeightY),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = baselineColor,
                        start = Offset(padding, baselineY),
                        end = Offset(width - padding, baselineY),
                        strokeWidth = 4f
                    )
                    drawLine(
                        color = guideColor,
                        start = Offset(padding, descenderY),
                        end = Offset(width - padding, descenderY),
                        strokeWidth = 2f
                    )

                    allStrokes.forEach { stroke ->
                        if (stroke.size > 1) {
                            val path = Path().apply {
                                moveTo(stroke[0].x, stroke[0].y)
                                for (i in 1 until stroke.size) {
                                    lineTo(stroke[i].x, stroke[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = Color.Black,
                                style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }

                    val current = currentStroke.value
                    if (current.size > 1) {
                        val path = Path().apply {
                            moveTo(current[0].x, current[0].y)
                            for (i in 1 until current.size) {
                                lineTo(current[i].x, current[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = Color.Blue,
                            style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    allStrokes.clear()
                    currentStroke.value = emptyList()
                    onPrevious()
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("Prev", maxLines = 1)
            }

            OutlinedButton(
                onClick = {
                    if (allStrokes.isNotEmpty()) {
                        allStrokes.removeAt(allStrokes.lastIndex)
                    }
                    onUndo()
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("Undo", maxLines = 1)
            }

            OutlinedButton(
                onClick = {
                    allStrokes.clear()
                    currentStroke.value = emptyList()
                    onClear()
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("Clear", maxLines = 1)
            }

            OutlinedButton(
                onClick = {
                    allStrokes.clear()
                    currentStroke.value = emptyList()
                    onNext()
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("Next", maxLines = 1)
            }
        }

        Button(
            onClick = {
                if (currentStroke.value.isNotEmpty()) {
                    addCurrentStrokeToPath()
                }
                onSaveAndNext()
                allStrokes.clear()
                currentStroke.value = emptyList()
            },
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            Text("Save & Next", maxLines = 1)
        }
    }
}

@Composable
fun CharacterGrid(
    characters: List<Char>,
    capturedGlyphs: List<Glyph>,
    onCharSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(52.dp),
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(characters) { char ->
            val isCaptured = capturedGlyphs.any { it.character == char.toString() }
            CharacterCell(
                char = char,
                isCaptured = isCaptured,
                onClick = { onCharSelected(char) }
            )
        }
    }
}

@Composable
fun CharacterCell(
    char: Char,
    isCaptured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCaptured) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = char.toString(),
                fontSize = 20.sp,
                color = if (isCaptured) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
fun CategoryTabs(
    selectedCategory: CharacterCategory,
    onCategorySelected: (CharacterCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CharacterCategory.ALL.forEach { category ->
            Button(
                onClick = { onCategorySelected(category) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedCategory == category) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(category.displayName)
            }
        }
    }
}
