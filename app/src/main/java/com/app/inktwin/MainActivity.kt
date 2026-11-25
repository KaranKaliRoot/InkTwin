package com.app.inktwin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.app.inktwin.ui.navigation.CAPTURE_ROUTE
import com.app.inktwin.ui.navigation.EDITOR_ROUTE
import com.app.inktwin.ui.navigation.PREVIEW_ROUTE
import com.app.inktwin.ui.screen.capture.CaptureScreen
import com.app.inktwin.ui.screen.editor.EditorScreen
import com.app.inktwin.ui.screen.preview.PreviewScreen
import com.app.inktwin.ui.theme.InkTwinTheme
import com.app.inktwin.ui.theme.Shapes
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InkTwinTheme {
                var showAboutDialog by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()
                        NavHost(navController, startDestination = CAPTURE_ROUTE) {
                            composable(CAPTURE_ROUTE) {
                                CaptureScreen(onNavigateToEditor = {
                                    navController.navigate(EDITOR_ROUTE)
                                })
                            }
                            composable(EDITOR_ROUTE) {
                                EditorScreen(onNavigateToPreview = {
                                    navController.navigate(PREVIEW_ROUTE)
                                })
                            }
                            composable(PREVIEW_ROUTE) {
                                PreviewScreen()
                            }
                        }
                    }

                    // Subtle creator badge in bottom-right corner
                    Text(
                        text = "Made by Rudra",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clickable { showAboutDialog = true }
                    )
                }

                if (showAboutDialog) {
                    AlertDialog(
                        onDismissRequest = { showAboutDialog = false },
                        title = { Text("InkTwin") },
                        text = {
                            Text(
                                "InkTwin v1.0\n\nDesigned & Developed by\nRudra Bhardwaj",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showAboutDialog = false }) {
                                Text("OK")
                            }
                        }
                    )
                }
            }
        }
    }
}