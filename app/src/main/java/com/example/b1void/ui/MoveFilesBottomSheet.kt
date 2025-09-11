package com.example.b1void.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.b1void.adapters.FolderTreeAdapter
import com.example.b1void.databinding.BottomSheetMoveFilesBinding
import com.example.b1void.models.MoveUiState
import com.example.b1void.viewmodels.MoveViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class MoveFilesBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetMoveFilesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MoveViewModel by viewModels()
    private lateinit var folderAdapter: FolderTreeAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetMoveFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sourceFolderPath = arguments?.getString(ARG_SOURCE_FOLDER_PATH) ?: ""
        val rootFolderPath = arguments?.getString(ARG_ROOT_FOLDER_PATH) ?: ""
        setupRecyclerView(sourceFolderPath, rootFolderPath)
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView(sourceFolderPath: String, rootFolderPath: String) {
        folderAdapter = FolderTreeAdapter(
            currentSourcePath = sourceFolderPath,
            rootFolderPath = rootFolderPath,
            onFolderClick = { folderNode -> viewModel.toggleFolderExpansion(folderNode) },
            onFolderSelect = { folderNode -> viewModel.selectFolder(folderNode) }
        )
        binding.recyclerViewFolders.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = folderAdapter
            itemAnimator = null
        }
    }

    private fun setupListeners() {
        binding.buttonCancel.setOnClickListener { dismiss() }
        binding.buttonMove.setOnClickListener {
            binding.buttonMove.isEnabled = false
            binding.buttonCancel.isEnabled = false
            binding.progressBar.isVisible = true
            binding.recyclerViewFolders.alpha = 0.5f // Полупрозрачность для индикации неактивности

            viewLifecycleOwner.lifecycleScope.launch {
                val success = viewModel.moveSelectedFiles()
                if (success) {
                    setFragmentResult(REQUEST_KEY, Bundle().apply { putBoolean(RESULT_MOVED, true) })
                    dismiss()
                } else {
                    Toast.makeText(context, "Ошибка при перемещении файлов", Toast.LENGTH_SHORT).show()
                    binding.buttonMove.isEnabled = true
                    binding.buttonCancel.isEnabled = true
                    binding.progressBar.isVisible = false
                    binding.recyclerViewFolders.alpha = 1.0f
                }
            }
        }
        binding.searchInputEditText.doOnTextChanged { text, _, _, _ ->
            viewModel.onSearchQueryChanged(text.toString())
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.progressBar.isVisible = state is MoveUiState.Loading
                binding.recyclerViewFolders.isVisible = state is MoveUiState.Success

                when (state) {
                    is MoveUiState.Success -> {
                        folderAdapter.submitList(state.folderTree)
                    }
                    is MoveUiState.Error -> {
                        Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                    }
                    is MoveUiState.Loading -> { /* Handled by visibility */ }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedFolderState.collect { state ->
                binding.buttonMove.isEnabled = state.isMoveButtonEnabled
                binding.breadcrumbs.text = state.breadcrumbs
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "MoveFilesBottomSheet"
        const val REQUEST_KEY = "move_files_request"
        const val RESULT_MOVED = "result_moved"
        private const val ARG_FILE_IDS = "arg_file_ids"
        private const val ARG_SOURCE_FOLDER_PATH = "arg_source_folder_path"
        private const val ARG_ROOT_FOLDER_PATH = "arg_root_folder_path"

        fun newInstance(filePaths: List<String>, sourceFolderPath: String, rootFolderPath: String): MoveFilesBottomSheet {
            return MoveFilesBottomSheet().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_FILE_IDS, ArrayList(filePaths))
                    putString(ARG_SOURCE_FOLDER_PATH, sourceFolderPath)
                    putString(ARG_ROOT_FOLDER_PATH, rootFolderPath)
                }
            }
        }
    }
}
