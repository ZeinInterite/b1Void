package com.example.b1void.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.b1void.R
import com.example.b1void.adapters.FileAdapter
import com.example.b1void.auth.AuthManager
import com.example.b1void.databinding.ActivityFileManagerBinding
import com.example.b1void.services.DropboxService
import com.example.b1void.viewmodels.FileManagerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class FileManagerActivityOptimized : AppCompatActivity() {

    private lateinit var binding: ActivityFileManagerBinding
    private val viewModel: FileManagerViewModel by viewModels()
    
    @Inject
    lateinit var dropboxService: DropboxService
    
    @Inject
    lateinit var authManager: AuthManager
    
    private lateinit var fileAdapter: FileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        initializeDropbox()
        observeViewModel()
    }

    private fun setupUI() {
        // Настройка RecyclerView
        val gridLayoutManager = GridLayoutManager(this, 4)
        binding.recyclerView.layoutManager = gridLayoutManager
        
        fileAdapter = FileAdapter(this) { file ->
            if (file.isDirectory) {
                viewModel.openDirectory(file)
            } else {
                Toast.makeText(this, getString(R.string.file_selected, file.name), Toast.LENGTH_SHORT).show()
            }
        }
        binding.recyclerView.adapter = fileAdapter

        // Настройка кнопок
        binding.createFolderButton.setOnClickListener {
            showCreateFolderDialog()
        }
        
        binding.addInspectionButton.setOnClickListener {
            val intent = Intent(this, InspectionAddActivity::class.java)
            intent.putExtra("current_directory", viewModel.getCurrentDirectory().absolutePath)
            startActivity(intent)
        }
        
        binding.showInspectionButton.setOnClickListener {
            startActivity(Intent(this, WorkerActivity::class.java))
        }

        // Настройка SwipeRefreshLayout
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshFiles()
        }
    }

    private fun initializeDropbox() {
        lifecycleScope.launch {
            try {
                val accessToken = authManager.getAccessToken()
                if (accessToken != null) {
                    dropboxService.initialize(accessToken)
                    viewModel.loadDirectoryContent(dropboxService.getAppDirectory())
                } else {
                    redirectToLogin()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error initializing Dropbox")
                Toast.makeText(this@FileManagerActivityOptimized, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                redirectToLogin()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.filesState.observe(this) { state ->
            when (state) {
                is FileManagerViewModel.FilesState.Loading -> {
                    binding.swipeRefreshLayout.isRefreshing = true
                }
                is FileManagerViewModel.FilesState.Success -> {
                    binding.swipeRefreshLayout.isRefreshing = false
                    fileAdapter.submitList(state.files)
                    title = viewModel.getCurrentDirectoryName()
                }
                is FileManagerViewModel.FilesState.Error -> {
                    binding.swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
                else -> {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun showCreateFolderDialog() {
        val builder = AlertDialog.Builder(this)
        val input = android.widget.EditText(this)
        builder.setTitle(R.string.create_folder)
        builder.setView(input)

        builder.setPositiveButton(R.string.create) { dialog, _ ->
            val folderName = input.text.toString()
            if (folderName.isNotBlank()) {
                viewModel.createFolder(folderName, dropboxService.getAppDirectory())
            }
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.cancel) { dialog, _ -> 
            dialog.cancel() 
        }
        builder.show()
    }

    override fun onBackPressed() {
        if (!viewModel.onBackPressed()) {
            super.onBackPressed()
        }
    }

    private fun redirectToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
} 