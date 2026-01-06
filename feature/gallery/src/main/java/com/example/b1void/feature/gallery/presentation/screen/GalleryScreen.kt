package com.example.b1void.feature.gallery.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.b1void.feature.gallery.presentation.viewmodel.GalleryViewModel
import androidx.compose.foundation.layout.PaddingValues // Added this import

@Composable
fun GalleryScreen(viewModel: GalleryViewModel) {
    val photos by viewModel.photos.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        if (photos.isEmpty()) {
            Text("No photos found")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(photos) { photo ->
                    Card {
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = photo.displayName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
