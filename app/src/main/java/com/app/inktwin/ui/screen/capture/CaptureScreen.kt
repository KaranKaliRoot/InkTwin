package com.app.inktwin.ui.screen.capture

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.inktwin.core.glyph.PathCommand
import com.app.inktwin.core.pipeline.ScannedCharCandidate
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onNavigateToEditor: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentCategory by viewModel.currentCategory.collectAsState()
    val currentStrokes by viewModel.currentStrokes.collectAsState()

    val currentChar = uiState.currentChar
    val progressCount = uiState.progressCount
    val totalLetters = uiState.totalLetters
    val modeSwitch = uiState.modeSwitch

    // Get characters for current category
    val currentChars = currentCategory.chars.toList()

    // Gallery launcher for multiple images
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            // Convert URIs to Bitmaps on background thread
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                val bitmaps = mutableListOf<Bitmap>()
                for (uri in uris) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        inputStream?.use { stream ->
                            val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                            bitmap?.let { bitmaps.add(it) }
                        }
                    } catch (e: Exception) {
                        // Ignore failed images
                    }
                }
                if (bitmaps.isNotEmpty()) {
                    viewModel.addScannedPages(bitmaps)
                }
            }
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { viewModel.addScannedPages(listOf(it)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Mode Switcher - 2 button toggle at top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.setModeSwitch("draw") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (modeSwitch == "draw") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("Draw", maxLines = 1)
            }

            Button(
                onClick = { viewModel.setModeSwitch("scan") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (modeSwitch == "scan") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text("Scan", maxLines = 1)
            }

            Button(
                onClick = onNavigateToEditor,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text("Edit Note →")
            }
        }

        if (modeSwitch == "draw") {
            // Draw Mode - full capture UI
            DrawModeContent(
                uiState = uiState,
                currentCategory = currentCategory,
                currentChars = currentChars,
                viewModel = viewModel,
                progressCount = progressCount,
                totalLetters = totalLetters,
                currentChar = currentChar
            )
        } else {
            // Scan Mode - full pipeline UI
            ScanModeContent(
                uiState = uiState,
                viewModel = viewModel,
                galleryLauncher = galleryLauncher,
                cameraLauncher = cameraLauncher
            )
        }
    }
}

@Composable
fun DrawModeContent(
    uiState: com.app.inktwin.ui.screen.capture.CaptureUiState,
    currentCategory: CharacterCategory,
    currentChars: List<Char>,
    viewModel: CaptureViewModel,
    progressCount: Int,
    totalLetters: Int,
    currentChar: Char
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress Header
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Captured: $progressCount / $totalLetters letters",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Active: '${currentChar}'",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Category Tabs
        CategoryTabs(
            selectedCategory = currentCategory,
            onCategorySelected = { viewModel.selectCategory(it) },
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Embedded DrawingPad
        DrawingPad(
            currentChar = currentChar,
            progressCount = progressCount,
            totalLetters = totalLetters,
            capturedGlyphs = uiState.capturedGlyphs,
            onStrokeComplete = { pathCommands: List<PathCommand> ->
                // Sync strokes to ViewModel so saveCurrentStroke() can access them
                viewModel.addStroke(pathCommands)
            },
            onUndo = { viewModel.undoLastStroke() },
            onClear = { viewModel.clearStrokes() },
            onPrevious = { viewModel.navigatePrevious() },
            onNext = { viewModel.navigateNext() },
            onSaveAndNext = { viewModel.saveCurrentStroke() },
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Captured Character Grid
        Text(
            text = "Character Library (${currentCategory.displayName})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        CharacterGrid(
            characters = currentChars,
            capturedGlyphs = uiState.capturedGlyphs,
            onCharSelected = { char ->
                val index = currentChars.indexOf(char)
                if (index >= 0) {
                    viewModel.setCurrentCharIndex(index)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
        )
    }
}

@Composable
fun ScanModeContent(
    uiState: com.app.inktwin.ui.screen.capture.CaptureUiState,
    viewModel: CaptureViewModel,
    galleryLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, List<Uri>>,
    cameraLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Void?, Bitmap?>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Page Picker Bar
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Gallery")
                            Spacer(Modifier.width(8.dp))
                            Text("+ Add Pages")
                        }
                    }

                    OutlinedButton(
                        onClick = { cameraLauncher.launch(null) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Camera")
                        }
                    }
                }

                if (uiState.scannedPages.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${uiState.scannedPages.size} page(s) queued",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Threshold Slider & Scan Button
        if (uiState.scannedPages.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Threshold slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Threshold / Ink Contrast")
                            Text(
                                "%.2f".format(uiState.thresholdSensitivity),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Slider(
                            value = uiState.thresholdSensitivity,
                            onValueChange = { viewModel.setThresholdSensitivity(it) },
                            valueRange = 0.05f..0.25f,
                            steps = 20,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Lower = more sensitive to faint ink",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Scan button
                    Button(
                        onClick = { if (!uiState.isScanning) viewModel.scanPages() },
                        enabled = !uiState.isScanning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isScanning) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Scanning... ${(uiState.scanProgress * 100).toInt()}%")
                            }
                        } else {
                            Text("Scan & Extract Letters")
                        }
                    }
                }
            }

            // Progress during scanning
            if (uiState.isScanning) {
                LinearProgressIndicator(
                    progress = uiState.scanProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }

        // Verification Grid
        if (uiState.scannedCandidates.isNotEmpty() && !uiState.isScanning) {
            Spacer(Modifier.height(16.dp))

            // Header with count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${uiState.selectedCandidatesCount} of ${uiState.scannedCandidates.size} selected",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Scrollable grid of candidates
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.scannedCandidates) { candidate ->
                    CandidateCard(
                        candidate = candidate,
                        onCharChange = { newChar ->
                            viewModel.updateCandidateChar(candidate.id, newChar)
                        },
                        onSelectionToggle = {
                            viewModel.toggleCandidateSelection(candidate.id)
                        }
                    )
                }
            }

            // Bottom save bar
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.commitScannedCandidates() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save ${uiState.selectedCandidatesCount} Verified Letters to Profile")
            }
        } else if (!uiState.isScanning && uiState.scannedPages.isNotEmpty()) {
            // No candidates yet, prompt to scan
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Ready to Scan",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Adjust threshold if needed, then tap \"Scan & Extract Letters\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun CandidateCard(
    candidate: ScannedCharCandidate,
    onCharChange: (String) -> Unit,
    onSelectionToggle: () -> Unit
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(candidate.proposedChar)) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (candidate.isSelected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Checkerboard background for transparency preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(
                        Color.White,
                        RectangleShape
                    )
            ) {
                // Draw checkerboard pattern for transparency visualization
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val size = this.size
                    val tileSize = 8f
                    val cols = (size.width / tileSize).toInt() + 1
                    val rows = (size.height / tileSize).toInt() + 1
                    for (x in 0 until cols) {
                        for (y in 0 until rows) {
                            if ((x + y) % 2 == 0) {
                                drawRect(
                                    color = Color(0xFFE0E0E0),
                                    topLeft = Offset(x * tileSize, y * tileSize),
                                    size = Size(tileSize, tileSize)
                                )
                            }
                        }
                    }

                    // Candidate alpha mask
                    candidate.alphaMask.asImageBitmap()?.let { imageBitmap ->
                        drawImage(imageBitmap)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Character label + edit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = candidate.isSelected,
                    onCheckedChange = { _ -> onSelectionToggle() }
                )

                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    modifier = Modifier.width(50.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelLarge,
                    label = { Text("Char") },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }
    }
}