package com.example.b1void.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {

    private val _authState = MutableLiveData<AuthState>(AuthState.Loading)
    val authState: LiveData<AuthState> = _authState

    sealed class AuthState {
        object Loading : AuthState()
        object NoToken : AuthState()
        object HasToken : AuthState()
        data class Error(val message: String) : AuthState()
    }

    fun checkAccessToken() {
        viewModelScope.launch {
            try {
                val hasToken = withContext(Dispatchers.IO) {
                    authManager.hasValidToken()
                }
                
                _authState.value = if (hasToken) {
                    AuthState.HasToken
                } else {
                    AuthState.NoToken
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking access token")
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun saveAccessToken(token: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    authManager.saveAccessToken(token)
                }
                _authState.value = AuthState.HasToken
            } catch (e: Exception) {
                Timber.e(e, "Error saving access token")
                _authState.value = AuthState.Error(e.message ?: "Failed to save token")
            }
        }
    }

    fun clearAccessToken() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    authManager.clearAccessToken()
                }
                _authState.value = AuthState.NoToken
            } catch (e: Exception) {
                Timber.e(e, "Error clearing access token")
                _authState.value = AuthState.Error(e.message ?: "Failed to clear token")
            }
        }
    }
}
