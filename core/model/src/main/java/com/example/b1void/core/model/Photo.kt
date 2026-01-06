package com.example.b1void.core.model

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
)
