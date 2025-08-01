package com.example.b1void.auth

import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    private val sharedPreferences: SharedPreferences
) {
    
    companion object {
        private const val ACCESS_TOKEN_KEY = "access-token"
    }
    
    fun hasValidToken(): Boolean {
        val token = sharedPreferences.getString(ACCESS_TOKEN_KEY, null)
        val hasToken = token != null
        Timber.d("Token check: ${if (hasToken) "exists" else "not found"}")
        return hasToken
    }
    
    fun getAccessToken(): String? {
        return sharedPreferences.getString(ACCESS_TOKEN_KEY, null)
    }
    
    fun saveAccessToken(token: String) {
        sharedPreferences.edit()
            .putString(ACCESS_TOKEN_KEY, token)
            .apply()
        Timber.d("Access token saved")
    }
    
    fun clearAccessToken() {
        sharedPreferences.edit()
            .remove(ACCESS_TOKEN_KEY)
            .apply()
        Timber.d("Access token cleared")
    }
}
