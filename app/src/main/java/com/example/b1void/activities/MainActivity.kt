package com.example.b1void.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.dropbox.core.android.Auth
import com.example.b1void.R
import com.example.b1void.databinding.ActivityMainBinding
import com.example.b1void.viewmodels.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: AuthViewModel by viewModels()
    
    companion object {
        private const val AUTH_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkAccessToken()
        observeViewModel()
    }

    private fun setupUI() {
        binding.logBtn.setOnClickListener {
            navigateToFileManager()
        }
    }

    private fun checkAccessToken() {
        viewModel.checkAccessToken()
    }

    private fun observeViewModel() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is AuthViewModel.AuthState.NoToken -> {
                    Timber.d("No access token found, starting OAuth")
                    startOAuth()
                }
                is AuthViewModel.AuthState.HasToken -> {
                    Timber.d("Access token found, navigating to file manager")
                    navigateToFileManager()
                }
                is AuthViewModel.AuthState.Error -> {
                    Timber.e("Auth error: ${state.message}")
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startOAuth() {
        Auth.startOAuth2Authentication(this, getString(R.string.APP_KEY))
    }

    private fun navigateToFileManager() {
        startActivity(Intent(this, FileManagerActivity::class.java))
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == AUTH_REQUEST_CODE) {
            val accessToken = Auth.getOAuth2Token()
            if (accessToken != null) {
                viewModel.saveAccessToken(accessToken)
            } else {
                Toast.makeText(this, R.string.auth_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
} 