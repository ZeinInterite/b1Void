package com.example.b1void.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.b1void.core.ui.theme.B1VoidTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CameraComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            B1VoidTheme {
                // TODO: REWRITE REQUIRED. Legacy code commented out to fix build.
            }
        }
    }
}
