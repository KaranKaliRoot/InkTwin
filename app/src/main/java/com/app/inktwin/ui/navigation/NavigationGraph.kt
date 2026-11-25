package com.app.inktwin.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.app.inktwin.ui.screen.capture.CaptureScreen
import com.app.inktwin.ui.screen.editor.EditorScreen
import com.app.inktwin.ui.screen.preview.PreviewScreen

const val CAPTURE_ROUTE = "capture"
const val EDITOR_ROUTE = "editor"
const val PREVIEW_ROUTE = "preview"

fun NavGraphBuilder.inkTwinNavGraph(navController: NavController) {
    navigation(
        startDestination = CAPTURE_ROUTE,
        route = "inktwin_nav_graph"
    ) {
        composable(CAPTURE_ROUTE) {
            CaptureScreen(
                onNavigateToEditor = {
                    navController.navigate(EDITOR_ROUTE)
                }
            )
        }
        composable(EDITOR_ROUTE) {
            EditorScreen(
                onNavigateToPreview = {
                    navController.navigate(PREVIEW_ROUTE)
                }
            )
        }
        composable(PREVIEW_ROUTE) {
            PreviewScreen()
        }
    }
}