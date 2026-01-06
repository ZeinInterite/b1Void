package com.example.b1void.core.model

import java.io.File

data class FolderNode(
    val file: File,
    val level: Int = 0,
    var isExpanded: Boolean = false,
    var isSelected: Boolean = false,
    val children: MutableList<FolderNode> = mutableListOf()
)
