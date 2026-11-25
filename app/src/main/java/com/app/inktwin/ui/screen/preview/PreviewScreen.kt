package com.app.inktwin.ui.screen.preview

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.app.inktwin.core.note.PageSettings

@Composable
fun PreviewScreen() {
    val viewModel: PreviewViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf(ExportFormat.PNG) }
    var exportScope by remember { mutableStateOf(ExportScope.CURRENT_PAGE) }

    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Preview & Export",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Page Settings", style = MaterialTheme.typography.titleMedium)

                var showRedMargins by remember { mutableStateOf(uiState.pageSettings.showRedMargins) }
                var showPageNumbers by remember { mutableStateOf(uiState.pageSettings.showPageNumbers) }
                var showRuledLines by remember { mutableStateOf(uiState.showRuledLines) }
                var fontSize by remember { mutableFloatStateOf(uiState.fontSize) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Red Margins")
                    Switch(
                        checked = showRedMargins,
                        onCheckedChange = {
                            showRedMargins = it
                            viewModel.setPageSettings(
                                uiState.pageSettings.copy(showRedMargins = it)
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Page Numbers")
                    Switch(
                        checked = showPageNumbers,
                        onCheckedChange = {
                            showPageNumbers = it
                            viewModel.setPageSettings(
                                uiState.pageSettings.copy(showPageNumbers = it)
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ruled Lines")
                    Switch(
                        checked = showRuledLines,
                        onCheckedChange = { viewModel.setRuledLines(it) }
                    )
                }

                Text("Text Size: ${fontSize.toInt()}")
                Slider(
                    value = fontSize,
                    onValueChange = {
                        fontSize = it
                        viewModel.setFontSize(it)
                    },
                    valueRange = 24f..48f
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (uiState.pageBitmaps.isNotEmpty()) {
            MultiPagePreviewViewer(
                currentPageIndex = uiState.currentPageIndex,
                totalPages = uiState.pages.size,
                currentBitmap = uiState.pageBitmaps.getOrNull(uiState.currentPageIndex),
                onPrevious = { viewModel.navigateToPage(uiState.currentPageIndex - 1) },
                onNext = { viewModel.navigateToPage(uiState.currentPageIndex + 1) }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No content to preview")
            }
        }

        uiState.exportStatus?.let { status ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { viewModel.clearExportStatus() }) {
                        Text("Dismiss")
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { showExportDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save / Export")
        }
    }

    val context = LocalContext.current
    if (showExportDialog) {
        ExportDialog(
            format = exportFormat,
            scope = exportScope,
            totalPages = uiState.pages.size,
            onFormatChange = { exportFormat = it },
            onScopeChange = { exportScope = it },
            onDismiss = { showExportDialog = false },
            onConfirm = {
                viewModel.exportDocument(context, exportFormat, exportScope)
                showExportDialog = false
            }
        )
    }
}

@Composable
fun MultiPagePreviewViewer(
    currentPageIndex: Int,
    totalPages: Int,
    currentBitmap: Bitmap?,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = currentPageIndex > 0
            ) {
                Text("← Prev")
            }

            Text(
                text = "Page ${currentPageIndex + 1} of $totalPages",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedButton(
                onClick = onNext,
                enabled = currentPageIndex < totalPages - 1
            ) {
                Text("Next →")
            }
        }

        Spacer(Modifier.height(8.dp))

        currentBitmap?.let { bitmap ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page preview",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun ExportDialog(
    format: ExportFormat,
    scope: ExportScope,
    totalPages: Int,
    onFormatChange: (ExportFormat) -> Unit,
    onScopeChange: (ExportScope) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Document") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text("Format", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = format == ExportFormat.PNG,
                                onClick = { onFormatChange(ExportFormat.PNG) }
                            )
                            Text("PNG Image")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = format == ExportFormat.PDF,
                                onClick = { onFormatChange(ExportFormat.PDF) }
                            )
                            Text("PDF Document")
                        }
                    }
                }

                Column {
                    Text("Scope", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = scope == ExportScope.CURRENT_PAGE,
                                onClick = { onScopeChange(ExportScope.CURRENT_PAGE) }
                            )
                            Text("This Page Only")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = scope == ExportScope.ENTIRE_DOCUMENT,
                                onClick = { onScopeChange(ExportScope.ENTIRE_DOCUMENT) }
                            )
                            Text("All $totalPages Pages")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Export")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}