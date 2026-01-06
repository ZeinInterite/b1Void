package com.example.b1void.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.b1void.feature.camera.presentation.screen.CameraScreen
import com.example.b1void.feature.camera.presentation.viewmodel.CameraViewModel
import com.example.b1void.feature.gallery.presentation.screen.GalleryScreen
import com.example.b1void.feature.gallery.presentation.viewmodel.GalleryViewModel

sealed class Screen(val route: String) {
    object Camera : Screen("camera_screen")
    object Gallery : Screen("gallery_screen")
    // Дополнительные экраны
}

@Composable
fun AppNavigation(navController: NavHostController, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    NavHost(navController = navController, startDestination = Screen.Gallery.route, modifier = modifier) {
        composable(Screen.Camera.route) {
            val cameraViewModel: CameraViewModel = hiltViewModel()
            CameraScreen(viewModel = cameraViewModel)
        }
        composable(Screen.Gallery.route) {
            val galleryViewModel: GalleryViewModel = hiltViewModel()
            GalleryScreen(viewModel = galleryViewModel)
        }
    }
}
