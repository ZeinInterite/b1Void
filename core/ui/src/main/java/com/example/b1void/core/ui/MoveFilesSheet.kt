package com.example.b1void.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.b1void.core.model.FolderNode
import com.example.b1void.core.model.MoveUiState
import com.example.b1void.core.ui.viewmodels.MoveViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveFilesSheet(
    viewModel: MoveViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onMoveSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedFolderState by viewModel.selectedFolderState.collectAsState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Переместить в", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) {
                    Text("ОТМЕНА")
                }
            }

            // Search Bar (Optional, can be added later)
            // val (searchQuery, setSearchQuery) = remember { mutableStateOf("") }
            // OutlinedTextField(
            //     value = searchQuery,
            //     onValueChange = setSearchQuery,
            //     label = { Text("Поиск папки") },
            //     modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            // )

            // Folder List
            Box(modifier = Modifier.weight(1f)) {
                when (val state = uiState) {
                    is MoveUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is MoveUiState.Error -> {
                        Text(
                            text = state.message,
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is MoveUiState.Success -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.folderTree) { node ->
                                FolderItem(
                                    node = node,
                                    onToggleExpand = { viewModel.toggleFolderExpansion(node) },
                                    onSelect = { viewModel.selectFolder(node) }
                                )
                            }
                        }
                    }
                }
            }

            // Footer
            Surface(shadowElevation = 4.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = selectedFolderState.breadcrumbs,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val success = viewModel.moveSelectedFiles()
                                if (success) {
                                    onMoveSuccess()
                                }
                            }
                        },
                        enabled = selectedFolderState.isMoveButtonEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Переместить")
                    }
                }
            }
        }
    }
}

@Composable
fun FolderItem(
    node: FolderNode,
    onToggleExpand: () -> Unit,
    onSelect: () -> Unit
) {
    val indentation = (node.level * 24).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(start = indentation, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.children.isNotEmpty()) {
            Icon(
                imageVector = if (node.isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
                contentDescription = "Expand/Collapse",
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onToggleExpand)
            )
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = node.file.name,
            modifier = Modifier.weight(1f),
            color = if (node.isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified,
            style = if (node.isSelected) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
        )
    }
}
