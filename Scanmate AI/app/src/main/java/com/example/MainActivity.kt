package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.navigation.Routes
import com.example.ui.screens.AiScreen
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.DocumentDetailScreen
import com.example.ui.screens.FileManagerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.QrScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ZipScreen
import com.example.ui.theme.ScanMateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanMateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = Routes.HOME) {
                        composable(Routes.HOME) {
                            HomeScreen(
                                onNavigateToCamera = { navController.navigate(Routes.CAMERA_SCAN) },
                                onNavigateToDoc = { id -> navController.navigate(Routes.docDetail(id)) },
                                onNavigateToQr = { navController.navigate(Routes.QR_TOOLS) },
                                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                                onNavigateToAi = { navController.navigate(Routes.AI_ASSISTANT) },
                                onNavigateToZip = { navController.navigate(Routes.ZIP_TOOLS) },
                                onNavigateToFiles = { navController.navigate(Routes.FILE_MANAGER) }
                            )
                        }
                        composable(Routes.CAMERA_SCAN) {
                            CameraScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onScanFinished = { id ->
                                    navController.popBackStack()
                                    navController.navigate(Routes.docDetail(id))
                                }
                            )
                        }
                        composable(Routes.DOC_DETAIL) { backStackEntry ->
                            val docIdStr = backStackEntry.arguments?.getString("docId") ?: "0"
                            DocumentDetailScreen(
                                docId = docIdStr.toLongOrNull() ?: 0L,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.QR_TOOLS) {
                            QrScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(Routes.AI_ASSISTANT) {
                            AiScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(Routes.ZIP_TOOLS) {
                            ZipScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(Routes.FILE_MANAGER) {
                            FileManagerScreen(onNavigateBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
