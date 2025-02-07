package com.example.b1void.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.dropbox.core.android.Auth
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val _authenticationState = MutableLiveData<AuthenticationState>(AuthenticationState.IDLE)
    val authenticationState: LiveData<AuthenticationState> = _authenticationState
    private val prefs = application.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private val accessTokenKey = "access-token"

    enum class AuthenticationState {
        AUTHENTICATED, UNAUTHENTICATED, LOADING, IDLE
    }


    fun checkAccessToken() {
        viewModelScope.launch {
            _authenticationState.value = AuthenticationState.LOADING
            val accessToken = prefs.getString(accessTokenKey, null)
            if (accessToken == null) {
                _authenticationState.value = AuthenticationState.UNAUTHENTICATED
            } else {
                _authenticationState.value = AuthenticationState.AUTHENTICATED
            }
        }

    }


    fun handleAuthResult(requestCode: Int) {
        viewModelScope.launch {
            if (requestCode == 1001) {
                val accessToken = Auth.getOAuth2Token()
                if (accessToken != null) {
                    prefs.edit().putString(accessTokenKey, accessToken).apply()
                    _authenticationState.value = AuthenticationState.AUTHENTICATED
                } else {
                    _authenticationState.value = AuthenticationState.UNAUTHENTICATED
                }
            }
        }
    }

    fun setLoading() {
        _authenticationState.value = AuthenticationState.LOADING
    }
    fun setAuthenticated() {
        _authenticationState.value = AuthenticationState.AUTHENTICATED
    }
}
