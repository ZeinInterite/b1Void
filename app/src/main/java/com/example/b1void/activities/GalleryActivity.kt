package com.example.b1void.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.b1void.core.ui.theme.B1VoidTheme
import com.example.b1void.feature.gallery.presentation.screen.GalleryScreen
import com.example.b1void.feature.gallery.presentation.viewmodel.GalleryViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            B1VoidTheme {
                val viewModel: GalleryViewModel = hiltViewModel()
                GalleryScreen(viewModel = viewModel)
            }
        }
    }
}